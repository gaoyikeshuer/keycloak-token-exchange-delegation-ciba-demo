package com.acme.tedemo.model;


public record DelegationOutcome(String status, String detail, DelegationResult result) {

    public static DelegationOutcome pending() {
        return new DelegationOutcome("pending", null, null);
    }

    public static DelegationOutcome denied(String detail) {
        return new DelegationOutcome("denied", detail, null);
    }

    public static DelegationOutcome error(String detail) {
        return new DelegationOutcome("error", detail, null);
    }

    public static DelegationOutcome done(DelegationResult result) {
        return new DelegationOutcome("done", null, result);
    }
}
