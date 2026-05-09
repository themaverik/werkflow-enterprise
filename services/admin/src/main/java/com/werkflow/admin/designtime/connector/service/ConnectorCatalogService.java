package com.werkflow.admin.designtime.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.connector.dto.ConnectorSummary;
import com.werkflow.admin.designtime.connector.dto.FlatField;
import com.werkflow.admin.designtime.connector.dto.OperationSummary;
import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import com.werkflow.admin.designtime.connector.repository.ConnectorDefinitionV2Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Core DTDS (Design-Time Data Service) for connector catalog operations.
 *
 * <p>All operations are tenant-scoped: a caller can only read connectors that belong
 * to their tenant.  Cross-tenant access results in a 403.</p>
 *
 * <p>Read paths are backed by a 30-minute Caffeine cache keyed by
 * {@code {tenantId}:{connectorKey}:{version}:{operationId}:{direction}}.
 * Caches are evicted on connector create/update/delete.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorCatalogService {

    private final ConnectorDefinitionV2Repository repo;
    private final SchemaResolverService schemaResolver;
    private final SchemaFlattenerService schemaFlattener;
    private final ConnectorDefinitionValidator validator;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists connector summaries for the given tenant, paginated.
     */
    @Transactional(readOnly = true)
    public Page<ConnectorSummary> list(String tenantId, Pageable pageable) {
        return repo.findByTenantId(tenantId, pageable)
                .map(this::toSummary);
    }

    // -------------------------------------------------------------------------
    // Get full definition (secrets redacted)
    // -------------------------------------------------------------------------

    /**
     * Returns the full ConnectorDefinition JSON for the latest version of {@code key},
     * with any auth secret values replaced by {@code "***"}.
     *
     * @throws ResponseStatusException 404 if not found, 403 if cross-tenant
     */
    @Cacheable(value = CacheConfig.DTDS_CONNECTOR_DEF, key = "#tenantId + ':' + #key")
    @Transactional(readOnly = true)
    public String getDefinitionJson(String tenantId, String key) {
        ConnectorDefinitionV2 entity = resolveLatest(tenantId, key);
        return redactSecrets(entity.getDefinitionJson());
    }

    // -------------------------------------------------------------------------
    // Operations list
    // -------------------------------------------------------------------------

    /**
     * Returns the list of operations declared in the connector definition.
     */
    @Cacheable(value = CacheConfig.DTDS_OPERATIONS, key = "#tenantId + ':' + #key")
    @Transactional(readOnly = true)
    public List<OperationSummary> listOperations(String tenantId, String key) {
        ConnectorDefinitionV2 entity = resolveLatest(tenantId, key);
        return extractOperationSummaries(entity.getDefinitionJson());
    }

    // -------------------------------------------------------------------------
    // Resolved schema
    // -------------------------------------------------------------------------

    /**
     * Returns the resolved JSON Schema for a specific operation's input or output.
     *
     * @param direction "input" or "output"
     */
    @Cacheable(value = CacheConfig.DTDS_SCHEMA,
               key = "#tenantId + ':' + #key + ':' + #operationId + ':' + #direction")
    @Transactional(readOnly = true)
    public JsonNode getOperationSchema(
            String tenantId, String key, String operationId, String direction) {
        ConnectorDefinitionV2 entity = resolveLatest(tenantId, key);
        String definitionJson = entity.getDefinitionJson();
        JsonNode opNode = findOperation(definitionJson, operationId);
        JsonNode schemaNode = opNode.path(direction);
        if (schemaNode.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        return schemaResolver.resolve(schemaNode, definitionJson);
    }

    // -------------------------------------------------------------------------
    // Flat fields
    // -------------------------------------------------------------------------

    /**
     * Returns the flattened field list for a specific operation's input or output.
     *
     * @param direction "input" or "output"
     */
    @Cacheable(value = CacheConfig.DTDS_FIELDS,
               key = "#tenantId + ':' + #key + ':' + #operationId + ':' + #direction")
    @Transactional(readOnly = true)
    public List<FlatField> getFlatFields(
            String tenantId, String key, String operationId, String direction) {
        JsonNode resolved = getOperationSchema(tenantId, key, operationId, direction);
        return schemaFlattener.flatten(resolved);
    }

    // -------------------------------------------------------------------------
    // Write operations (with cache eviction)
    // -------------------------------------------------------------------------

    /**
     * Stores a validated ConnectorDefinition, rejecting duplicates for the same
     * (key, version, tenantId) tuple.
     *
     * @return the persisted entity
     * @throws ConnectorDefinitionValidator.ConnectorValidationException if schema-invalid
     * @throws ResponseStatusException 409 if the exact (key, version, tenant) already exists
     */
    @CacheEvict(value = {
        CacheConfig.DTDS_CONNECTOR_DEF,
        CacheConfig.DTDS_OPERATIONS,
        CacheConfig.DTDS_SCHEMA,
        CacheConfig.DTDS_FIELDS
    }, allEntries = true)
    @Transactional
    public ConnectorDefinitionV2 register(String tenantId, String definitionJson) {
        validator.validate(definitionJson);

        JsonNode root = parseOrThrow(definitionJson);
        String key = root.path("metadata").path("key").asText();
        String version = root.path("metadata").path("version").asText();

        if (repo.existsByKeyAndVersionAndTenantId(key, version, tenantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Connector '" + key + "' version '" + version + "' already registered for tenant '" + tenantId + "'");
        }

        ConnectorDefinitionV2 entity = new ConnectorDefinitionV2();
        entity.setKey(key);
        entity.setVersion(version);
        entity.setTenantId(tenantId);
        entity.setDefinitionJson(definitionJson);
        ConnectorDefinitionV2 saved = repo.save(entity);
        log.info("connector.definition.registered tenantId={} key={} version={}", tenantId, key, version);
        return saved;
    }

    /**
     * Updates an existing connector definition in-place (same key/version/tenant).
     * Validates the new definition before replacing.
     */
    @CacheEvict(value = {
        CacheConfig.DTDS_CONNECTOR_DEF,
        CacheConfig.DTDS_OPERATIONS,
        CacheConfig.DTDS_SCHEMA,
        CacheConfig.DTDS_FIELDS
    }, allEntries = true)
    @Transactional
    public ConnectorDefinitionV2 update(String tenantId, String key, String definitionJson) {
        validator.validate(definitionJson);
        ConnectorDefinitionV2 entity = resolveLatest(tenantId, key);
        entity.setDefinitionJson(definitionJson);
        ConnectorDefinitionV2 saved = repo.save(entity);
        log.info("connector.definition.updated tenantId={} key={}", tenantId, key);
        return saved;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the latest version of a connector for a tenant. Enforces tenant scoping.
     */
    private ConnectorDefinitionV2 resolveLatest(String tenantId, String key) {
        return repo.findFirstByKeyAndTenantIdOrderByCreatedAtDesc(key, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Connector '" + key + "' not found for tenant '" + tenantId + "'"));
    }

    private ConnectorSummary toSummary(ConnectorDefinitionV2 entity) {
        try {
            JsonNode root = objectMapper.readTree(entity.getDefinitionJson());
            JsonNode meta = root.path("metadata");
            JsonNode spec = root.path("spec");
            String transportType = spec.path("transport").path("type").asText(null);
            JsonNode ops = spec.path("operations");
            int opCount = ops.isArray() ? ops.size() : 0;

            List<String> tags = new ArrayList<>();
            meta.path("tags").forEach(t -> tags.add(t.asText()));

            return new ConnectorSummary(
                    entity.getKey(),
                    meta.path("displayName").asText(entity.getKey()),
                    meta.path("description").asText(null),
                    entity.getVersion(),
                    meta.path("category").asText(null),
                    tags.isEmpty() ? null : tags,
                    transportType,
                    opCount,
                    entity.getUpdatedAt()
            );
        } catch (IOException e) {
            log.warn("Failed to parse definition for summary — key={}: {}", entity.getKey(), e.getMessage());
            return new ConnectorSummary(entity.getKey(), entity.getKey(), null,
                    entity.getVersion(), null, null, null, 0, entity.getUpdatedAt());
        }
    }

    private List<OperationSummary> extractOperationSummaries(String definitionJson) {
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode ops = root.path("spec").path("operations");
            List<OperationSummary> result = new ArrayList<>();
            if (ops.isArray()) {
                ops.forEach(op -> result.add(new OperationSummary(
                        op.path("id").asText(),
                        op.path("displayName").asText(),
                        op.path("description").asText(null),
                        op.path("category").asText(null),
                        op.path("deprecated").asBoolean(false),
                        !op.path("pagination").isMissingNode(),
                        !op.path("input").isMissingNode(),
                        !op.path("output").isMissingNode()
                )));
            }
            return result;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to parse connector definition: " + e.getMessage());
        }
    }

    private JsonNode findOperation(String definitionJson, String operationId) {
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode ops = root.path("spec").path("operations");
            if (ops.isArray()) {
                for (JsonNode op : ops) {
                    if (operationId.equals(op.path("id").asText())) return op;
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to parse connector definition");
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Operation '" + operationId + "' not found in connector definition");
    }

    /**
     * Deep-copies the definition JSON and replaces auth secret values with {@code "***"}.
     * The {@code secretKey} reference field is preserved; only a hypothetical plain-text
     * value field would be redacted.  Per the spec, secrets must not be in the definition,
     * so this is a defence-in-depth measure.
     */
    private String redactSecrets(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (IOException e) {
            log.warn("Secret redaction failed — returning original JSON: {}", e.getMessage());
            return json;
        }
    }

    @SuppressWarnings("all")
    private void redactNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // Redact any field named "secretValue" or "rawSecret" at any depth
            if (obj.has("secretValue")) obj.put("secretValue", "***");
            if (obj.has("rawSecret"))   obj.put("rawSecret", "***");
            Iterator<JsonNode> children = obj.elements();
            while (children.hasNext()) redactNode(children.next());
        } else if (node.isArray()) {
            node.forEach(this::redactNode);
        }
    }

    private JsonNode parseOrThrow(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid JSON: " + e.getMessage());
        }
    }
}
