package com.acme.tedemo.controller;

import com.acme.tedemo.model.PendingApproval;
import com.acme.tedemo.service.DeviceApprovalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
public class AuthenticationChannelController {

    private final DeviceApprovalService approvals;

    public AuthenticationChannelController(DeviceApprovalService approvals) {
        this.approvals = approvals;
    }

    @PostMapping(path = "/ciba/request-authentication-channel", consumes = "application/json")
    public ResponseEntity<Void> receive(@RequestHeader("Authorization") String authorization,
                                        @RequestBody Map<String, Object> body) {
        approvals.record(new PendingApproval(
                str(body, "login_hint"),
                str(body, "binding_message"),
                str(body, "scope"),
                Boolean.parseBoolean(str(body, "is_consent_required")),
                stripBearer(authorization)));
        // 201 Created tells Keycloak the request reached the device; the customer still has to approve.
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static String stripBearer(String header) {
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return header;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
