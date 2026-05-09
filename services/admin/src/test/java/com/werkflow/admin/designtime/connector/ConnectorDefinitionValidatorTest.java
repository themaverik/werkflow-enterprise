package com.werkflow.admin.designtime.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.designtime.connector.service.ConnectorDefinitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates that {@link ConnectorDefinitionValidator} correctly accepts valid
 * connector definitions and rejects invalid ones according to the JSON Schema.
 *
 * <p>Uses the real schema loaded from classpath so this also tests that the schema
 * file is accessible at the expected path.</p>
 */
class ConnectorDefinitionValidatorTest {

    private ConnectorDefinitionValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new ConnectorDefinitionValidator(new ObjectMapper());
        validator.init();
    }

    // -------------------------------------------------------------------------
    // Valid connector
    // -------------------------------------------------------------------------

    @Test
    void validate_minimalValidConnector_passes() {
        String json = minimalConnector("test-connector");
        validator.validate(json); // must not throw
    }

    @Test
    void isValid_minimalValidConnector_returnsTrue() {
        assertThat(validator.isValid(minimalConnector("my-connector"))).isTrue();
    }

    // -------------------------------------------------------------------------
    // Missing required fields
    // -------------------------------------------------------------------------

    @Test
    void validate_missingApiVersion_throws() {
        String json = """
            {
              "kind": "ConnectorDefinition",
              "metadata": { "key": "x-connector", "displayName": "X", "version": "1.0.0" },
              "spec": {
                "transport": { "type": "rest", "config": { "baseUrl": "https://example.com" } },
                "operations": [
                  { "id": "defaultOp", "displayName": "Default", "transportSpecific": { "method": "GET", "path": "/" } }
                ]
              }
            }
            """;
        assertThatThrownBy(() -> validator.validate(json))
                .isInstanceOf(ConnectorDefinitionValidator.ConnectorValidationException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void validate_wrongApiVersion_throws() {
        String json = minimalConnector("ok-connector")
                .replace("werkflow.io/connector/v1", "werkflow.io/connector/v99");
        assertThatThrownBy(() -> validator.validate(json))
                .isInstanceOf(ConnectorDefinitionValidator.ConnectorValidationException.class);
    }

    @Test
    void validate_missingKind_throws() {
        String json = """
            {
              "apiVersion": "werkflow.io/connector/v1",
              "metadata": { "key": "x-connector", "displayName": "X", "version": "1.0.0" },
              "spec": {
                "transport": { "type": "rest", "config": { "baseUrl": "https://example.com" } },
                "operations": [
                  { "id": "defaultOp", "displayName": "Default", "transportSpecific": { "method": "GET", "path": "/" } }
                ]
              }
            }
            """;
        assertThatThrownBy(() -> validator.validate(json))
                .isInstanceOf(ConnectorDefinitionValidator.ConnectorValidationException.class);
    }

    // -------------------------------------------------------------------------
    // Invalid key format
    // -------------------------------------------------------------------------

    @Test
    void validate_invalidKeyFormat_throws() {
        // key must match ^[a-z][a-z0-9-]{1,62}[a-z0-9]$
        String json = minimalConnector("UPPER_CASE_KEY");
        assertThatThrownBy(() -> validator.validate(json))
                .isInstanceOf(ConnectorDefinitionValidator.ConnectorValidationException.class);
    }

    // -------------------------------------------------------------------------
    // Malformed JSON
    // -------------------------------------------------------------------------

    @Test
    void validate_malformedJson_throwsValidationException() {
        assertThatThrownBy(() -> validator.validate("{not valid json"))
                .isInstanceOf(ConnectorDefinitionValidator.ConnectorValidationException.class)
                .hasMessageContaining("parseable");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String minimalConnector(String key) {
        return """
            {
              "apiVersion": "werkflow.io/connector/v1",
              "kind": "ConnectorDefinition",
              "metadata": {
                "key": "%s",
                "displayName": "Test Connector",
                "version": "1.0.0"
              },
              "spec": {
                "transport": {
                  "type": "rest",
                  "config": { "baseUrl": "https://example.com" }
                },
                "operations": [
                  {
                    "id": "defaultOp",
                    "displayName": "Default Operation",
                    "transportSpecific": { "method": "GET", "path": "/" }
                  }
                ]
              }
            }
            """.formatted(key);
    }
}
