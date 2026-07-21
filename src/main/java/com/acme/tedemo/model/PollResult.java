package com.acme.tedemo.model;

public record PollResult(PollStatus status, String accessToken, String refreshToken, String scope, String detail) {

    public enum PollStatus {SUCCESS, PENDING, DENIED, EXPIRED, ERROR}

    public static PollResult of(PollStatus status, String detail) {
        return new PollResult(status, null, null, null, detail);
    }
}
