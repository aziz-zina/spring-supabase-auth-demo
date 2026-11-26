package tn.azizzina.supabasedemo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One JWT claim, formatted for the page: a readable value and what the claim means.
 *
 * @param name    claim name, e.g. "sub"
 * @param value   readable value (JSON for objects and arrays, ISO-8601 for times)
 * @param time    true when {@code value} is an ISO-8601 instant (the page shows it in local time)
 * @param meaning plain-language explanation, empty when unknown
 */
public record ClaimView(String name, String value, boolean time, String meaning) {

    private static final Set<String> TIME_CLAIMS = Set.of("exp", "iat", "nbf", "auth_time", "updated_at");

    private static final Map<String, String> MEANINGS = Map.ofEntries(
            Map.entry("iss", "Who issued the token: your Supabase project's Auth server."),
            Map.entry("sub", "The user's ID in Supabase (auth.users.id). Stable: use it as the user key in your database."),
            Map.entry("aud", "Who the token is meant for. Supabase sets \"authenticated\" by default; an API should check it."),
            Map.entry("exp", "When the token stops working."),
            Map.entry("iat", "When the token was issued."),
            Map.entry("nbf", "The token isn't valid before this time."),
            Map.entry("auth_time", "When the user actually signed in."),
            Map.entry("email", "The user's email address."),
            Map.entry("email_verified", "Whether the user confirmed this email address."),
            Map.entry("phone", "The user's phone number, if any."),
            Map.entry("phone_number", "The user's phone number, if any."),
            Map.entry("phone_verified", "Whether the user confirmed this phone number."),
            Map.entry("phone_number_verified", "Whether the user confirmed this phone number."),
            Map.entry("role", "The Postgres role used by Row Level Security. \"authenticated\" means a signed-in user."),
            Map.entry("aal", "Assurance level: aal1 is one factor (password, link), aal2 means the user passed MFA."),
            Map.entry("amr", "How the user authenticated, and when."),
            Map.entry("session_id", "The Supabase session behind this token. Signing out revokes it."),
            Map.entry("sid", "The Supabase session behind this token."),
            Map.entry("is_anonymous", "Whether this is an anonymous (guest) user."),
            Map.entry("app_metadata", "Data only Supabase or your backend can change, like the sign-in provider. Safe to use for authorization."),
            Map.entry("user_metadata", "Data the user can edit themselves. Never use it for authorization."),
            Map.entry("client_id", "The OAuth app the token was issued to: this Spring app."),
            Map.entry("azp", "The OAuth app the token was issued to: this Spring app."),
            Map.entry("scope", "What the user approved on the consent page."),
            Map.entry("nonce", "A random value Spring sent when you clicked sign in. It proves this ID token answers that request."),
            Map.entry("at_hash", "A hash of the access token, tying the two tokens together."),
            Map.entry("jti", "A unique ID for this token."),
            Map.entry("name", "The user's name, if known."),
            Map.entry("picture", "The user's avatar URL, if known."),
            Map.entry("preferred_username", "The user's username, if known."));

    public static List<ClaimView> of(Map<String, Object> claims) {
        return new TreeMap<>(claims).entrySet().stream()
                .map(claim -> of(claim.getKey(), claim.getValue()))
                .toList();
    }

    private static ClaimView of(String name, Object value) {
        String meaning = MEANINGS.getOrDefault(name, "");
        if (value instanceof Instant instant) {
            return new ClaimView(name, instant.toString(), true, meaning);
        }
        if (TIME_CLAIMS.contains(name) && value instanceof Number seconds) {
            return new ClaimView(name, Instant.ofEpochSecond(seconds.longValue()).toString(), true, meaning);
        }
        String text = value instanceof String string ? string : toJson(value);
        return new ClaimView(name, text, false, meaning);
    }

    /** Minimal JSON rendering for claim values (maps, lists, strings, numbers, booleans). */
    static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            var json = new StringBuilder("{");
            map.forEach((key, item) -> json.append(json.length() > 1 ? ", " : "")
                    .append(toJson(String.valueOf(key))).append(": ").append(toJson(item)));
            return json.append('}').toString();
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(ClaimView::toJson).toList().toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Instant instant) {
            return toJson(instant.toString());
        }
        String escaped = value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }
}
