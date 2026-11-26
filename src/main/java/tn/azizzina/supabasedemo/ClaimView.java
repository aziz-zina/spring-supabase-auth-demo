package tn.azizzina.supabasedemo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One row of a claims table: a readable value and what the claim means.
 *
 * @param name    claim name, e.g. "sub"
 * @param value   readable value (compact JSON for objects and arrays, ISO-8601 for times)
 * @param time    true when {@code value} is an ISO-8601 instant (the page shows it in local time)
 * @param meaning short explanation, empty when unknown
 */
public record ClaimView(String name, String value, boolean time, String meaning) {

    private static final Set<String> TIME_CLAIMS = Set.of("exp", "iat", "nbf", "auth_time", "updated_at");

    private static final Map<String, String> MEANINGS = Map.ofEntries(
            Map.entry("iss", "Your project's Auth server"),
            Map.entry("sub", "User ID in Supabase (auth.users.id)"),
            Map.entry("aud", "Intended audience. APIs should check it"),
            Map.entry("exp", "Expiry"),
            Map.entry("iat", "Issued at"),
            Map.entry("nbf", "Not valid before"),
            Map.entry("auth_time", "When the user signed in"),
            Map.entry("email", "User's email"),
            Map.entry("email_verified", "Email confirmed"),
            Map.entry("phone", "User's phone number"),
            Map.entry("phone_number", "User's phone number"),
            Map.entry("phone_verified", "Phone number confirmed"),
            Map.entry("phone_number_verified", "Phone number confirmed"),
            Map.entry("role", "Postgres role used by RLS"),
            Map.entry("aal", "One factor. aal2 after MFA"),
            Map.entry("amr", "How the user signed in"),
            Map.entry("session_id", "Supabase session. Sign-out revokes it"),
            Map.entry("sid", "Supabase session"),
            Map.entry("is_anonymous", "Anonymous (guest) user"),
            Map.entry("app_metadata", "Set by Supabase or your backend only. Safe for authorization"),
            Map.entry("user_metadata", "Editable by the user. Never use for authorization"),
            Map.entry("client_id", "OAuth app the token was issued to"),
            Map.entry("azp", "OAuth app the token was issued to"),
            Map.entry("scope", "What the user allowed on the consent page"),
            Map.entry("nonce", "Ties this ID token to the sign-in request Spring sent"),
            Map.entry("at_hash", "Hash of the access token, links the two tokens"),
            Map.entry("jti", "Unique token ID"),
            Map.entry("name", "User's name"),
            Map.entry("picture", "User's avatar URL"),
            Map.entry("preferred_username", "User's username"),
            Map.entry("updated_at", "Last profile update"));

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
        String text = value instanceof String string ? string : Json.compact(value);
        return new ClaimView(name, text, false, meaning);
    }
}
