package com.acme.tedemo.model;


public record DelegationResult(
        String subject,
        String mayActSub,
        String actor,
        String scope,
        String decodedSubjectToken,
        String decodedToken) {
}
