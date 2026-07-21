package com.acme.tedemo.model;

import java.util.UUID;


public class PendingApproval {

    public enum State {PENDING, APPROVED, DENIED}

    private final String id = UUID.randomUUID().toString();
    private final long createdAt = System.currentTimeMillis();
    private final String loginHint;
    private final String bindingMessage;
    private final String scope;
    private final boolean consentRequired;
    private final String bearerToken;
    private volatile State state = State.PENDING;

    public PendingApproval(String loginHint, String bindingMessage, String scope,
                           boolean consentRequired, String bearerToken) {
        this.loginHint = loginHint;
        this.bindingMessage = bindingMessage;
        this.scope = scope;
        this.consentRequired = consentRequired;
        this.bearerToken = bearerToken;
    }

    public String requestedActor() {
        if (scope == null) {
            return "(unknown)";
        }
        for (String s : scope.split(" ")) {
            if (s.startsWith("delegation:")) {
                return s.substring("delegation:".length());
            }
        }
        return "(none)";
    }

    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public String getBindingMessage() {
        return bindingMessage;
    }

    public String getScope() {
        return scope;
    }

    public boolean isConsentRequired() {
        return consentRequired;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
