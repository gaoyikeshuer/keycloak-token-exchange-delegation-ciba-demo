package com.acme.tedemo.service;

import com.acme.tedemo.model.AuthRequest;
import com.acme.tedemo.model.PollResult;


public interface CibaService {


    String CIBA_GRANT_TYPE = "urn:openid:params:grant-type:ciba";


    AuthRequest backchannelAuthenticate(String loginHint, String scope, String bindingMessage);


    PollResult poll(String authReqId);


    class CibaException extends RuntimeException {
        public CibaException(String message) {
            super(message);
        }
    }
}
