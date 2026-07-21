package com.acme.tedemo.controller;

import com.acme.tedemo.model.DelegationOutcome;
import com.acme.tedemo.service.DelegationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;


@Controller
public class ConsoleController {

    private static final String S_AUTH_REQ_ID = "authReqId";
    private static final String S_TARGET_USER = "targetUser";
    private static final String S_ACTOR_TOKEN = "actorToken";
    private static final String S_RESULT = "result";

    private final DelegationService delegation;

    public ConsoleController(DelegationService delegation) {
        this.delegation = delegation;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal OidcUser admin, HttpSession session, Model model) {
        model.addAttribute("adminUsername", admin.getPreferredUsername());
        Object result = session.getAttribute(S_RESULT);
        if (result != null) {
            model.addAttribute("result", result);
            session.removeAttribute(S_RESULT);
        }
        return "index";
    }

    @PostMapping("/delegate")
    public String delegate(@RequestParam String targetUser,
                           @RequestParam(required = false) String bindingMessage,
                           @AuthenticationPrincipal OidcUser admin,
                           @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient adminClient,
                           HttpSession session,
                           Model model) {
        String target = targetUser.trim();
        try {
            String authReqId = delegation.start(target, bindingMessage, admin.getPreferredUsername());
            session.setAttribute(S_AUTH_REQ_ID, authReqId);
            session.setAttribute(S_TARGET_USER, target);
            // Admin's OIDC access token is the actor_token for the exchange.
            session.setAttribute(S_ACTOR_TOKEN, adminClient.getAccessToken().getTokenValue());
            return "redirect:/waiting";
        } catch (RuntimeException e) {
            model.addAttribute("adminUsername", admin.getPreferredUsername());
            model.addAttribute("error", e.getMessage());
            return "index";
        }
    }

    @GetMapping("/waiting")
    public String waiting(HttpSession session, Model model) {
        if (session.getAttribute(S_AUTH_REQ_ID) == null) {
            return "redirect:/";
        }
        model.addAttribute("targetUser", session.getAttribute(S_TARGET_USER));
        return "waiting";
    }

    /** JS on the waiting page polls this; returns a small JSON status object. */
    @GetMapping("/delegate/poll")
    @ResponseBody
    public Map<String, String> poll(HttpSession session) {
        String authReqId = (String) session.getAttribute(S_AUTH_REQ_ID);
        if (authReqId == null) {
            return Map.of("status", "error", "detail", "No pending delegation request.");
        }
        String actorToken = (String) session.getAttribute(S_ACTOR_TOKEN);
        String targetUser = (String) session.getAttribute(S_TARGET_USER);

        DelegationOutcome outcome = delegation.poll(authReqId, actorToken, targetUser);
        if (!"pending".equals(outcome.status())) {
            session.removeAttribute(S_AUTH_REQ_ID);
            session.removeAttribute(S_ACTOR_TOKEN);
            if (outcome.result() != null) {
                session.setAttribute(S_RESULT, outcome.result());
            }
        }
        return (outcome.detail() == null || outcome.detail().isEmpty())
                ? Map.of("status", outcome.status())
                : Map.of("status", outcome.status(), "detail", outcome.detail());
    }
}
