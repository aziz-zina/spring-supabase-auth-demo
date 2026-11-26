package tn.azizzina.supabasedemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pages Supabase does not host for you.
 *
 * <p>Supabase's OAuth 2.1 server redirects the browser to {@code <Site URL>/oauth/consent?authorization_id=...}.
 * That page must sign the user in to Supabase and approve or deny the request. It is plain HTML + supabase-js.
 */
@Controller
public class ConsentController {

    private final SupabaseProperties supabase;

    public ConsentController(SupabaseProperties supabase) {
        this.supabase = supabase;
    }

    @GetMapping("/oauth/consent")
    public String consent(Model model) {
        model.addAttribute("supabase", supabase);
        return "consent";
    }

    @GetMapping("/oauth/signed-out")
    public String signedOut(Model model) {
        model.addAttribute("supabase", supabase);
        return "signed-out";
    }
}
