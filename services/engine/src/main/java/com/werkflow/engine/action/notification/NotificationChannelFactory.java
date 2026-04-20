package com.werkflow.engine.action.notification;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationChannelFactory {

    private final Map<String, NotificationChannel> channels;

    public NotificationChannelFactory(List<NotificationChannel> channels) {
        this.channels = channels.stream()
            .collect(Collectors.toMap(NotificationChannel::getChannelName, Function.identity()));
    }

    public NotificationChannel getChannel(String name) {
        NotificationChannel ch = channels.get(name);
        if (ch == null) throw new IllegalArgumentException("Unknown notification channel: " + name);
        return ch;
    }
}
