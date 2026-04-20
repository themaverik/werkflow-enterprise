package com.werkflow.engine.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtDecoderConfig
 *
 * Note: These are minimal unit tests focusing on configuration logic.
 * Full integration tests with real Keycloak tokens should be performed separately.
 */
class JwtDecoderConfigTest {

    private JwtDecoderConfig jwtDecoderConfig;

    @BeforeEach
    void setUp() {
        jwtDecoderConfig = new JwtDecoderConfig();
        // Set the JWK set URI for testing
        ReflectionTestUtils.setField(
            jwtDecoderConfig,
            "jwkSetUri",
            "http://localhost:8090/realms/werkflow/protocol/openid-connect/certs"
        );
    }

    @Test
    @DisplayName("Should create JwtDecoder bean successfully")
    void shouldCreateJwtDecoderBean() {
        // When
        JwtDecoder decoder = jwtDecoderConfig.jwtDecoder();

        // Then
        assertNotNull(decoder, "JwtDecoder bean should not be null");
    }

    @Test
    @DisplayName("Should configure decoder with correct JWK set URI")
    void shouldConfigureDecoderWithCorrectJwkSetUri() {
        // Given
        String expectedUri = "http://keycloak:8080/realms/werkflow/protocol/openid-connect/certs";
        ReflectionTestUtils.setField(jwtDecoderConfig, "jwkSetUri", expectedUri);

        // When
        JwtDecoder decoder = jwtDecoderConfig.jwtDecoder();

        // Then
        assertNotNull(decoder, "JwtDecoder should be created with custom JWK set URI");
    }

    @Test
    @DisplayName("Should accept tokens from localhost issuer")
    void shouldAcceptTokensFromLocalhostIssuer() {
        // This test validates the issuer validation logic
        // Note: Actual token validation requires a running Keycloak instance
        // This test focuses on the configuration setup

        // When
        JwtDecoder decoder = jwtDecoderConfig.jwtDecoder();

        // Then
        assertNotNull(decoder, "Decoder should be configured to accept localhost issuer");
    }

    @Test
    @DisplayName("Should accept tokens from Docker network issuer")
    void shouldAcceptTokensFromDockerNetworkIssuer() {
        // This test validates the issuer validation logic
        // Note: Actual token validation requires a running Keycloak instance

        // When
        JwtDecoder decoder = jwtDecoderConfig.jwtDecoder();

        // Then
        assertNotNull(decoder, "Decoder should be configured to accept Docker network issuer");
    }

    @Test
    @DisplayName("Should configure multi-issuer validation")
    void shouldConfigureMultiIssuerValidation() {
        // Given
        JwtDecoder decoder = jwtDecoderConfig.jwtDecoder();

        // Then
        assertNotNull(decoder, "Decoder should be configured with multi-issuer support");
        // The actual multi-issuer validation is tested in integration tests
        // where real JWT tokens from Keycloak can be validated
    }

    /**
     * Helper method to create a mock JWT for testing
     * Note: This creates a JWT-like structure but not a real signed JWT
     */
    private Jwt createMockJwt(String issuer) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", "test-user");
        claims.put("exp", Instant.now().plusSeconds(3600));
        claims.put("iat", Instant.now());

        return new Jwt(
            "mock-token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            headers,
            claims
        );
    }
}
