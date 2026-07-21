package com.acme.tedemo.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Base64;
import java.util.Map;


public final class JwtUtils {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JwtUtils() {
    }

    // The token's claims, or an empty map if it can't be decoded.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> claims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return Map.of();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // The token's claims as pretty-printed JSON, for display. 
    public static String prettyClaims(String jwt) {
        try {
            return MAPPER.writeValueAsString(claims(jwt));
        } catch (Exception e) {
            return "<unable to decode token>";
        }
    }
}
