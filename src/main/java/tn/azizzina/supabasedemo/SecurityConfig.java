package tn.azizzina.supabasedemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Spring checks ID tokens with RS256 by default. Supabase signs with the project's current JWT signing key,
     * an ECC P-256 key (ES256) on new projects. Change this if you rotate to an RSA key.
     */
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        var factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(clientRegistration -> SignatureAlgorithm.ES256);
        return factory;
    }

    /**
     * API: stateless, accepts "Authorization: Bearer <Supabase access token>".
     * Tokens are validated against the project's JWKS (signature, issuer, expiry).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * Web pages: "Sign in with Supabase" through the OAuth 2.1 authorization code flow.
     * The consent page must stay public: Supabase redirects the browser there before the user is signed in here.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http, ClientRegistrationRepository clientRegistrations) throws Exception {

        // OAuth 2.1 requires PKCE for every client, confidential clients included
        var authorizationRequestResolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization");
        authorizationRequestResolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());

        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/oauth/**", "/css/**", "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .loginPage("/")
                        // log the real reason (Render logs) and keep it in the session for the home page
                        .failureHandler((request, response, exception) -> {
                            log.warn("Sign-in with Supabase failed: {}", exception.getMessage(), exception);
                            new SimpleUrlAuthenticationFailureHandler("/?error")
                                    .onAuthenticationFailure(request, response, exception);
                        })
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(authorizationRequestResolver)))
                // after the Spring session ends, also end the Supabase browser session (see signed-out.html)
                .logout(logout -> logout.logoutSuccessUrl("/oauth/signed-out"))
                .build();
    }
}
