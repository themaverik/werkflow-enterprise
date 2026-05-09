package com.werkflow.engine.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.webhook.WebhookCorrelator.WebhookCorrelationException;
import com.werkflow.engine.webhook.entity.WebhookUndelivered;
import com.werkflow.engine.webhook.repository.WebhookUndeliveredRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Inbound webhook receiver.
 *
 * <p>Endpoint: {@code POST /api/v1/webhooks/{tenantCode}/{connectorKey}}
 *
 * <p>Pipeline per request:
 * <ol>
 *   <li>Resolve ConnectorDefinition from admin service (cached)</li>
 *   <li>Extract webhook config (hmac, replay, events) from definition JSON</li>
 *   <li>Verify HMAC signature</li>
 *   <li>Check replay window (idempotency key deduplication)</li>
 *   <li>Correlate to a Flowable process (catch event or message start)</li>
 *   <li>On correlation failure: persist to {@code webhook_undelivered}</li>
 * </ol>
 *
 * <p>This endpoint is intentionally unauthenticated (external systems call it);
 * security is provided by HMAC verification of the payload signature.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final AdminServiceClient adminServiceClient;
    private final HmacVerifier hmacVerifier;
    private final ReplayProtectionService replayProtection;
    private final WebhookCorrelator correlator;
    private final WebhookUndeliveredRepository deadLetterRepo;
    private final ObjectMapper objectMapper;

    @PostMapping("/{tenantCode}/{connectorKey}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String tenantCode,
            @PathVariable String connectorKey,
            @RequestBody byte[] rawBody,
            HttpServletRequest request) {

        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        Map<String, String> headers = extractHeaders(request);

        // ── 1. Resolve connector definition ────────────────────────────────
        String definitionJson;
        try {
            definitionJson = adminServiceClient.getConnectorDefinitionJson(tenantCode, connectorKey);
        } catch (Exception e) {
            log.error("WebhookController: failed to resolve connector '{}' for tenant '{}': {}",
                    connectorKey, tenantCode, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("error", "Connector not found: " + connectorKey));
        }

        JsonNode definition;
        JsonNode webhookCfg;
        try {
            definition = objectMapper.readTree(definitionJson);
            webhookCfg = definition.path("transport").path("webhook");
        } catch (Exception e) {
            log.error("WebhookController: invalid definition JSON for connector '{}': {}",
                    connectorKey, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Invalid connector definition"));
        }

        // ── 2. HMAC verification ────────────────────────────────────────────
        String hmacStrategy = webhookCfg.path("hmac").path("strategy").asText("none");
        String hmacHeader   = webhookCfg.path("hmac").path("headerName").asText("X-Werkflow-Signature");
        String secret       = resolveSecret(webhookCfg.path("hmac").path("secretRef").asText(null));

        if (!hmacVerifier.verify(hmacStrategy, hmacHeader, secret, rawBody, headers)) {
            log.warn("WebhookController: HMAC verification failed for connector='{}' tenant='{}'",
                    connectorKey, tenantCode);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
        }

        // ── 3. Replay protection ────────────────────────────────────────────
        if (replayProtection.isDuplicate(tenantCode, connectorKey, idempotencyKey)) {
            log.info("WebhookController: duplicate delivery suppressed [connector={}, key={}]",
                    connectorKey, idempotencyKey);
            return ResponseEntity.ok(Map.of("status", "duplicate_suppressed"));
        }

        // ── 4. Parse payload ────────────────────────────────────────────────
        String rawBodyStr = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBodyStr);
        } catch (Exception e) {
            return deadLetter(tenantCode, connectorKey, idempotencyKey, rawBodyStr,
                    headers, "Payload is not valid JSON: " + e.getMessage());
        }

        // ── 5. Match event config & correlate ──────────────────────────────
        JsonNode events = webhookCfg.path("events");
        if (!events.isArray() || events.isEmpty()) {
            return deadLetter(tenantCode, connectorKey, idempotencyKey, rawBodyStr,
                    headers, "Connector has no webhook events configured");
        }

        // Try each event definition — first match wins
        for (JsonNode event : events) {
            String messageName         = event.path("messageName").asText(null);
            String correlationVariable = event.path("correlationVariable").asText(null);

            if (messageName == null || correlationVariable == null) continue;

            Object correlationValue = extractJsonValue(payload, correlationVariable);
            if (correlationValue == null) continue;

            Map<String, Object> variables = flattenPayload(payload);
            try {
                correlator.correlate(tenantCode, messageName, correlationVariable,
                        correlationValue, variables);
                return ResponseEntity.ok(Map.of(
                        "status", "correlated",
                        "message", messageName,
                        "correlationVariable", correlationVariable,
                        "correlationValue", correlationValue.toString()));
            } catch (WebhookCorrelationException e) {
                log.debug("WebhookController: correlation attempt failed for event '{}': {}",
                        messageName, e.getMessage());
                // Try next event definition
            }
        }

        return deadLetter(tenantCode, connectorKey, idempotencyKey, rawBodyStr, headers,
                "No process instance matched any configured event in connector: " + connectorKey);
    }

    // ── Dead letter ──────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> deadLetter(
            String tenantCode, String connectorKey, String idempotencyKey,
            String rawBody, Map<String, String> headers, String reason) {
        try {
            String headersJson = objectMapper.writeValueAsString(headers);
            deadLetterRepo.save(WebhookUndelivered.of(
                    tenantCode, connectorKey, idempotencyKey, rawBody, headersJson, reason));
        } catch (Exception e) {
            log.error("WebhookController: failed to persist dead-letter payload: {}", e.getMessage());
        }
        log.warn("WebhookController: undelivered webhook [tenant={}, connector={}]: {}",
                tenantCode, connectorKey, reason);
        // Return 200 so the sender stops retrying for uncorrelated events
        return ResponseEntity.ok(Map.of("status", "undelivered", "reason", reason));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> map.put(name.toLowerCase(), request.getHeader(name)));
        return map;
    }

    private String resolveSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return null;
        // Support "env:VAR_NAME" pattern
        if (secretRef.startsWith("env:")) {
            return System.getenv(secretRef.substring(4));
        }
        return secretRef;
    }

    private Object extractJsonValue(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) return null;
        if (field.isNumber()) return field.numberValue();
        return field.asText();
    }

    private Map<String, Object> flattenPayload(JsonNode node) {
        Map<String, Object> vars = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode v = entry.getValue();
            if (v.isNumber())      vars.put(entry.getKey(), v.numberValue());
            else if (v.isBoolean())vars.put(entry.getKey(), v.booleanValue());
            else if (!v.isNull())  vars.put(entry.getKey(), v.asText());
        });
        return vars;
    }
}
