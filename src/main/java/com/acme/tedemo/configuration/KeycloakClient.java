package com.acme.tedemo.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;


@Component
public class KeycloakClient {

    private final KeycloakProperties props;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.create();

    public KeycloakClient(KeycloakProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public KeycloakProperties props() {
        return props;
    }

    // Result of a form POST: HTTP status, parsed JSON body (best-effort), and the raw body. 
    public record FormResponse(int status, Map<String, Object> body, String raw) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        public String string(String key) {
            Object v = body.get(key);
            return v == null ? null : String.valueOf(v);
        }

        // OAuth error code from an error response (the "error" field), or null.
        public String error() {
            return string("error");
        }
    }


    public FormResponse postForm(String url, MultiValueMap<String, String> form) {
        return http.post()
                .uri(url)
                .header("Authorization", basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .exchange((request, response) -> {
                    String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    return new FormResponse(response.getStatusCode().value(), parse(raw), raw);
                });
    }

  
    public int postJsonBearer(String url, String bearerToken, Object body) {
        return http.post()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String basicAuth() {
        String creds = props.clientId() + ":" + props.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }
}
