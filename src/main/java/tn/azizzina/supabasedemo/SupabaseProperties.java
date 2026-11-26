package tn.azizzina.supabasedemo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public Supabase settings handed to the browser for the consent page.
 *
 * @param url            project URL, e.g. https://abcd.supabase.co
 * @param publishableKey publishable (anon) key, safe to expose
 */
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(String url, String publishableKey) {
}
