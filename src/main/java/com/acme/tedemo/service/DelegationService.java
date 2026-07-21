package com.acme.tedemo.service;

import com.acme.tedemo.model.DelegationOutcome;


public interface DelegationService {

    String start(String targetUser, String bindingMessage, String adminUsername);

    DelegationOutcome poll(String authReqId, String actorToken, String targetUser);
}
