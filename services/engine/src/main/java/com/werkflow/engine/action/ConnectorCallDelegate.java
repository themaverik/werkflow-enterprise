package com.werkflow.engine.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Calls an external API through the admin connector proxy and maps response fields
 * to process variables using the connector's variable_mappings configuration.
 *
 * BPMN extension fields (all via &lt;flowable:field&gt;):
 *   connectorKey     — required; matches admin_service.tenant_service_endpoints.connector_key
 *   path             — required; e.g. /departments
 *   method           — optional; default GET
 *   body             — optional; JSON string for POST/PUT/PATCH
 *   responseVariable — optional; stores raw response body; default "connectorResponse"
 *   variableMappings — optional; JSON array of VariableMapping objects
 *   onError          — optional; FAIL (default) | CONTINUE | THROW_BPMN_ERROR
 */
@Slf4j
@Component("connectorCallDelegate")
public class ConnectorCallDelegate implements JavaDelegate {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<VariableMapping>> MAPPINGS_TYPE =
        new TypeReference<>() {};

    private final RestTemplate serviceRestTemplate;
    private final String adminServiceUrl;

    @Setter private Expression connectorKey;
    @Setter private Expression path;
    @Setter private Expression method;
    @Setter private Expression body;
    @Setter private Expression responseVariable;
    @Setter private Expression variableMappings;
    @Setter private Expression onError;

    public ConnectorCallDelegate(
            @Qualifier("serviceRestTemplate") RestTemplate serviceRestTemplate,
            @Value("${app.admin-service.url}") String adminServiceUrl) {
        this.serviceRestTemplate = serviceRestTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String onErrorMode = getString(onError, execution, "FAIL");
        String responseVar = getString(responseVariable, execution, "connectorResponse");

        try {
            String key = getString(connectorKey, execution, null);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("connectorCallDelegate: 'connectorKey' field is required");
            }
            String connPath = getString(path, execution, null);
            if (connPath == null || connPath.isBlank()) {
                throw new IllegalArgumentException("connectorCallDelegate: 'path' field is required");
            }
            String httpMethod = getString(method, execution, "GET").toUpperCase();
            String requestBody = getString(body, execution, null);
            String tenantCode = execution.getTenantId();
            if (tenantCode == null || tenantCode.isBlank()) {
                throw new IllegalStateException(
                    "connectorCallDelegate: execution has no tenantId — connector mode requires a tenant-scoped process");
            }

            String callUrl = UriComponentsBuilder.fromHttpUrl(adminServiceUrl)
                .path("/api/connectors/{key}/call")
                .queryParam("tenantCode", tenantCode)
                .queryParam("path", connPath)
                .queryParam("method", httpMethod)
                .buildAndExpand(key)
                .toUriString();

            ResponseEntity<Map> response;
            if (requestBody != null && !requestBody.isBlank()) {
                response = serviceRestTemplate.postForEntity(
                    callUrl,
                    new org.springframework.http.HttpEntity<>(requestBody,
                        org.springframework.http.HttpHeaders.EMPTY),
                    Map.class
                );
            } else {
                response = serviceRestTemplate.postForEntity(callUrl, null, Map.class);
            }

            Map<?, ?> payload = response.getBody();
            String responseBody = payload != null && payload.containsKey("body")
                ? String.valueOf(payload.get("body"))
                : "";

            execution.setVariable(responseVar, responseBody);

            String mappingsJson = getString(variableMappings, execution, null);
            if (mappingsJson != null && !mappingsJson.isBlank()) {
                applyVariableMappings(responseBody, mappingsJson, execution);
            }

            log.info("connectorCallDelegate: tenantCode={} connectorKey={} path={} method={} status={}",
                tenantCode, key, connPath, httpMethod,
                payload != null ? payload.get("statusCode") : "?");

        } catch (Exception e) {
            log.error("connectorCallDelegate error [{}]: {}", onErrorMode, e.getMessage());
            if ("CONTINUE".equals(onErrorMode)) {
                execution.setVariable(responseVar + "Success", false);
                execution.setVariable(responseVar + "Error", e.getMessage());
            } else if ("THROW_BPMN_ERROR".equals(onErrorMode)) {
                throw new BpmnError("CONNECTOR_CALL_ERROR", e.getMessage());
            } else {
                throw new RuntimeException("connectorCallDelegate failed: " + e.getMessage(), e);
            }
        }
    }

    private void applyVariableMappings(String responseBody, String mappingsJson,
                                       DelegateExecution execution) {
        List<VariableMapping> mappings;
        try {
            mappings = MAPPER.readValue(mappingsJson, MAPPINGS_TYPE);
        } catch (Exception e) {
            log.warn("connectorCallDelegate: could not parse variableMappings JSON: {}", e.getMessage());
            return;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            log.warn("connectorCallDelegate: response body is not valid JSON — skipping variable mappings");
            return;
        }

        for (VariableMapping m : mappings) {
            JsonNode node = root.path(m.responseField());
            if (node.isMissingNode() || node.isNull()) {
                if (Boolean.TRUE.equals(m.required())) {
                    log.warn("connectorCallDelegate: required field '{}' missing in response", m.responseField());
                }
                continue;
            }
            Object value = coerce(node, m.type());
            execution.setVariable(m.variableName(), value);
            log.debug("connectorCallDelegate: {} -> {} = {}", m.responseField(), m.variableName(), value);
        }
    }

    private Object coerce(JsonNode node, String type) {
        if (type == null) return node.asText();
        return switch (type.toLowerCase()) {
            case "number", "integer", "long" -> node.isLong() ? node.longValue() : node.asLong();
            case "double", "float"           -> node.asDouble();
            case "boolean"                   -> node.asBoolean();
            default                          -> node.asText();
        };
    }

    private String getString(Expression expr, DelegateExecution execution, String defaultValue) {
        if (expr == null) return defaultValue;
        Object val = expr.getValue(execution);
        return val != null ? val.toString() : defaultValue;
    }

    /** Mirrors the variable_mappings JSON schema from the M9 spec. */
    private record VariableMapping(
        String responseField,
        String variableName,
        String type,
        Boolean required
    ) {}
}
