package com.werkflow.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_api_credentials")
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TenantApiCredential {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantCode;

    @Column(nullable = false, length = 100)
    private String credentialKey;

    @Column(nullable = false, length = 100)
    private String connectorKey;

    @Column(length = 200)
    private String label;

    @Column(nullable = false, length = 30)
    private String authScheme;

    /** References SecretsResolver key — never the raw secret value */
    @Column(nullable = false, length = 200)
    private String secretRef;

    @Column(length = 100)
    private String headerName;

    @Column(length = 20)
    private String keyPrefix;

    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;
}
