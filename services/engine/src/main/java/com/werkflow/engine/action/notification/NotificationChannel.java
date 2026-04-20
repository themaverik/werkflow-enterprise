package com.werkflow.engine.action.notification;

public interface NotificationChannel {
    String getChannelName();
    void send(ActionBlockNotificationRequest request);
}
