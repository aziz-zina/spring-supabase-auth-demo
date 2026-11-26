package tn.azizzina.supabasedemo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The token inspector: one page per token, plus Supabase's discovery document and public keys. */
@Controller
public class InspectorController {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    private final OAuth2AuthorizedClientService authorizedClients;
    private final JwtDecoder jwtDecoder;
    private final SupabaseProperties supabase;
    private final RestClient http = RestClient.create();

    public InspectorController(
            OAuth2AuthorizedClientService authorizedClients, JwtDecoder jwtDecoder, SupabaseProperties supabase) {
        this.authorizedClients = authorizedClients;
        this.jwtDecoder = jwtDecoder;
        this.supabase = supabase;
    }

    /** One row of the summary bar above a token. */
    public record Fact(String label, String value, boolean mono, boolean time) {

        static Fact text(String label, String value) {
            return new Fact(label, value, false, false);
        }

        static Fact code(String label, String value) {
            return new Fact(label, value, true, false);
        }

        static Fact time(String label, Instant value) {
            return new Fact(label, value == null ? "" : value.toString(), false, true);
        }
    }

    @ModelAttribute
    public void shell(Model model, Authentication authentication) {
        model.addAttribute("projectRef", supabase.projectRef());
        if (authentication instanceof OAuth2AuthenticationToken oauth && oauth.getPrincipal() instanceof OidcUser user) {
            model.addAttribute("email", user.getEmail() != null ? user.getEmail() : user.getSubject());
            model.addAttribute("idAlg", Json.headerField(user.getIdToken().getTokenValue(), "alg"));
            OAuth2AuthorizedClient client = client(oauth);
            if (client != null) {
                model.addAttribute("accessAlg", Json.headerField(client.getAccessToken().getTokenValue(), "alg"));
            }
        }
    }

    @GetMapping("/")
    public String home(Authentication authentication, HttpSession session, Model model) {
        if (authentication instanceof OAuth2AuthenticationToken) {
            return "redirect:/access-token";
        }
        // set by the failure handler after a failed sign-in (/?error)
        if (session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) instanceof AuthenticationException failure) {
            model.addAttribute("signInError", failure.getMessage());
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
        return "home";
    }

    @GetMapping("/access-token")
    public String accessToken(OAuth2AuthenticationToken oauth, Model model) {
        OAuth2AuthorizedClient client = client(oauth);
        if (client == null) {
            return "redirect:/oauth2/authorization/supabase";
        }
        String token = client.getAccessToken().getTokenValue();
        model.addAttribute("active", "access");
        model.addAttribute("title", "Access token");
        model.addAttribute("description",
                "Spring sends this token to APIs in the Authorization header. "
                        + "Signature checked against your project's public keys.");
        model.addAttribute("token", token);
        model.addAttribute("console", true);

        try {
            // validated exactly like /api/** does it
            Jwt jwt = jwtDecoder.decode(token);
            model.addAttribute("claims", ClaimView.of(jwt.getClaims()));
            model.addAttribute("issuedAt", jwt.getIssuedAt());
            model.addAttribute("expiresAt", jwt.getExpiresAt());
            // Supabase's token response has no "scope" field, so read what was granted from the token itself
            model.addAttribute("facts", List.of(
                    Fact.time("Issued", jwt.getIssuedAt()),
                    Fact.code("Client", shorten(jwt.getClaimAsString("client_id"))),
                    Fact.code("Scopes", String.valueOf(jwt.getClaimAsString("scope")))));
        } catch (JwtException e) {
            model.addAttribute("tokenError", e.getMessage());
        }
        return "token";
    }

    @GetMapping("/id-token")
    public String idToken(@AuthenticationPrincipal OidcUser user, Model model) {
        OidcIdToken idToken = user.getIdToken();
        model.addAttribute("active", "id");
        model.addAttribute("title", "ID token");
        model.addAttribute("description",
                "Tells this app who signed in. Spring validated it at sign-in. Never send it to an API.");
        model.addAttribute("token", idToken.getTokenValue());
        model.addAttribute("claims", ClaimView.of(idToken.getClaims()));
        model.addAttribute("issuedAt", idToken.getIssuedAt());
        model.addAttribute("expiresAt", idToken.getExpiresAt());

        var facts = new ArrayList<Fact>();
        facts.add(Fact.time("Signed in", idToken.getAuthenticatedAt()));
        facts.add(Fact.code("Audience", shorten(String.join(", ", idToken.getAudience()))));
        facts.add(Fact.text("Email verified", Boolean.TRUE.equals(idToken.getEmailVerified()) ? "Yes" : "No"));
        model.addAttribute("facts", facts);
        return "token";
    }

    @GetMapping("/refresh-token")
    public String refreshToken(OAuth2AuthenticationToken oauth, Model model) {
        model.addAttribute("active", "refresh");
        OAuth2AuthorizedClient client = client(oauth);
        OAuth2RefreshToken refreshToken = client == null ? null : client.getRefreshToken();
        if (refreshToken != null) {
            model.addAttribute("refreshToken", refreshToken.getTokenValue());
            model.addAttribute("refreshIssuedAt", refreshToken.getIssuedAt());
            model.addAttribute("accessExpiresAt", client.getAccessToken().getExpiresAt());
        }
        return "refresh-token";
    }

    @GetMapping("/discovery")
    public String discovery(Model model) {
        model.addAttribute("active", "discovery");
        String url = supabase.issuer() + "/.well-known/openid-configuration";
        model.addAttribute("url", url);
        try {
            Map<String, Object> document = http.get().uri(url).retrieve().body(JSON_OBJECT);
            model.addAttribute("rows", ClaimView.of(document == null ? Map.of() : document));
            model.addAttribute("raw", Json.pretty(document));
        } catch (RestClientException e) {
            model.addAttribute("fetchError", e.getMessage());
        }
        return "discovery";
    }

    @GetMapping("/jwks")
    public String jwks(OAuth2AuthenticationToken oauth, Model model) {
        model.addAttribute("active", "jwks");
        String url = supabase.issuer() + "/.well-known/jwks.json";
        model.addAttribute("url", url);
        OAuth2AuthorizedClient client = client(oauth);
        if (client != null) {
            model.addAttribute("currentKid", Json.headerField(client.getAccessToken().getTokenValue(), "kid"));
        }
        try {
            Map<String, Object> jwks = http.get().uri(url).retrieve().body(JSON_OBJECT);
            Object keys = jwks == null ? null : jwks.get("keys");
            model.addAttribute("keys", keys instanceof List<?> list ? list : List.of());
            model.addAttribute("raw", Json.pretty(jwks));
        } catch (RestClientException e) {
            model.addAttribute("fetchError", e.getMessage());
        }
        return "jwks";
    }

    private OAuth2AuthorizedClient client(OAuth2AuthenticationToken oauth) {
        return authorizedClients.loadAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), oauth.getName());
    }

    /** ab5830a5-78e7-482f-98b0-57c55e95e42a → ab5830a5…5e95e42a */
    private static String shorten(String id) {
        return id != null && id.length() > 20 ? id.substring(0, 8) + "…" + id.substring(id.length() - 8) : id;
    }
}
