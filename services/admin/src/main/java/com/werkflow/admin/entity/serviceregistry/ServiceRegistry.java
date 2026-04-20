package com.werkflow.admin.entity.serviceregistry;

import com.werkflow.admin.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a registered service in the platform
 */
@Entity
@Table(name = "service_registry", schema = "admin_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_name", unique = true, nullable = false, length = 100)
    private String serviceName;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 50)
    private ServiceType serviceType;

    @Column(name = "base_path", nullable = false, length = 255)
    private String basePath;

    @Column(name = "version", nullable = false, length = 50)
    @Builder.Default
    private String version = "1.0.0";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @Column(name = "health_check_url", length = 500)
    private String healthCheckUrl;

    @Column(name = "last_health_check_at")
    private OffsetDateTime lastHealthCheckAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 50)
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ServiceEndpoint> endpoints = new HashSet<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ServiceEnvironmentUrl> environmentUrls = new HashSet<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ServiceHealthCheck> healthChecks = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "service_tags",
        schema = "admin_service",
        joinColumns = @JoinColumn(name = "service_id")
    )
    @Column(name = "tag", length = 100)
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    /**
     * Helper method to add an endpoint to this service
     */
    public void addEndpoint(ServiceEndpoint endpoint) {
        endpoints.add(endpoint);
        endpoint.setService(this);
    }

    /**
     * Helper method to remove an endpoint from this service
     */
    public void removeEndpoint(ServiceEndpoint endpoint) {
        endpoints.remove(endpoint);
        endpoint.setService(null);
    }

    /**
     * Helper method to add an environment URL to this service
     */
    public void addEnvironmentUrl(ServiceEnvironmentUrl url) {
        environmentUrls.add(url);
        url.setService(this);
    }

    /**
     * Helper method to remove an environment URL from this service
     */
    public void removeEnvironmentUrl(ServiceEnvironmentUrl url) {
        environmentUrls.remove(url);
        url.setService(null);
    }

    /**
     * Helper method to add a health check record to this service
     */
    public void addHealthCheck(ServiceHealthCheck healthCheck) {
        healthChecks.add(healthCheck);
        healthCheck.setService(this);
    }

    /**
     * Helper method to add a tag to this service
     */
    public void addTag(String tag) {
        tags.add(tag);
    }

    /**
     * Helper method to remove a tag from this service
     */
    public void removeTag(String tag) {
        tags.remove(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceRegistry)) return false;
        ServiceRegistry that = (ServiceRegistry) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
