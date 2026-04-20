package com.werkflow.engine.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Keycloak Admin Client.
 * Used to interact with Keycloak Admin REST API for user/role/group management.
 */
@Configuration
public class KeycloakAdminConfig {

    @Value("${keycloak.auth-server-url:http://keycloak:8080}")
    private String authServerUrl;

    @Value("${keycloak.realm:werkflow-platform}")
    private String realm;

    @Value("${keycloak.admin.client-id:werkflow-engine}")
    private String clientId;

    @Value("${keycloak.admin.client-secret:REDACTED_ROTATE_THIS_SECRET}")
    private String clientSecret;

    /**
     * Create Keycloak Admin Client bean.
     * Uses service account (client credentials) for authentication.
     *
     * @return Keycloak admin client
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    }
}
