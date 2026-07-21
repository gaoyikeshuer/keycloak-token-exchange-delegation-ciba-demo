package com.acme.tedemo.service.impl;

import com.acme.tedemo.configuration.KeycloakClient;
import com.acme.tedemo.model.ExchangeResult;
import com.acme.tedemo.service.TokenExchangeService;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class TokenExchangeServiceImpl implements TokenExchangeService {

    private static final String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final KeycloakClient kc;

    public TokenExchangeServiceImpl(KeycloakClient kc) {
        this.kc = kc;
    }

    @Override
    public ExchangeResult delegate(String subjectToken, String actorToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", TOKEN_EXCHANGE_GRANT_TYPE);
        form.add("subject_token", subjectToken);
        form.add("subject_token_type", ACCESS_TOKEN_TYPE);
        form.add("actor_token", actorToken);
        form.add("actor_token_type", ACCESS_TOKEN_TYPE);
        form.add("requested_token_type", ACCESS_TOKEN_TYPE);

        KeycloakClient.FormResponse res = kc.postForm(kc.props().tokenEndpoint(), form);
        if (res.isSuccess()) {
            return new ExchangeResult(true, res.string("access_token"), res.string("scope"), null);
        }
        return new ExchangeResult(false, null, null,
                res.error() + " — " + res.string("error_description"));
    }
}
