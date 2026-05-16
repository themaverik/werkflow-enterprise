package com.werkflow.engine.action;

import com.werkflow.engine.action.notification.*;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SEND_NOTIFICATION action block — dispatches a notification via the configured channel.
 *
 * <p>Reads the following {@code <flowable:field>} entries from the BPMN element:
 * <ul>
 *   <li>{@code recipient} — email address or expression (required)</li>
 *   <li>{@code templateKey} — notification template key (required)</li>
 *   <li>{@code channel} — delivery channel: email | slack | whatsapp (optional, default: email)</li>
 *   <li>{@code condition} — optional boolean expression; delegate no-ops if {@code false}</li>
 * </ul>
 *
 * <p>Delegate-Checklist.md compliance:
 * <ul>
 *   <li>Singleton {@code @Component} — no scope override</li>
 *   <li>Implements {@code JavaDelegate} — single {@code execute()} entry point</li>
 *   <li>No {@code @Async} — Flowable controls async via {@code flowable:async} on the element</li>
 *   <li>{@code private final} service fields, constructor-injected via {@code @RequiredArgsConstructor}</li>
 *   <li>{@code @Setter} Expression fields are framework-managed (Flowable per-execution injection)</li>
 *   <li>No mutable instance state — Expressions injected by Flowable, resolved inside execute()</li>
 *   <li>Throws {@code IllegalArgumentException} on invalid email (not swallowed)</li>
 *   <li>Throws {@code IllegalStateException} on missing required field (not swallowed)</li>
 *   <li>Logs executionId, processInstanceId, tenantId at INFO; no PII in INFO logs</li>
 * </ul>
 */
@Slf4j
@Component("notificationDelegate")
@RequiredArgsConstructor
public class NotificationDelegate implements JavaDelegate {

    private final NotificationChannelFactory channelFactory;
    private final NotificationTemplateService templateService;

    // Injected via <flowable:field> — Flowable sets these per-execution; not mutable instance state
    @Setter private Expression recipient;
    @Setter private Expression templateKey;
    @Setter private Expression channel;
    @Setter private Expression condition;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("notificationDelegate: executing for processInstance={} execution={} tenant={}",
                execution.getProcessInstanceId(), execution.getId(), execution.getTenantId());

        // Evaluate optional condition
        if (condition != null) {
            Object condValue = condition.getValue(execution);
            if (Boolean.FALSE.equals(condValue)) {
                log.info("notificationDelegate: condition evaluated to false — skipping");
                return;
            }
        }

        String recipientValue   = sanitizeEmail(getString(recipient, execution, "recipient"));
        String templateKeyValue = getString(templateKey, execution, "templateKey");
        String channelValue     = channel != null ? getString(channel, execution, "channel") : "email";

        Map<String, Object> variables = execution.getVariables();

        NotificationTemplateService.RenderedTemplate rendered =
            templateService.render(templateKeyValue, variables, false);

        ActionBlockNotificationRequest request = new ActionBlockNotificationRequest(
            recipientValue,
            sanitizeSubject(rendered.subject()),
            rendered.body(),
            templateKeyValue,
            channelValue
        );

        channelFactory.getChannel(channelValue).send(request);
        log.info("notificationDelegate: notification dispatched channel={} processInstance={} execution={} tenant={}",
                channelValue, execution.getProcessInstanceId(), execution.getId(), execution.getTenantId());
    }

    private String sanitizeEmail(String raw) {
        String cleaned = raw.replace("\r", "").replace("\n", "").trim();
        if (!EmailValidator.getInstance().isValid(cleaned)) {
            throw new IllegalArgumentException("Invalid recipient email address.");
        }
        return cleaned;
    }

    // Spec section 7.4: strip CR/LF from subject, enforce max length 998
    private String sanitizeSubject(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace("\r", "").replace("\n", "");
        return cleaned.length() > 998 ? cleaned.substring(0, 998) : cleaned;
    }

    private String getString(Expression expr, DelegateExecution execution, String fieldName) {
        if (expr == null) throw new IllegalStateException("Required field not set: " + fieldName);
        Object val = expr.getValue(execution);
        return val != null ? val.toString() : "";
    }
}
