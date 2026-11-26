package tn.azizzina.supabasedemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Renders every page with sample data: fails on template errors, and writes the HTML to target/preview
 * so the design can be checked in a browser without a Supabase project.
 */
class TemplateRenderingTest {

    private static final String TOKEN =
            "eyJhbGciOiJFUzI1NiIsImtpZCI6ImFlOGU3ZTc3IiwidHlwIjoiSldUIn0"
                    + ".eyJzdWIiOiJkNzk5YmM0YiIsImVtYWlsIjoiYXppei56aW5hMjAwMUBnbWFpbC5jb20ifQ"
                    + ".c2lnbmF0dXJlLXNpZ25hdHVyZS1zaWduYXR1cmU";

    private static SpringTemplateEngine engine;
    private static JakartaServletWebApplication application;

    @BeforeAll
    static void setUp() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        application = JakartaServletWebApplication.buildApplication(new MockServletContext());
    }

    @ParameterizedTest
    @ValueSource(strings = {"home", "home-error", "access-token", "id-token", "refresh-token", "discovery", "jwks",
            "consent", "signed-out"})
    void renders(String page) throws IOException {
        Map<String, Object> model = model(page);
        String template = switch (page) {
            case "home-error" -> "home";
            case "access-token", "id-token" -> "token";
            default -> page;
        };
        var exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        String html = engine.process(template, new WebContext(exchange, Locale.ENGLISH, model));

        assertThat(html).contains("</html>");
        Path out = Path.of("target/preview", page + ".html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, html);
    }

    private static Map<String, Object> model(String page) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var model = new HashMap<String, Object>();
        model.put("projectRef", "nukkyudgvbbsadyyxfbu");
        model.put("supabase", new SupabaseProperties("https://nukkyudgvbbsadyyxfbu.supabase.co", "sb_publishable_demo"));
        model.put("email", "aziz.zina2001@gmail.com");
        model.put("accessAlg", "ES256");
        model.put("idAlg", "ES256");

        var claims = new LinkedHashMap<String, Object>();
        claims.put("aal", "aal1");
        claims.put("amr", List.of(Map.of("method", "oauth_provider/authorization_code", "timestamp", 1795702626)));
        claims.put("app_metadata", Map.of("provider", "email", "providers", List.of("email")));
        claims.put("aud", List.of("authenticated"));
        claims.put("client_id", "ab5830a5-78e7-482f-98b0-57c55e95e42a");
        claims.put("email", "aziz.zina2001@gmail.com");
        claims.put("exp", now.plus(54, ChronoUnit.MINUTES));
        claims.put("iat", now.minus(6, ChronoUnit.MINUTES));
        claims.put("iss", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1");
        claims.put("role", "authenticated");
        claims.put("scope", "openid email profile");
        claims.put("session_id", "3e1ec785-5650-4708-b7df-99ca61a1f44e");
        claims.put("sub", "d799bc4b-8993-4d5a-8346-7a9d59707339");

        switch (page) {
            case "home-error" -> {
                model.remove("email");
                model.put("signInError", "[invalid_id_token] An error occurred while attempting to decode the Jwt: "
                        + "Signed JWT rejected: Another algorithm expected, or no matching key(s) found");
            }
            case "home", "consent", "signed-out" -> model.remove("email");
            case "access-token" -> {
                model.put("active", "access");
                model.put("title", "Access token");
                model.put("description", "Spring sends this token to APIs in the Authorization header. "
                        + "Signature checked against your project's public keys.");
                model.put("token", TOKEN);
                model.put("console", true);
                model.put("claims", ClaimView.of(claims));
                model.put("issuedAt", now.minus(6, ChronoUnit.MINUTES));
                model.put("expiresAt", now.plus(54, ChronoUnit.MINUTES));
                model.put("facts", List.of(
                        InspectorController.Fact.time("Issued", now.minus(6, ChronoUnit.MINUTES)),
                        InspectorController.Fact.code("Client", "ab5830a5…5e95e42a"),
                        InspectorController.Fact.code("Scopes", "openid email profile")));
            }
            case "id-token" -> {
                model.put("active", "id");
                model.put("title", "ID token");
                model.put("description", "Tells this app who signed in. Spring validated it at sign-in. Never send it to an API.");
                model.put("token", TOKEN);
                model.put("claims", ClaimView.of(claims));
                model.put("issuedAt", now.minus(6, ChronoUnit.MINUTES));
                model.put("expiresAt", now.plus(54, ChronoUnit.MINUTES));
                model.put("facts", List.of(
                        InspectorController.Fact.time("Signed in", now.minus(6, ChronoUnit.MINUTES)),
                        InspectorController.Fact.code("Audience", "ab5830a5…5e95e42a"),
                        InspectorController.Fact.text("Email verified", "Yes")));
            }
            case "refresh-token" -> {
                model.put("active", "refresh");
                model.put("refreshToken", "r7kqv2xh3mzw");
                model.put("refreshIssuedAt", now.minus(6, ChronoUnit.MINUTES));
                model.put("accessExpiresAt", now.plus(54, ChronoUnit.MINUTES));
            }
            case "discovery" -> {
                model.put("active", "discovery");
                model.put("url", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1/.well-known/openid-configuration");
                var document = new LinkedHashMap<String, Object>();
                document.put("issuer", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1");
                document.put("authorization_endpoint", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1/oauth/authorize");
                document.put("token_endpoint", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1/oauth/token");
                document.put("jwks_uri", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1/.well-known/jwks.json");
                document.put("scopes_supported", List.of("openid", "profile", "email", "phone", "offline_access"));
                document.put("code_challenge_methods_supported", List.of("S256", "plain"));
                document.put("id_token_signing_alg_values_supported", List.of("RS256", "HS256", "ES256"));
                model.put("rows", ClaimView.of(document));
                model.put("raw", Json.pretty(document));
            }
            case "jwks" -> {
                model.put("active", "jwks");
                model.put("url", "https://nukkyudgvbbsadyyxfbu.supabase.co/auth/v1/.well-known/jwks.json");
                model.put("currentKid", "ae8e7e77-fe74-498b-a877-a4fb3b959c99");
                var key = new LinkedHashMap<String, Object>();
                key.put("kid", "ae8e7e77-fe74-498b-a877-a4fb3b959c99");
                key.put("kty", "EC");
                key.put("crv", "P-256");
                key.put("alg", "ES256");
                key.put("use", "sig");
                key.put("x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU");
                key.put("y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0");
                model.put("keys", List.of(key));
                model.put("raw", Json.pretty(Map.of("keys", List.of(key))));
            }
            default -> { }
        }
        return model;
    }
}
