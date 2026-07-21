package com.acme.tedemo.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;


@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
        // Real sign-out: end the Keycloak SSO session too, not just the local one — otherwise Keycloak
        // logs you straight back in from its cookie and you never look logged out.
        OidcClientInitiatedLogoutSuccessHandler oidcLogout =
                new OidcClientInitiatedLogoutSuccessHandler(clients);
        oidcLogout.setPostLogoutRedirectUri("{baseUrl}");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/favicon.ico", "/error").permitAll()
                // Keycloak calls this server-to-server (bearer token, no browser) — leave it open.
                .requestMatchers("/ciba/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(withDefaults())
            .logout(logout -> logout.logoutSuccessHandler(oidcLogout))
            // Demo only — the channel callback is server-to-server and the device page uses plain form
            // posts. Don't disable CSRF like this in a real app.
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
