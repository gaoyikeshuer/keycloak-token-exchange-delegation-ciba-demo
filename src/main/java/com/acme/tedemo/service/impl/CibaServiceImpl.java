package com.acme.tedemo.service.impl;

import com.acme.tedemo.configuration.KeycloakClient;
import com.acme.tedemo.model.AuthRequest;
import com.acme.tedemo.model.PollResult;
import com.acme.tedemo.model.PollResult.PollStatus;
import com.acme.tedemo.service.CibaService;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class CibaServiceImpl implements CibaService {

    private final KeycloakClient kc;

    public CibaServiceImpl(KeycloakClient kc) {
        this.kc = kc;
    }

    @Override
    public AuthRequest backchannelAuthenticate(String loginHint, String scope, String bindingMessage) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("scope", scope);
        form.add("login_hint", loginHint);
        if (bindingMessage != null && !bindingMessage.isBlank()) {
            form.add("binding_message", bindingMessage);
        }

        KeycloakClient.FormResponse res = kc.postForm(cibaAuthEndpoint(), form);
        if (!res.isSuccess()) {
            throw new CibaException("Backchannel auth failed: " + res.error()
                    + " — " + res.string("error_description"));
        }
        String authReqId = res.string("auth_req_id");
        if (authReqId == null) {
            throw new CibaException("Keycloak did not return auth_req_id: " + res.raw());
        }
        return new AuthRequest(authReqId, intOr(res, "interval", 5), intOr(res, "expires_in", 120));
    }

    @Override
    public PollResult poll(String authReqId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", CIBA_GRANT_TYPE);
        form.add("auth_req_id", authReqId);

        KeycloakClient.FormResponse res = kc.postForm(kc.props().tokenEndpoint(), form);
        if (res.isSuccess()) {
            return new PollResult(PollStatus.SUCCESS,
                    res.string("access_token"), res.string("refresh_token"), res.string("scope"), null);
        }
        String error = res.error();
        return switch (error == null ? "" : error) {
            case "authorization_pending", "slow_down" -> PollResult.of(PollStatus.PENDING, error);
            case "access_denied" -> PollResult.of(PollStatus.DENIED, res.string("error_description"));
            case "expired_token" -> PollResult.of(PollStatus.EXPIRED, res.string("error_description"));
            default -> PollResult.of(PollStatus.ERROR, error + " — " + res.string("error_description"));
        };
    }

    private String cibaAuthEndpoint() {
        return kc.props().realmProtocolBase() + "/ext/ciba/auth";
    }

    private static int intOr(KeycloakClient.FormResponse res, String key, int fallback) {
        Object v = res.body().get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }
}
