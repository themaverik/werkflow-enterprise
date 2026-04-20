package com.werkflow.admin.entity.serviceregistry;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing environment-specific URLs for a service
 */
@Entity
@Table(
    name = "service_environment_urls",
    schema = "admin_service",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_service_environment",
            columnNames = {"service_id", "environment", "priority"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceEnvironmentUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceRegistry service;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 50)
    private Environment environment;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceEnvironmentUrl)) return false;
        ServiceEnvironmentUrl that = (ServiceEnvironmentUrl) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
