package com.werkflow.engine.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.eventsubscription.api.EventSubscription;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Correlates an inbound webhook payload to a Flowable process instance.
 *
 * <p>Strategy (in order):
 * <ol>
 *   <li>Find active message event subscriptions for the given message name + tenant,
 *       then filter to those whose process instance has the matching correlation variable.
 *       Deliver via {@code RuntimeService.messageEventReceived()}.</li>
 *   <li>If no running instance matched, start a new process via a Message Start Event
 *       with the same message name.</li>
 *   <li>If both fail, throw {@link WebhookCorrelationException} so the caller can persist
 *       the payload to the dead-letter store.</li>
 * </ol>
 *
 * <p>Note: {@code MessageCorrelationBuilder} is a Camunda API and does not exist in
 * Flowable 7.x. The correct Flowable pattern uses {@code createEventSubscriptionQuery()}
 * combined with {@code messageEventReceived()}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookCorrelator {

    private final RuntimeService runtimeService;

    /**
     * @param tenantCode          the Flowable tenant ID
     * @param messageName         BPMN message name (e.g. "VendorStatusChanged")
     * @param correlationVariable process variable name used to match a running instance
     * @param correlationValue    the value to match (extracted from the webhook payload)
     * @param payloadVariables    all payload fields to set as process variables on correlation
     */
    public void correlate(String tenantCode, String messageName,
                          String correlationVariable, Object correlationValue,
                          Map<String, Object> payloadVariables) {
        // Attempt 1 — correlate to a running Intermediate Message Catch Event
        try {
            List<EventSubscription> subscriptions = runtimeService.createEventSubscriptionQuery()
                    .eventType("message")
                    .eventName(messageName)
                    .tenantId(tenantCode)
                    .list();

            List<EventSubscription> matched = subscriptions.stream()
                    .filter(sub -> runtimeService.createProcessInstanceQuery()
                            .processInstanceId(sub.getProcessInstanceId())
                            .variableValueEquals(correlationVariable, correlationValue)
                            .count() > 0)
                    .toList();

            if (!matched.isEmpty()) {
                for (EventSubscription sub : matched) {
                    runtimeService.messageEventReceived(messageName, sub.getExecutionId(), payloadVariables);
                }
                log.info("WebhookCorrelator: correlated message='{}' to {} running instance(s) " +
                         "[tenant={}, {}={}]",
                        messageName, matched.size(), tenantCode, correlationVariable, correlationValue);
                return;
            }
        } catch (Exception e) {
            log.debug("WebhookCorrelator: no running catch event for message='{}' [tenant={}, {}={}]: {}",
                    messageName, tenantCode, correlationVariable, correlationValue, e.getMessage());
        }

        // Attempt 2 — start a new process via Message Start Event
        try {
            runtimeService.startProcessInstanceByMessageAndTenantId(messageName, payloadVariables, tenantCode);
            log.info("WebhookCorrelator: started new process via message='{}' [tenant={}, {}={}]",
                    messageName, tenantCode, correlationVariable, correlationValue);
            return;
        } catch (Exception e) {
            log.debug("WebhookCorrelator: no Message Start Event for message='{}' [tenant={}]: {}",
                    messageName, tenantCode, e.getMessage());
        }

        throw new WebhookCorrelationException(
                "No running catch event or Message Start Event found for message='" + messageName +
                "' with " + correlationVariable + "=" + correlationValue +
                " in tenant=" + tenantCode);
    }

    public static class WebhookCorrelationException extends RuntimeException {
        public WebhookCorrelationException(String message) {
            super(message);
        }
    }
}
