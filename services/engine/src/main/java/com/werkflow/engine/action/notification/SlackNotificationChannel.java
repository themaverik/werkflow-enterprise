package com.werkflow.engine.action.notification;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Slack service adapter stub (ADR-009, ADR-019).
 * Full implementation deferred post-demo; credential type: slack-bot-token (ADR-020).
 */
@Component
public class SlackNotificationChannel implements ServiceAdapter {

    @Override
    public String name() {
        return "slack";
    }

    @Override
    public Set<String> supportedOperations() {
        return Set.of("SEND_NOTIFICATION");
    }

    @Override
    public String credentialTypeName() {
        return "slack-bot-token";
    }

    @Override
    public void send(ActionBlockNotificationRequest request) {
        throw new UnsupportedOperationException(
            "Slack notifications are not yet implemented. Channel: slack, recipient: " + request.recipient());
    }
}
