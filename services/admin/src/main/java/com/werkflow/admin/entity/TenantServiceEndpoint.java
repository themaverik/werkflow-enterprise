package com.werkflow.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_service_endpoints")
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TenantServiceEndpoint {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantCode;

    @Column(nullable = false, length = 100)
    private String serviceKey;

    @Column(nullable = false, length = 100)
    private String connectorKey;

    @Column(length = 200)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String baseUrl;

    @Column(nullable = false, length = 50)
    private String environment = "development";

    @Column(nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String sampleSchema;

    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;
}
