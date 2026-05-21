package com.werkflow.engine.action.notification;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * WhatsApp service adapter stub (ADR-009, ADR-019).
 * Full implementation deferred post-demo; credential type: whatsapp-cloud-api (ADR-020).
 */
@Component
public class WhatsAppNotificationChannel implements ServiceAdapter {

    @Override
    public String name() {
        return "whatsapp";
    }

    @Override
    public Set<String> supportedOperations() {
        return Set.of("SEND_NOTIFICATION");
    }

    @Override
    public String credentialTypeName() {
        return "whatsapp-cloud-api";
    }

    @Override
    public void send(ActionBlockNotificationRequest request) {
        throw new UnsupportedOperationException(
            "WhatsApp notifications are not yet implemented. Channel: whatsapp, recipient: " + request.recipient());
    }
}
