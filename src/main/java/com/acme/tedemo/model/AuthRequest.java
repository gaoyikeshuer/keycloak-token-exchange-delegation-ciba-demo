package com.acme.tedemo.model;


public record AuthRequest(String authReqId, int intervalSeconds, int expiresInSeconds) {
}
