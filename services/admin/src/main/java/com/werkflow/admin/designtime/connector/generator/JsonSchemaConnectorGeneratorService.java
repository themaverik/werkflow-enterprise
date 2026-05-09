package com.werkflow.admin.designtime.connector.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import com.werkflow.admin.designtime.connector.service.ConnectorCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Generates a stub ConnectorDefinition from a JSON Schema document.
 *
 * <p>This is a convenience generator for demos and integration testing — it creates a
 * single-operation connector with {@code transport.type=rest} and the submitted schema
 * as the operation's {@code inputSchema}. The base URL and auth must be configured
 * separately after import.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsonSchemaConnectorGeneratorService {

    private final ConnectorCatalogService catalogService;
    private final ObjectMapper objectMapper;

    /**
     * Generates and registers a single-operation connector definition from a JSON Schema.
     *
     * @param tenantId   caller's tenant
     * @param request    generator request containing key, display name, and schema
     * @return the persisted ConnectorDefinitionV2 entity
     * @throws ResponseStatusException 400 if the schema JSON is not parseable
     */
    public ConnectorDefinitionV2 generate(String tenantId, JsonSchemaGeneratorRequest request) {
        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(request.schemaJson());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "schemaJson is not valid JSON: " + e.getMessage());
        }

        ObjectNode definition = objectMapper.createObjectNode();

        // metadata
        ObjectNode metadata = definition.putObject("metadata");
        metadata.put("key", request.connectorKey());
        metadata.put("version", "1.0.0");
        metadata.put("displayName", request.displayName());
        metadata.put("description", "Auto-generated from JSON Schema by Werkflow generator");
        metadata.putArray("tags").add("generated");

        // spec
        ObjectNode spec = definition.putObject("spec");

        // transport (stub — URL must be configured post-import)
        ObjectNode transport = spec.putObject("transport");
        transport.put("type", "rest");
        ObjectNode transportConfig = transport.putObject("config");
        transportConfig.put("baseUrl", "https://example.com");
        ObjectNode auth = transportConfig.putObject("auth");
        auth.put("type", "none");

        // operations
        ArrayNode operations = spec.putArray("operations");
        ObjectNode op = operations.addObject();
        op.put("id", "execute");
        op.put("displayName", "Execute");
        op.put("description", "Single generated operation from JSON Schema input");
        op.set("inputSchema", schemaNode);

        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(definition);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to serialise generated connector definition: " + e.getMessage());
        }

        log.info("json-schema.generator: tenantId={} connectorKey={}", tenantId, request.connectorKey());
        return catalogService.register(tenantId, definitionJson);
    }
}
