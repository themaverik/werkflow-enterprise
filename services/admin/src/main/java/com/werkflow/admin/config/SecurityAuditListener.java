package com.werkflow.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SecurityAuditListener {

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("SECURITY_AUTH_FAILURE type={} principal={}",
                event.getException().getClass().getSimpleName(),
                event.getAuthentication().getName());
    }
}
