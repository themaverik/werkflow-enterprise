package com.werkflow.engine.action.notification;

import org.springframework.stereotype.Component;

/**
 * WhatsApp notification channel stub (ADR-009).
 * Full implementation deferred post-demo.
 */
@Component
public class WhatsAppNotificationChannel implements NotificationChannel {

    @Override
    public String getChannelName() {
        return "whatsapp";
    }

    @Override
    public void send(ActionBlockNotificationRequest request) {
        throw new UnsupportedOperationException(
            "WhatsApp notifications are not yet implemented. Channel: whatsapp, recipient: " + request.recipient());
    }
}
