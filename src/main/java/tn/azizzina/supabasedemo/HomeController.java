package tn.azizzina.supabasedemo;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class HomeController {

    private final OAuth2AuthorizedClientService authorizedClients;
    private final JwtDecoder jwtDecoder;

    public HomeController(OAuth2AuthorizedClientService authorizedClients, JwtDecoder jwtDecoder) {
        this.authorizedClients = authorizedClients;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser user)) {
            return "index";
        }

        model.addAttribute("email", user.getEmail());
        // https://<your-app>.onrender.com/api/me on Render, http://localhost:8080/api/me locally
        model.addAttribute("apiUrl", ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/me").toUriString());
        model.addAttribute("idTokenClaims", new TreeMap<>(user.getIdToken().getClaims()));

        OAuth2AuthorizedClient client =
                authorizedClients.loadAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), oauth.getName());
        if (client != null) {
            String accessToken = client.getAccessToken().getTokenValue();
            model.addAttribute("accessToken", accessToken);
            model.addAttribute("grantedScopes", client.getAccessToken().getScopes());
            model.addAttribute("hasRefreshToken", client.getRefreshToken() != null);
            model.addAttribute("accessTokenClaims", decode(accessToken));
        }
        return "index";
    }

    /** Validates and decodes the access token exactly like /api/** does. */
    private Map<String, Object> decode(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return new TreeMap<>(jwt.getClaims());
        } catch (JwtException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
