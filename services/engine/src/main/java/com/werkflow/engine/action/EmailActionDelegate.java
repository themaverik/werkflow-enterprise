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

@Slf4j
@Component("emailActionDelegate")
@RequiredArgsConstructor
public class EmailActionDelegate implements JavaDelegate {

    private final NotificationChannelFactory channelFactory;
    private final NotificationTemplateService templateService;

    // Injected via <flowable:field> — must be instance fields (not constructor-injected)
    @Setter private Expression recipient;
    @Setter private Expression templateKey;
    @Setter private Expression channel;
    @Setter private Expression condition;

    @Override
    public void execute(DelegateExecution execution) {
        // Evaluate optional condition
        if (condition != null) {
            Object condValue = condition.getValue(execution);
            if (Boolean.FALSE.equals(condValue)) {
                log.info("emailActionDelegate: condition evaluated to false — skipping");
                return;
            }
        }

        String recipientValue   = sanitizeEmail(getString(recipient, execution, "recipient"));
        String templateKeyValue = getString(templateKey, execution, "templateKey");
        String channelValue     = getString(channel, execution, "channel");

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
        log.info("emailActionDelegate: notification dispatched to channel={}", channelValue);
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
