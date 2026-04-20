package com.werkflow.engine.action.notification;

public record ActionBlockNotificationRequest(
    String recipient,
    String subject,
    String body,
    String templateKey,
    String channel
) {}
