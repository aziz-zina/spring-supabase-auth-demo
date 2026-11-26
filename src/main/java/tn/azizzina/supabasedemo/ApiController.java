package tn.azizzina.supabasedemo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A protected API that accepts Supabase access tokens, like an MCP server would. */
@RestController
public class ApiController {

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        var body = new LinkedHashMap<String, Object>();
        body.put("sub", jwt.getSubject());
        body.put("email", jwt.getClaimAsString("email"));
        body.put("role", jwt.getClaimAsString("role"));
        body.put("client_id", jwt.getClaimAsString("client_id"));
        body.put("aud", jwt.getAudience());
        body.put("iss", String.valueOf(jwt.getIssuer()));
        body.put("expires_at", jwt.getExpiresAt());
        return body;
    }
}
