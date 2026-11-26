package tn.azizzina.supabasedemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(authorizationRequestResolver)))
                // after the Spring session ends, also end the Supabase browser session (see signed-out.html)
                .logout(logout -> logout.logoutSuccessUrl("/oauth/signed-out"))
                .build();
    }
}
