package com.werkflow.engine.webhook.repository;

import com.werkflow.engine.webhook.entity.WebhookUndelivered;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookUndeliveredRepository extends JpaRepository<WebhookUndelivered, Long> {

    Page<WebhookUndelivered> findByTenantCodeAndReplayedAtIsNull(String tenantCode, Pageable pageable);

    Page<WebhookUndelivered> findByTenantCode(String tenantCode, Pageable pageable);
}
