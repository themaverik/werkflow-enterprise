package com.werkflow.engine.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.common.security.SecretsResolver;
import com.werkflow.engine.action.db.DatasourceRegistry;
import com.werkflow.engine.action.db.KeysetPaginator;
import com.werkflow.engine.action.db.NamedQueryExecutor;
import com.werkflow.engine.action.db.PageResult;
import com.werkflow.engine.audit.ProcessAuditLogRepository;
import com.werkflow.engine.client.AdminServiceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable JavaDelegate for executing named, parameterized SQL queries from BPMN
 * service tasks via the database transport.
 *
 * <p>This delegate never executes raw SQL from BPMN inputs. All SQL lives in the
 * connector definition's {@code transport.config.queries[]} array, registered by an
 * admin/DBA and validated for DML at registration time. The BPMN process references
 * a query by its stable {@code operationId}, which maps to a {@code queryRef}.</p>
 *
 * <p>Extends {@link ConnectorDelegateBase} to inherit audit logging, response masking,
 * JSONPath extraction, variable storage, and error-mode dispatch.</p>
 */
@Slf4j
@Component("databaseConnectorDelegate")
public class DatabaseConnectorDelegate extends ConnectorDelegateBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AdminServiceClient adminServiceClient;
    private final DatasourceRegistry datasourceRegistry;
    private final NamedQueryExecutor queryExecutor;
    private final KeysetPaginator keysetPaginator;

    // -------------------------------------------------------------------------
    // BPMN expression fields (set by Flowable from <flowable:field> elements)
    // -------------------------------------------------------------------------

    /** Connector key matching a registered ConnectorDefinition with transport.type=database. */
    @Setter private Expression connector;

    /** Operation ID declared in the connector definition's operations array. */
    @Setter private Expression operationId;

    /**
     * JSON object expression mapping parameter names to values.
     * Example: {@code {"departmentCode": "${deptCode}", "limit": 50}}
     */
    @Setter private Expression queryParams;

    /** Result mode override: SINGLE, LIST, or PAGINATED. Falls back to the query spec's resultMode. */
    @Setter private Expression resultMode;

    /** Keyset cursor column name for PAGINATED mode (e.g. "id"). */
    @Setter private Expression cursorParam;

    /** Initial cursor value for PAGINATED mode; null for the first page. */
    @Setter private Expression cursorValue;

    /** Page size for PAGINATED mode; falls back to query spec's rowLimit or 100. */
    @Setter private Expression pageSize;

    public DatabaseConnectorDelegate(ResponseMasker responseMasker,
                                     SecretsResolver secretsResolver,
                                     ProcessAuditLogRepository auditLogRepository,
                                     MeterRegistry meterRegistry,
                                     AdminServiceClient adminServiceClient,
                                     DatasourceRegistry datasourceRegistry,
                                     NamedQueryExecutor queryExecutor,
                                     KeysetPaginator keysetPaginator) {
        super(responseMasker, secretsResolver, auditLogRepository, meterRegistry);
        this.adminServiceClient = adminServiceClient;
        this.datasourceRegistry = datasourceRegistry;
        this.queryExecutor = queryExecutor;
        this.keysetPaginator = keysetPaginator;
    }

    @Override
    protected String resolveActionType() {
        return "DATABASE_CONNECTOR_CALL";
    }

    @Override
    public void execute(DelegateExecution execution) {
        String onErrorMode = getString(onError, execution, "FAIL");
        String responseVar = getString(responseVariable, execution, "response");

        String connectorKey = null;
        String opId = null;

        try {
            connectorKey = getString(connector, execution, null);
            if (connectorKey == null || connectorKey.isBlank()) {
                throw new IllegalArgumentException(
                    "databaseConnectorDelegate: 'connector' field is required on task '"
                    + execution.getCurrentActivityId() + "'");
            }

            opId = getString(operationId, execution, null);
            if (opId == null || opId.isBlank()) {
                throw new IllegalArgumentException(
                    "databaseConnectorDelegate: 'operationId' field is required on task '"
                    + execution.getCurrentActivityId() + "'");
            }

            String tenantCode = execution.getTenantId();
            if (tenantCode == null || tenantCode.isBlank()) {
                throw new IllegalStateException(
                    "databaseConnectorDelegate: execution has no tenantId — requires a tenant-scoped process");
            }

            // Fetch and parse the connector definition from the admin service
            String definitionJson = adminServiceClient.getConnectorDefinitionJson(tenantCode, connectorKey);
            JsonNode definition = parseDefinition(definitionJson);

            // Extract transport config
            JsonNode transportConfig = definition.path("spec").path("transport").path("config");
            String datasourceRef = transportConfig.path("datasourceRef").asText(null);
            if (datasourceRef == null || datasourceRef.isBlank()) {
                throw new IllegalStateException(
                    "Connector '" + connectorKey + "' has no datasourceRef in transport config");
            }
            boolean readOnly = transportConfig.path("readOnly").asBoolean(true);

            // Resolve the named query by operationId → queryRef
            JsonNode querySpec = resolveQuerySpec(definition, opId, transportConfig);
            String sql = querySpec.path("sql").asText();
            int rowLimit = querySpec.path("rowLimit").asInt(NamedQueryExecutor.DEFAULT_MAX_ROWS);
            int queryTimeout = querySpec.path("queryTimeoutSeconds").asInt(NamedQueryExecutor.DEFAULT_TIMEOUT_SECONDS);
            String specResultMode = querySpec.path("resultMode").asText("array");

            // Build parameter map from expression field
            Map<String, Object> params = resolveQueryParams(execution);

            // Resolve datasource and apply circuit breaker
            DataSource dataSource = datasourceRegistry.resolve(tenantCode, datasourceRef);
            CircuitBreaker cb = datasourceRegistry.circuitBreaker(tenantCode, connectorKey);

            String effectiveMode = resolveResultMode(execution, specResultMode);
            long startTime = System.currentTimeMillis();

            String rawResult;
            try {
                rawResult = cb.executeCheckedSupplier(() ->
                    executeQuery(dataSource, sql, params, rowLimit, queryTimeout, readOnly, effectiveMode, execution));
            } catch (CallNotPermittedException e) {
                throw new RuntimeException(
                    "Circuit breaker open for connector '" + connectorKey + "' — database may be unavailable", e);
            } catch (Throwable t) {
                if (t instanceof RuntimeException re) throw re;
                throw new RuntimeException(t);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            writeAuditLog(execution, datasourceRef + ":" + opId, "QUERY",
                          null, 200, durationMs, false, List.of(), null);

            storeResult(rawResult, execution);

        } catch (Exception e) {
            String ctx = connectorKey + ":" + opId;
            handleError(e, onErrorMode, responseVar, execution, ctx);
        }
    }

    // -------------------------------------------------------------------------
    // Query execution
    // -------------------------------------------------------------------------

    private String executeQuery(DataSource ds,
                                String sql,
                                Map<String, Object> params,
                                int rowLimit,
                                int timeoutSecs,
                                boolean readOnly,
                                String effectiveMode,
                                DelegateExecution execution) throws IOException {
        return switch (effectiveMode.toUpperCase()) {
            case "SINGLE", "OBJECT" -> {
                Map<String, Object> row = queryExecutor.executeSingle(ds, sql, params, timeoutSecs, readOnly);
                yield MAPPER.writeValueAsString(row);
            }
            case "PAGINATED" -> {
                String cursorCol = getString(cursorParam, execution, "id");
                Object initialCursor = parseCursorValue(getString(cursorValue, execution, null));
                int pgSize = parseIntExpression(getString(pageSize, execution, null), rowLimit);
                PageResult result = keysetPaginator.fetchAll(
                    ds, sql, params, cursorCol, initialCursor, pgSize, timeoutSecs, readOnly);
                // Store next cursor as a separate variable for the caller to chain subsequent pages
                execution.setVariable(getString(responseVariable, execution, "response") + "NextCursor",
                    result.nextCursor());
                yield MAPPER.writeValueAsString(result.rows());
            }
            default -> { // LIST / ARRAY
                List<Map<String, Object>> rows = queryExecutor.executeList(
                    ds, sql, params, rowLimit, timeoutSecs, readOnly);
                yield MAPPER.writeValueAsString(rows);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Definition parsing helpers
    // -------------------------------------------------------------------------

    private JsonNode parseDefinition(String json) throws IOException {
        return MAPPER.readTree(json);
    }

    /**
     * Navigates spec.operations[] to find the operation by {@code opId}, then
     * resolves its {@code transportSpecific.queryRef} to a named query in
     * {@code transport.config.queries[]}.
     */
    private JsonNode resolveQuerySpec(JsonNode definition, String opId, JsonNode transportConfig) {
        JsonNode ops = definition.path("spec").path("operations");
        String queryRef = null;
        if (ops.isArray()) {
            for (JsonNode op : ops) {
                if (opId.equals(op.path("id").asText())) {
                    queryRef = op.path("transportSpecific").path("queryRef").asText(null);
                    break;
                }
            }
        }
        if (queryRef == null) {
            // Fall back: treat operationId as a direct queryRef (convenience for simple defs)
            queryRef = opId;
        }

        JsonNode queries = transportConfig.path("queries");
        if (queries.isArray()) {
            for (JsonNode q : queries) {
                if (queryRef.equals(q.path("id").asText())) {
                    return q;
                }
            }
        }
        throw new IllegalStateException(
            "Query '" + queryRef + "' not found in connector transport config (referenced by operation '" + opId + "')");
    }

    private Map<String, Object> resolveQueryParams(DelegateExecution execution) {
        String paramsJson = getString(queryParams, execution, null);
        if (paramsJson == null || paramsJson.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(paramsJson, MAP_TYPE);
        } catch (IOException e) {
            log.warn("databaseConnectorDelegate: could not parse queryParams JSON '{}': {}", paramsJson, e.getMessage());
            return Map.of();
        }
    }

    private String resolveResultMode(DelegateExecution execution, String specResultMode) {
        String expressionMode = getString(resultMode, execution, null);
        if (expressionMode != null && !expressionMode.isBlank()) return expressionMode;
        // Map connector spec result modes to our execution modes
        return switch (specResultMode.toLowerCase()) {
            case "object" -> "SINGLE";
            case "scalar" -> "SINGLE";
            case "void" -> "LIST";  // void operations return empty list
            default -> "LIST";       // array → LIST
        };
    }

    private static Object parseCursorValue(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return null;
        try { return Long.parseLong(raw); } catch (NumberFormatException ignored) {}
        return raw;
    }

    private static int parseIntExpression(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try { return Integer.parseInt(raw.trim()); } catch (NumberFormatException ignored) {}
        return defaultValue;
    }
}
