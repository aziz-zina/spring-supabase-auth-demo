package tn.azizzina.supabasedemo;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public Supabase settings, also handed to the browser for the consent page.
 *
 * @param url            project URL, e.g. https://abcd.supabase.co
 * @param publishableKey publishable (anon) key, safe to expose
 */
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(String url, String publishableKey) {

    /** Supabase Auth's issuer: the base of every OAuth endpoint. */
    public String issuer() {
        return url + "/auth/v1";
    }

    /** The project ref, e.g. "abcd" for https://abcd.supabase.co. */
    public String projectRef() {
        String host = URI.create(url).getHost();
        return host == null ? url : host.split("\\.")[0];
    }
}
