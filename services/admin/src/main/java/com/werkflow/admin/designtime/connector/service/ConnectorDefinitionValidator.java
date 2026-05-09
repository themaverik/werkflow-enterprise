package com.werkflow.admin.designtime.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates ConnectorDefinition JSON documents against the canonical
 * {@code connector-definition.schema.json} (JSON Schema 2020-12).
 *
 * <p>Loaded once at startup; the compiled schema is reused for all validations.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorDefinitionValidator {

    private static final String SCHEMA_PATH =
            "schemas/connector/v1/connector-definition.schema.json";

    private final ObjectMapper objectMapper;

    private JsonSchema compiledSchema;

    @PostConstruct
    public void init() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            compiledSchema = factory.getSchema(is);
        }
        log.info("ConnectorDefinitionValidator initialised from classpath:{}", SCHEMA_PATH);
    }

    /**
     * Validates a JSON string against the ConnectorDefinition schema.
     *
     * @param json raw connector definition JSON
     * @throws ConnectorValidationException if validation fails; message contains all violation messages
     */
    public void validate(String json) {
        Set<ValidationMessage> errors = collectErrors(json);
        if (!errors.isEmpty()) {
            String detail = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            throw new ConnectorValidationException("Connector definition is invalid: " + detail);
        }
    }

    /**
     * Returns {@code true} if the JSON passes schema validation without throwing.
     */
    public boolean isValid(String json) {
        return collectErrors(json).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Set<ValidationMessage> collectErrors(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return compiledSchema.validate(node);
        } catch (IOException e) {
            throw new ConnectorValidationException("Connector JSON is not parseable: " + e.getMessage(), e);
        }
    }

    /**
     * Thrown when a ConnectorDefinition fails JSON Schema validation.
     */
    public static final class ConnectorValidationException extends RuntimeException {
        public ConnectorValidationException(String message) {
            super(message);
        }

        public ConnectorValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
