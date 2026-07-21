package com.acme.tedemo.controller;

import com.acme.tedemo.service.DeviceApprovalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class DeviceController {

    private final DeviceApprovalService approvals;

    public DeviceController(DeviceApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/device")
    public String inbox(@AuthenticationPrincipal OidcUser customer, Model model) {
        String username = customer.getPreferredUsername();
        model.addAttribute("customer", username);
        model.addAttribute("approvals", approvals.pendingFor(username));
        return "device";
    }

    @PostMapping("/device/approve")
    public String approve(@RequestParam String id, @AuthenticationPrincipal OidcUser customer) {
        approvals.approve(id, customer.getPreferredUsername());
        return "redirect:/device";
    }

    @PostMapping("/device/deny")
    public String deny(@RequestParam String id, @AuthenticationPrincipal OidcUser customer) {
        approvals.deny(id, customer.getPreferredUsername());
        return "redirect:/device";
    }
}
