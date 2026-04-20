package com.werkflow.engine.action.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@werkflow.local}")
    private String mailFrom;

    @Value("${app.mail.fromName:Werkflow Platform}")
    private String fromName;

    @Override
    public String getChannelName() {
        return "email";
    }

    @Override
    @Async("asyncTaskExecutor")
    @Retryable(
        retryFor = {MailException.class, MessagingException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void send(ActionBlockNotificationRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, fromName);
            helper.setTo(request.recipient());
            helper.setSubject(request.subject());
            helper.setText(request.body(), true);
            mailSender.send(message);
            log.info("Action block email sent to {}", request.recipient());
        } catch (Exception e) {
            log.error("Failed to send action block email to {}: {}", request.recipient(), e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
