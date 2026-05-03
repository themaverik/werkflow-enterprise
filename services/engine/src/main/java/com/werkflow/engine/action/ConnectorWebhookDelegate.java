package com.werkflow.engine.action;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Fire-and-forget outbound webhook delegate for BPMN SendTask / WEBHOOK_OUT connectors.
 * Calls the admin connector proxy with method=POST and only requires a 2xx ACK.
 * The process continues immediately regardless of the downstream response body.
 *
 * BPMN extension fields (all via &lt;flowable:field&gt;):
 *   connectorKey — required; matches admin_service.tenant_service_endpoints.connector_key
 *   path         — required; webhook endpoint path, e.g. /events/purchase-order-created
 *   body         — optional; JSON payload (supports ${variable} interpolation via process vars)
 *   onError      — optional; FAIL (default) | CONTINUE | THROW_BPMN_ERROR
 */
@Slf4j
@Component("connectorWebhookDelegate")
public class ConnectorWebhookDelegate implements JavaDelegate {

    private final RestTemplate serviceRestTemplate;
    private final String adminServiceUrl;

    @Setter private Expression connectorKey;
    @Setter private Expression path;
    @Setter private Expression body;
    @Setter private Expression onError;

    public ConnectorWebhookDelegate(
            @Qualifier("serviceRestTemplate") RestTemplate serviceRestTemplate,
            @Value("${app.admin-service.url}") String adminServiceUrl) {
        this.serviceRestTemplate = serviceRestTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String onErrorMode = getString(onError, execution, "FAIL");

        try {
            String key = getString(connectorKey, execution, null);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("connectorWebhookDelegate: 'connectorKey' field is required");
            }
            String webhookPath = getString(path, execution, null);
            if (webhookPath == null || webhookPath.isBlank()) {
                throw new IllegalArgumentException("connectorWebhookDelegate: 'path' field is required");
            }
            String payload = getString(body, execution, null);
            String tenantCode = execution.getTenantId();
            if (tenantCode == null || tenantCode.isBlank()) {
                throw new IllegalStateException(
                    "connectorWebhookDelegate: execution has no tenantId — connector mode requires a tenant-scoped process");
            }

            String callUrl = UriComponentsBuilder.fromHttpUrl(adminServiceUrl)
                .path("/api/connectors/{key}/call")
                .queryParam("tenantCode", tenantCode)
                .queryParam("path", webhookPath)
                .queryParam("method", "POST")
                .buildAndExpand(key)
                .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(payload != null ? payload : "{}", headers);
            ResponseEntity<Map> response = serviceRestTemplate.postForEntity(callUrl, entity, Map.class);

            Map<?, ?> result = response.getBody();
            int statusCode = result != null && result.containsKey("statusCode")
                ? ((Number) result.get("statusCode")).intValue()
                : response.getStatusCode().value();

            if (statusCode < 200 || statusCode >= 300) {
                log.warn("connectorWebhookDelegate: non-2xx ACK {} for connector={} path={}",
                    statusCode, key, webhookPath);
            } else {
                log.info("connectorWebhookDelegate: ACK {} tenantCode={} connectorKey={} path={}",
                    statusCode, tenantCode, key, webhookPath);
            }

        } catch (Exception e) {
            log.error("connectorWebhookDelegate error [{}]: {}", onErrorMode, e.getMessage());
            switch (onErrorMode) {
                case "CONTINUE" -> log.info("connectorWebhookDelegate: CONTINUE — process proceeds despite error");
                case "THROW_BPMN_ERROR" -> throw new BpmnError("CONNECTOR_WEBHOOK_ERROR", e.getMessage());
                default -> throw new RuntimeException("connectorWebhookDelegate failed: " + e.getMessage(), e);
            }
        }
    }

    private String getString(Expression expr, DelegateExecution execution, String defaultValue) {
        if (expr == null) return defaultValue;
        Object val = expr.getValue(execution);
        return val != null ? val.toString() : defaultValue;
    }
}
