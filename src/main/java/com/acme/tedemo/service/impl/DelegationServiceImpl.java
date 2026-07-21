package com.acme.tedemo.service.impl;

import com.acme.tedemo.configuration.JwtUtils;
import com.acme.tedemo.model.DelegationOutcome;
import com.acme.tedemo.model.DelegationResult;
import com.acme.tedemo.model.ExchangeResult;
import com.acme.tedemo.model.PollResult;
import com.acme.tedemo.service.CibaService;
import com.acme.tedemo.service.DelegationService;
import com.acme.tedemo.service.TokenExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DelegationServiceImpl implements DelegationService {

    private static final Logger log = LoggerFactory.getLogger(DelegationServiceImpl.class);

    private final CibaService ciba;
    private final TokenExchangeService exchange;

    public DelegationServiceImpl(CibaService ciba, TokenExchangeService exchange) {
        this.ciba = ciba;
        this.exchange = exchange;
    }

    @Override
    public String start(String targetUser, String bindingMessage, String adminUsername) {
        String scope = "openid delegation:" + adminUsername;
        String authReqId = ciba.backchannelAuthenticate(targetUser, scope, bindingMessage).authReqId();
        log.info("Started CIBA for '{}' with scope '{}' (authReqId issued)", targetUser, scope);
        return authReqId;
    }

    @Override
    public DelegationOutcome poll(String authReqId, String actorToken, String targetUser) {
        PollResult res = ciba.poll(authReqId);
        return switch (res.status()) {
            case PENDING -> DelegationOutcome.pending();
            case DENIED -> DelegationOutcome.denied(nz(res.detail()));
            case EXPIRED -> DelegationOutcome.error("Approval timed out: " + nz(res.detail()));
            case ERROR -> DelegationOutcome.error(nz(res.detail()));
            case SUCCESS -> exchangeFor(res.accessToken(), actorToken, targetUser);
        };
    }

    // The subject token has approval (and {@code may_act}); exchange it against the actor token. 
    private DelegationOutcome exchangeFor(String subjectToken, String actorToken, String targetUser) {
        ExchangeResult ex = exchange.delegate(subjectToken, actorToken);
        if (!ex.success()) {
            log.warn("Token exchange failed: {}", ex.error());
            return DelegationOutcome.error("Token exchange failed: " + nz(ex.error()));
        }

        // may_act (the permission, on the subject token) becomes act (the record, on the delegated token).
        DelegationResult result = new DelegationResult(
                targetUser,
                subOf(JwtUtils.claims(subjectToken), "may_act"),
                subOf(JwtUtils.claims(ex.accessToken()), "act"),
                nz(ex.scope()),
                JwtUtils.prettyClaims(subjectToken),
                JwtUtils.prettyClaims(ex.accessToken()));
        log.info("Delegation exchange succeeded: actor acts for '{}'", targetUser);
        return DelegationOutcome.done(result);
    }

    // The {@code sub} inside a nested claim like {@code act} / {@code may_act}, or "(none)".
    private static String subOf(Map<String, Object> claims, String claim) {
        return (claims.get(claim) instanceof Map<?, ?> m && m.get("sub") != null)
                ? String.valueOf(m.get("sub"))
                : "(none)";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
