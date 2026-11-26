package tn.azizzina.supabasedemo;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final OAuth2AuthorizedClientService authorizedClients;
    private final JwtDecoder jwtDecoder;

    public HomeController(OAuth2AuthorizedClientService authorizedClients, JwtDecoder jwtDecoder) {
        this.authorizedClients = authorizedClients;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/")
    public String home(Authentication authentication, HttpSession session, Model model) {
        // set by the failure handler after a failed sign-in (/?error)
        if (session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION) instanceof AuthenticationException failure) {
            model.addAttribute("signInError", failure.getMessage());
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser user)) {
            model.addAttribute("signedIn", false);
            return "index";
        }

        model.addAttribute("signedIn", true);
        model.addAttribute("email", user.getEmail() != null ? user.getEmail() : user.getSubject());
        model.addAttribute("idToken", user.getIdToken().getTokenValue());
        model.addAttribute("idClaims", ClaimView.of(user.getIdToken().getClaims()));

        OAuth2AuthorizedClient client =
                authorizedClients.loadAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), oauth.getName());
        if (client == null) {
            return "index";
        }
        String accessToken = client.getAccessToken().getTokenValue();
        model.addAttribute("accessToken", accessToken);
        model.addAttribute("hasRefreshToken", client.getRefreshToken() != null);

        // validated exactly like /api/** does it
        try {
            Jwt jwt = jwtDecoder.decode(accessToken);
            model.addAttribute("accessClaims", ClaimView.of(jwt.getClaims()));
            model.addAttribute("clientId", jwt.getClaimAsString("client_id"));
            model.addAttribute("issuedAt", jwt.getIssuedAt());
            model.addAttribute("expiresAt", jwt.getExpiresAt());
            // Supabase's token response has no "scope" field, so read what was granted from the token itself
            String scope = jwt.getClaimAsString("scope");
            model.addAttribute("scopes", scope == null ? List.of() : Arrays.asList(scope.trim().split("\\s+")));
        } catch (JwtException e) {
            model.addAttribute("accessTokenError", e.getMessage());
        }
        return "index";
    }
}
