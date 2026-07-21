package com.acme.tedemo.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;



@ConfigurationProperties(prefix = "demo.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String clientId, String clientSecret) {

    public String realmProtocolBase() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect";
    }

    public String tokenEndpoint() {
        return realmProtocolBase() + "/token";
    }
}
