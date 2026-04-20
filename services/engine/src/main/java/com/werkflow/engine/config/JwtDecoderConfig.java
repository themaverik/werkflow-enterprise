package com.werkflow.engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

import java.util.Arrays;
import java.util.List;

/**
 * Custom JWT Decoder configuration for Keycloak integration.
 *
 * This configuration solves the issuer mismatch problem that occurs when:
 * - Browsers access Keycloak via http://localhost:8090/realms/werkflow (external URL)
 * - Backend services access Keycloak via http://keycloak:8080/realms/werkflow (internal Docker URL)
 *
 * The custom decoder:
 * - Fetches JWK sets from the internal Keycloak URL (keycloak:8080)
 * - Accepts tokens issued from EITHER localhost:8090 OR keycloak:8080
 * - Maintains all standard JWT validations (signature, expiry, etc.)
 *
 * This approach provides:
 * - Secure token validation using JWK public keys
 * - Flexibility for local development and Docker deployments
 * - No token manipulation or security compromises
 */
@Configuration
public class JwtDecoderConfig {

    private static final Logger logger = LoggerFactory.getLogger(JwtDecoderConfig.class);

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Creates a custom JwtDecoder that accepts tokens from multiple valid issuers.
     *
     * The decoder validates:
     * 1. Token signature using JWK public keys from Keycloak
     * 2. Token expiry (timestamps)
     * 3. Token issuer (must be one of the allowed issuers)
     * 4. All other standard JWT claims
     *
     * @return Configured JwtDecoder instance
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        logger.info("Configuring custom JWT decoder with JWK Set URI: {}", jwkSetUri);

        // Create the base decoder using JWK sets from Keycloak
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri(jwkSetUri)
            .build();

        // Define valid issuers - both external and internal URLs
        List<String> validIssuers = Arrays.asList(
            "http://localhost:8090/realms/werkflow",  // External/browser access
            "http://keycloak:8080/realms/werkflow"    // Internal Docker network
        );

        logger.info("Configured valid JWT issuers: {}", validIssuers);

        // Create a custom issuer validator that accepts multiple issuers
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(validIssuers);

        // Create timestamp validator (checks exp, nbf claims)
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();

        // Combine all validators
        OAuth2TokenValidator<Jwt> combinedValidator = new DelegatingOAuth2TokenValidator<>(
            issuerValidator,
            timestampValidator
        );

        // Apply the combined validator to the decoder
        jwtDecoder.setJwtValidator(combinedValidator);

        logger.info("JWT decoder successfully configured with multi-issuer support");

        return jwtDecoder;
    }

    /**
     * Custom JWT Issuer Validator that accepts multiple valid issuer URLs.
     *
     * This validator allows tokens issued from any of the configured Keycloak URLs
     * (both external localhost:8090 and internal keycloak:8080) to pass validation.
     */
    private static class JwtIssuerValidator implements OAuth2TokenValidator<Jwt> {

        private static final Logger validatorLogger = LoggerFactory.getLogger(JwtIssuerValidator.class);
        private final List<String> validIssuers;

        public JwtIssuerValidator(List<String> validIssuers) {
            this.validIssuers = validIssuers;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String tokenIssuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;

            if (tokenIssuer == null) {
                validatorLogger.error("JWT validation failed: Token has no issuer claim");
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token must have an issuer claim", null)
                );
            }

            // Check if the token's issuer matches any of our valid issuers
            boolean isValid = validIssuers.stream()
                .anyMatch(validIssuer -> validIssuer.equals(tokenIssuer));

            if (isValid) {
                validatorLogger.debug("JWT issuer validation passed for issuer: {}", tokenIssuer);
                return OAuth2TokenValidatorResult.success();
            } else {
                validatorLogger.error(
                    "JWT validation failed: Token issuer '{}' not in list of valid issuers: {}",
                    tokenIssuer,
                    validIssuers
                );
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                        "invalid_token",
                        String.format("Invalid issuer: %s. Expected one of: %s", tokenIssuer, validIssuers),
                        null
                    )
                );
            }
        }
    }
}
