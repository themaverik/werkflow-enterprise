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
@Table(name = "tenant_connector_paths")
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TenantConnectorPath {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String connectorKey;

    @Column(nullable = false, length = 50)
    private String tenantCode;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false, length = 10)
    private String httpMethod;

    @Column(nullable = false, length = 20)
    private String interactionType;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String requestSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String responseSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String variableMappings;

    @CreatedDate @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate @Column(nullable = false)
    private LocalDateTime updatedAt;
}
