package com.werkflow.engine.webhook.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "webhook_undelivered")
@Getter @Setter @NoArgsConstructor
public class WebhookUndelivered {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;

    @Column(name = "connector_key", nullable = false)
    private String connectorKey;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "raw_body", nullable = false, columnDefinition = "TEXT")
    private String rawBody;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "failure_reason", nullable = false)
    private String failureReason;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "replayed_at")
    private OffsetDateTime replayedAt;

    @Column(name = "replayed_by")
    private String replayedBy;

    public static WebhookUndelivered of(String tenantCode, String connectorKey,
                                        String idempotencyKey, String rawBody,
                                        String headersJson, String failureReason) {
        WebhookUndelivered u = new WebhookUndelivered();
        u.tenantCode = tenantCode;
        u.connectorKey = connectorKey;
        u.idempotencyKey = idempotencyKey;
        u.rawBody = rawBody;
        u.headersJson = headersJson;
        u.failureReason = failureReason;
        return u;
    }
}
