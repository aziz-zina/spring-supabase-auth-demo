package tn.azizzina.supabasedemo;

import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small JSON helpers for display: compact and pretty printing, reading a JWT header field. */
final class Json {

    private static final Pattern HEADER_FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    private Json() {
    }

    static String compact(Object value) {
        return write(value, null, 0);
    }

    static String pretty(Object value) {
        return write(value, "  ", 0);
    }

    /** A string field of a JWT's header (e.g. "alg", "kid"), without verifying anything. */
    static String headerField(String jwt, String field) {
        if (jwt == null || jwt.indexOf('.') < 0) {
            return null;
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(jwt.substring(0, jwt.indexOf('.'))));
            Matcher matcher = HEADER_FIELD.matcher(header);
            while (matcher.find()) {
                if (matcher.group(1).equals(field)) {
                    return matcher.group(2);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // not base64url: not a JWT
        }
        return null;
    }

    private static String write(Object value, String indent, int depth) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            var json = new StringBuilder("{");
            boolean first = true;
            for (var entry : map.entrySet()) {
                json.append(first ? "" : separator(indent)).append(newline(indent, depth + 1))
                        .append(string(String.valueOf(entry.getKey()))).append(": ")
                        .append(write(entry.getValue(), indent, depth + 1));
                first = false;
            }
            return json.append(newline(indent, depth)).append('}').toString();
        }
        if (value instanceof Collection<?> items) {
            if (items.isEmpty()) {
                return "[]";
            }
            var json = new StringBuilder("[");
            boolean first = true;
            for (Object item : items) {
                json.append(first ? "" : separator(indent)).append(newline(indent, depth + 1)).append(write(item, indent, depth + 1));
                first = false;
            }
            return json.append(newline(indent, depth)).append(']').toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Instant instant) {
            return string(instant.toString());
        }
        return string(value.toString());
    }

    private static String newline(String indent, int depth) {
        return indent == null ? "" : "\n" + indent.repeat(depth);
    }

    private static String separator(String indent) {
        return indent == null ? ", " : ",";
    }

    private static String string(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
