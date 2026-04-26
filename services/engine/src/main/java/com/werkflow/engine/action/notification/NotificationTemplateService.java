package com.werkflow.engine.action.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private static final PolicyFactory HTML_POLICY =
        Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    private final NotificationTemplateRepository repository;

    public record RenderedTemplate(String subject, String body) {}

    public RenderedTemplate render(String templateKey, Map<String, Object> variables, boolean isHtml) {
        NotificationTemplate template = repository.findByTemplateKeyAndDeletedAtIsNull(templateKey)
            .orElseThrow(() -> new IllegalArgumentException(
                "Notification template not found or deleted: " + templateKey));

        String subject = substitute(template.getSubject() != null ? template.getSubject() : "", variables);
        String body    = substitute(template.getBody(), variables);

        if (isHtml) {
            body = HTML_POLICY.sanitize(body);
        }

        return new RenderedTemplate(subject, body);
    }

    private static final java.util.regex.Pattern DOUBLE_BRACE =
        java.util.regex.Pattern.compile("\\{\\{[^}]+\\}\\}");

    private String substitute(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            // Support both ${key} (legacy plain-text templates) and {{key}} (Unlayer HTML templates)
            result = result.replace("${" + entry.getKey() + "}", value);
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        // Remove any unreplaced {{var}} placeholders so they don't appear literally in emails.
        // Variables missing from the process context (e.g. decision on auto-approved paths)
        // will render as empty string rather than the raw tag.
        result = DOUBLE_BRACE.matcher(result).replaceAll("");
        return result;
    }
}
