package com.acme.tedemo.service;

import com.acme.tedemo.model.ExchangeResult;


public interface TokenExchangeService {

    /**
     * @param subjectToken the subject user's token (must carry {@code may_act} for the actor)
     * @param actorToken   the acting admin's access token
     */
    ExchangeResult delegate(String subjectToken, String actorToken);
}
