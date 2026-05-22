package com.werkflow.engine.action;

import com.werkflow.engine.audit.ProcessAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Fire-and-forget outbound webhook delegate for BPMN SendTask / WEBHOOK_OUT connectors.
 * Calls the admin connector proxy with method=POST and only requires a 2xx ACK.
 * The process continues immediately regardless of the downstream response body.
 *
 * <p>Extends {@link ConnectorDelegateBase} for parity with {@code RestConnectorDelegate}
 * and {@code DatabaseConnectorDelegate}: shared field helpers, audit logging, and meter
 * counters are inherited. Response storage ({@link #storeResult}) is intentionally NOT
 * called — webhook is fire-and-forget with no response variable concept.</p>
 *
 * <p>Error handling is kept local (not delegated to {@link #handleError}) because the
 * webhook error code is {@code CONNECTOR_WEBHOOK_ERROR}, distinct from the base
 * {@code CONNECTOR_ERROR}, and the CONTINUE branch does not write response variables.</p>
 *
 * BPMN extension fields (all via &lt;flowable:field&gt;):
 *   connectorKey — required; matches admin_service.tenant_service_endpoints.connector_key
 *   path         — required; webhook endpoint path, e.g. /events/purchase-order-created
 *   body         — optional; JSON payload (supports ${variable} interpolation via process vars)
 *   onError      — optional; FAIL (default) | CONTINUE | THROW_BPMN_ERROR
 */
@Slf4j
@Component("connectorWebhookDelegate")
public class ConnectorWebhookDelegate extends ConnectorDelegateBase {

    private final RestTemplate serviceRestTemplate;
    private final String adminServiceUrl;

    public ConnectorWebhookDelegate(
            @Qualifier("serviceRestTemplate") RestTemplate serviceRestTemplate,
            @Value("${app.admin-service.url}") String adminServiceUrl,
            ResponseMasker responseMasker,
            ProcessAuditLogRepository auditLogRepository,
            MeterRegistry meterRegistry) {
        super(responseMasker, auditLogRepository, meterRegistry);
        this.serviceRestTemplate = serviceRestTemplate;
        this.adminServiceUrl = adminServiceUrl;
    }

    @Override
    protected String resolveActionType() {
        return "WEBHOOK_OUT";
    }

    @Override
    public void execute(DelegateExecution execution) {
        String onErrorMode = getFieldString(execution, "onError", "FAIL");
        String callUrl = null;

        try {
            String key = getFieldString(execution, "connectorKey", null);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("connectorWebhookDelegate: 'connectorKey' field is required");
            }
            String webhookPath = getFieldString(execution, "path", null);
            if (webhookPath == null || webhookPath.isBlank()) {
                throw new IllegalArgumentException("connectorWebhookDelegate: 'path' field is required");
            }
            String payload = getFieldString(execution, "body", null);
            String tenantCode = execution.getTenantId();
            if (tenantCode == null || tenantCode.isBlank()) {
                throw new IllegalStateException(
                    "connectorWebhookDelegate: execution has no tenantId — connector mode requires a tenant-scoped process");
            }

            callUrl = UriComponentsBuilder.fromHttpUrl(adminServiceUrl)
                .path("/api/connectors/{key}/call")
                .queryParam("tenantCode", tenantCode)
                .queryParam("path", webhookPath)
                .queryParam("method", "POST")
                .buildAndExpand(key)
                .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(payload != null ? payload : "{}", headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<Map> response = serviceRestTemplate.postForEntity(callUrl, entity, Map.class);
            long durationMs = System.currentTimeMillis() - startTime;

            Map<?, ?> result = response.getBody();
            int statusCode = result != null && result.containsKey("statusCode")
                ? ((Number) result.get("statusCode")).intValue()
                : response.getStatusCode().value();

            writeAuditLog(execution, callUrl, "POST", null, statusCode, durationMs, false, List.of(), null);

            if (statusCode < 200 || statusCode >= 300) {
                log.warn("connectorWebhookDelegate: non-2xx ACK {} for connector={} path={}",
                    statusCode, key, webhookPath);
            } else {
                log.info("connectorWebhookDelegate: ACK {} tenantCode={} connectorKey={} path={}",
                    statusCode, tenantCode, key, webhookPath);
            }

        } catch (Exception e) {
            writeAuditLog(execution, callUrl, "POST", null, null, 0, false, List.of(), e.getMessage());
            log.error("connectorWebhookDelegate error [{}]: {}", onErrorMode, e.getMessage());
            if ("CONTINUE".equals(onErrorMode)) {
                log.info("connectorWebhookDelegate: CONTINUE — process proceeds despite error");
            } else if ("THROW_BPMN_ERROR".equals(onErrorMode)) {
                throw new BpmnError("CONNECTOR_WEBHOOK_ERROR", e.getMessage());
            } else {
                throw new RuntimeException("connectorWebhookDelegate failed: " + e.getMessage(), e);
            }
        }
    }
}
