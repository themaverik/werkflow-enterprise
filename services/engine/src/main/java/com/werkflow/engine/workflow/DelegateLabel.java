package com.werkflow.engine.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-static utility that converts a Spring bean name (camelCase JavaDelegate name) into a
 * human-readable label suitable for display in tooltips or process capability summaries.
 *
 * <p>Rule (simple, no acronym dictionary):
 * <ol>
 *   <li>Split on camelCase boundaries — each transition from a lowercase letter to an
 *       uppercase letter starts a new word.</li>
 *   <li>Strip a trailing "Delegate" word (case-insensitive).</li>
 *   <li>Title-case each remaining word (first letter uppercase, rest lowercase).</li>
 *   <li>Join with a single space.</li>
 * </ol>
 *
 * <p>Expected outputs:
 * <ul>
 *   <li>{@code externalApiCallDelegate} → {@code External Api Call}</li>
 *   <li>{@code databaseConnectorDelegate} → {@code Database Connector}</li>
 *   <li>{@code connectorWebhookDelegate} → {@code Connector Webhook}</li>
 *   <li>{@code restConnectorDelegate} → {@code Rest Connector}</li>
 *   <li>{@code notificationDelegate} → {@code Notification}</li>
 * </ul>
 *
 * <p>"Api" is intentionally not uppercased to "API" — the rule is kept simple and consistent
 * rather than maintaining an acronym dictionary. Callers that need display-layer adjustments
 * can post-process the result.
 *
 * <p>This class has no Spring dependencies and is not a bean — it is a static utility only.
 */
public final class DelegateLabel {

    /** Matches the boundary between a lowercase letter and an uppercase letter. */
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])");

    private DelegateLabel() {
        // utility class — not instantiable
    }

    /**
     * Converts a camelCase Spring bean name to a human-readable label.
     *
     * @param beanName the bean name (e.g. {@code externalApiCallDelegate}); may be {@code null}
     * @return human-readable label, or an empty string if {@code beanName} is blank/null
     */
    public static String toHuman(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            return "";
        }

        // Split on camelCase boundaries
        String[] rawParts = CAMEL_BOUNDARY.split(beanName);

        // Strip trailing "Delegate" word (case-insensitive)
        List<String> parts = new ArrayList<>(rawParts.length);
        for (int i = 0; i < rawParts.length; i++) {
            boolean isLast = (i == rawParts.length - 1);
            if (isLast && rawParts[i].equalsIgnoreCase("Delegate")) {
                continue;
            }
            parts.add(rawParts[i]);
        }

        if (parts.isEmpty()) {
            return "";
        }

        // Title-case each word and join
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String word = parts.get(i);
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
