package com.werkflow.engine.action;

import com.werkflow.engine.action.notification.*;
import com.werkflow.engine.security.el.RestrictedExpressionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.HasExpressionManagerEngineConfiguration;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.DelegateHelper;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SEND_NOTIFICATION action block — dispatches a notification via the configured channel.
 *
 * <p>Reads the following {@code <flowable:field>} entries from the BPMN element at execution
 * time via {@link DelegateHelper#getFlowElementFields(DelegateExecution)}, eliminating the
 * {@code @Setter} field-injection race documented in ADR-014 and ADR-015:
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
 *   <li>{@code private final} service fields and {@code expressionManager}, all constructor-injected</li>
 *   <li>No {@code @Setter} Expression fields — fields read per-execution via {@link DelegateHelper}
 *       (ADR-014 pattern); no mutable instance state; safe for concurrent process instances</li>
 *   <li>Throws {@code IllegalArgumentException} on invalid email (not swallowed)</li>
 *   <li>Throws {@code IllegalStateException} on missing required field (not swallowed)</li>
 *   <li>Logs executionId, processInstanceId, tenantId at INFO; no PII in INFO logs</li>
 * </ul>
 *
 * @see <a href="../../../../../../../../../../docs/adr/ADR-014-unified-connector-operation-action-block.md">ADR-014</a>
 * @see <a href="../../../../../../../../../../docs/adr/ADR-015-werkflow-custom-sendtask-parse-handler.md">ADR-015</a>
 */
@Slf4j
@Component("notificationDelegate")
public class NotificationDelegate implements JavaDelegate {

    private final NotificationChannelFactory channelFactory;
    private final NotificationTemplateService templateService;
    private final ExpressionManager expressionManager;

    public NotificationDelegate(NotificationChannelFactory channelFactory,
                                NotificationTemplateService templateService,
                                ProcessEngineConfiguration cfg) {
        this.channelFactory = channelFactory;
        this.templateService = templateService;
        this.expressionManager = ((HasExpressionManagerEngineConfiguration) cfg).getExpressionManager();
    }

    @Override
    public void execute(DelegateExecution execution) {
        log.info("notificationDelegate: executing for processInstance={} execution={} tenant={}",
                execution.getProcessInstanceId(), execution.getId(), execution.getTenantId());

        List<FieldExtension> fields = DelegateHelper.getFlowElementFields(execution);

        // Evaluate optional condition
        Expression conditionExpr = resolveField(fields, "condition");
        if (conditionExpr != null) {
            Object condValue = conditionExpr.getValue(execution);
            if (Boolean.FALSE.equals(condValue)) {
                log.info("notificationDelegate: condition evaluated to false — skipping");
                return;
            }
        }

        String recipientValue   = sanitizeEmail(getString(resolveRequiredField(fields, "recipient"), execution, "recipient"));
        String templateKeyValue = getString(resolveRequiredField(fields, "templateKey"), execution, "templateKey");
        Expression channelExpr  = resolveField(fields, "channel");
        String channelValue     = channelExpr != null ? getString(channelExpr, execution, "channel") : "email";

        java.util.Map<String, Object> variables = execution.getVariables();

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

    /**
     * Looks up a named field from the list and returns its resolved {@link Expression},
     * or {@code null} if the field is not present. Optional fields use this method.
     */
    private Expression resolveField(List<FieldExtension> fields, String fieldName) {
        for (FieldExtension field : fields) {
            if (fieldName.equals(field.getFieldName())) {
                return toExpression(field);
            }
        }
        return null;
    }

    /**
     * Looks up a named field and returns its resolved {@link Expression}.
     * Throws {@link IllegalStateException} if the field is absent. Required fields use this method.
     */
    private Expression resolveRequiredField(List<FieldExtension> fields, String fieldName) {
        Expression expr = resolveField(fields, fieldName);
        if (expr == null) {
            throw new IllegalStateException("Required field not set: " + fieldName);
        }
        return expr;
    }

    /**
     * Converts a {@link FieldExtension} to an {@link Expression}. If the field has an EL
     * expression string, creates it via the engine {@link ExpressionManager} — routing through
     * {@link RestrictedExpressionManager} when present (ADR-013 cross-reference). Otherwise
     * wraps the string value in a fixed expression.
     */
    private Expression toExpression(FieldExtension field) {
        if (field.getExpression() != null && !field.getExpression().isBlank()) {
            return expressionManager.createExpression(field.getExpression());
        }
        String literal = field.getStringValue() != null ? field.getStringValue() : "";
        return expressionManager.createExpression(literal);
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
