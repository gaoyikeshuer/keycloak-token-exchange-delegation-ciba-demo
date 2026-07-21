package com.acme.tedemo.model;


public record ExchangeResult(boolean success, String accessToken, String scope, String error) {
}
