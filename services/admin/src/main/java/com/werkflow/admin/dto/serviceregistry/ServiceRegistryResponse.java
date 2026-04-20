package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.HealthStatus;
import com.werkflow.admin.entity.serviceregistry.ServiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRegistryResponse {
    private UUID id;
    private String serviceName;
    private String displayName;
    private String description;
    private ServiceType serviceType;
    private String basePath;
    private String version;
    private UUID ownerUserId;
    private String ownerUsername;
    private String healthCheckUrl;
    private OffsetDateTime lastHealthCheckAt;
    private HealthStatus healthStatus;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Set<String> tags;
}
