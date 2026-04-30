package com.werkflow.engine.action.notification;

import org.springframework.stereotype.Component;

/**
 * Slack notification channel stub (ADR-009).
 * Full implementation deferred post-demo.
 */
@Component
public class SlackNotificationChannel implements NotificationChannel {

    @Override
    public String getChannelName() {
        return "slack";
    }

    @Override
    public void send(ActionBlockNotificationRequest request) {
        throw new UnsupportedOperationException(
            "Slack notifications are not yet implemented. Channel: slack, recipient: " + request.recipient());
    }
}
