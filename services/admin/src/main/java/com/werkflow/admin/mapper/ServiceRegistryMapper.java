package com.werkflow.admin.mapper;

import com.werkflow.admin.dto.serviceregistry.*;
import com.werkflow.admin.entity.User;
import com.werkflow.admin.entity.serviceregistry.*;
import com.werkflow.admin.exception.ServiceRegistryException;
import com.werkflow.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for converting between ServiceRegistry entities and DTOs
 */
@Component
@RequiredArgsConstructor
public class ServiceRegistryMapper {

    private final UserRepository userRepository;

    /**
     * Convert ServiceRegistryRequest to ServiceRegistry entity
     * @param request The request DTO
     * @return ServiceRegistry entity
     */
    public ServiceRegistry toEntity(ServiceRegistryRequest request) {
        ServiceRegistry.ServiceRegistryBuilder builder = ServiceRegistry.builder()
                .serviceName(request.getServiceName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .serviceType(request.getServiceType())
                .basePath(request.getBasePath())
                .version(request.getVersion())
                .healthCheckUrl(request.getHealthCheckUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .tags(request.getTags() != null ? request.getTags() : Collections.emptySet());

        // Set owner if provided
        if (request.getOwnerUserId() != null) {
            Long userId = Long.parseLong(request.getOwnerUserId().toString().replace("-", "").substring(0, 18));
            User owner = userRepository.findById(userId)
                    .orElseThrow(() -> new ServiceRegistryException("User not found with ID: " + request.getOwnerUserId()));
            builder.owner(owner);
        }

        return builder.build();
    }

    /**
     * Convert ServiceRegistry entity to ServiceRegistryResponse DTO
     * @param entity The entity
     * @return ServiceRegistryResponse DTO
     */
    public ServiceRegistryResponse toResponse(ServiceRegistry entity) {
        return ServiceRegistryResponse.builder()
                .id(entity.getId())
                .serviceName(entity.getServiceName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .serviceType(entity.getServiceType())
                .basePath(entity.getBasePath())
                .version(entity.getVersion())
                .healthCheckUrl(entity.getHealthCheckUrl())
                .healthStatus(entity.getHealthStatus())
                .lastHealthCheckAt(entity.getLastHealthCheckAt())
                .active(entity.getActive())
                .ownerUserId(entity.getOwner() != null ?
                        UUID.nameUUIDFromBytes(entity.getOwner().getId().toString().getBytes()) : null)
                .ownerUsername(entity.getOwner() != null ? entity.getOwner().getUsername() : null)
                .tags(entity.getTags() != null ? entity.getTags() : Collections.emptySet())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Convert list of ServiceRegistry entities to list of ServiceRegistryResponse DTOs
     * @param entities List of entities
     * @return List of ServiceRegistryResponse DTOs
     */
    public List<ServiceRegistryResponse> toResponseList(List<ServiceRegistry> entities) {
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update entity with request data
     * @param entity Existing entity
     * @param request Request with updated data
     */
    public void updateEntityFromRequest(ServiceRegistry entity, ServiceRegistryRequest request) {
        entity.setServiceName(request.getServiceName());
        entity.setDisplayName(request.getDisplayName());
        entity.setDescription(request.getDescription());
        entity.setServiceType(request.getServiceType());
        entity.setBasePath(request.getBasePath());
        entity.setVersion(request.getVersion());
        entity.setHealthCheckUrl(request.getHealthCheckUrl());

        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }

        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }

        // Update owner if provided
        if (request.getOwnerUserId() != null) {
            Long userId = Long.parseLong(request.getOwnerUserId().toString().replace("-", "").substring(0, 18));
            User owner = userRepository.findById(userId)
                    .orElseThrow(() -> new ServiceRegistryException("User not found with ID: " + request.getOwnerUserId()));
            entity.setOwner(owner);
        } else {
            entity.setOwner(null);
        }
    }

    /**
     * Convert ServiceEndpointRequest to ServiceEndpoint entity
     * @param request The request DTO
     * @param service The parent service
     * @return ServiceEndpoint entity
     */
    public ServiceEndpoint toEndpointEntity(ServiceEndpointRequest request, ServiceRegistry service) {
        return ServiceEndpoint.builder()
                .service(service)
                .endpointPath(request.getEndpointPath())
                .httpMethod(request.getHttpMethod())
                .description(request.getDescription())
                .requiresAuth(request.getRequiresAuth() != null ? request.getRequiresAuth() : true)
                .timeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30)
                .retryCount(request.getRetryCount() != null ? request.getRetryCount() : 0)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
    }

    /**
     * Convert ServiceEndpoint entity to ServiceEndpointResponse DTO
     * @param entity The entity
     * @return ServiceEndpointResponse DTO
     */
    public ServiceEndpointResponse toEndpointResponse(ServiceEndpoint entity) {
        return ServiceEndpointResponse.builder()
                .id(entity.getId())
                .serviceId(entity.getService().getId())
                .endpointPath(entity.getEndpointPath())
                .httpMethod(entity.getHttpMethod())
                .description(entity.getDescription())
                .requiresAuth(entity.getRequiresAuth())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .retryCount(entity.getRetryCount())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Convert list of ServiceEndpoint entities to list of ServiceEndpointResponse DTOs
     * @param entities List of entities
     * @return List of ServiceEndpointResponse DTOs
     */
    public List<ServiceEndpointResponse> toEndpointResponseList(List<ServiceEndpoint> entities) {
        return entities.stream()
                .map(this::toEndpointResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update endpoint entity with request data
     * @param entity Existing entity
     * @param request Request with updated data
     */
    public void updateEndpointFromRequest(ServiceEndpoint entity, ServiceEndpointRequest request) {
        entity.setEndpointPath(request.getEndpointPath());
        entity.setHttpMethod(request.getHttpMethod());
        entity.setDescription(request.getDescription());

        if (request.getRequiresAuth() != null) {
            entity.setRequiresAuth(request.getRequiresAuth());
        }

        if (request.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(request.getTimeoutSeconds());
        }

        if (request.getRetryCount() != null) {
            entity.setRetryCount(request.getRetryCount());
        }

        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    /**
     * Convert ServiceEnvironmentUrlRequest to ServiceEnvironmentUrl entity
     * @param request The request DTO
     * @param service The parent service
     * @return ServiceEnvironmentUrl entity
     */
    public ServiceEnvironmentUrl toEnvironmentUrlEntity(ServiceEnvironmentUrlRequest request, ServiceRegistry service) {
        return ServiceEnvironmentUrl.builder()
                .service(service)
                .environment(request.getEnvironment())
                .baseUrl(request.getBaseUrl())
                .priority(request.getPriority() != null ? request.getPriority() : 1)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
    }

    /**
     * Convert ServiceEnvironmentUrl entity to ServiceEnvironmentUrlResponse DTO
     * @param entity The entity
     * @return ServiceEnvironmentUrlResponse DTO
     */
    public ServiceEnvironmentUrlResponse toEnvironmentUrlResponse(ServiceEnvironmentUrl entity) {
        return ServiceEnvironmentUrlResponse.builder()
                .id(entity.getId())
                .serviceId(entity.getService().getId())
                .environment(entity.getEnvironment())
                .baseUrl(entity.getBaseUrl())
                .priority(entity.getPriority())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Convert list of ServiceEnvironmentUrl entities to list of ServiceEnvironmentUrlResponse DTOs
     * @param entities List of entities
     * @return List of ServiceEnvironmentUrlResponse DTOs
     */
    public List<ServiceEnvironmentUrlResponse> toEnvironmentUrlResponseList(List<ServiceEnvironmentUrl> entities) {
        return entities.stream()
                .map(this::toEnvironmentUrlResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update environment URL entity with request data
     * @param entity Existing entity
     * @param request Request with updated data
     */
    public void updateEnvironmentUrlFromRequest(ServiceEnvironmentUrl entity, ServiceEnvironmentUrlRequest request) {
        entity.setEnvironment(request.getEnvironment());
        entity.setBaseUrl(request.getBaseUrl());

        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }

        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    /**
     * Convert ServiceHealthCheck entity to HealthCheckResultResponse DTO
     * @param entity The entity
     * @return HealthCheckResultResponse DTO
     */
    public HealthCheckResultResponse toHealthCheckResponse(ServiceHealthCheck entity) {
        return HealthCheckResultResponse.builder()
                .id(entity.getId())
                .serviceId(entity.getService().getId())
                .serviceName(entity.getService().getServiceName())
                .environment(entity.getEnvironment())
                .status(entity.getStatus())
                .responseTimeMs(entity.getResponseTimeMs())
                .errorMessage(entity.getErrorMessage())
                .checkedAt(entity.getCheckedAt())
                .build();
    }

    /**
     * Convert list of ServiceHealthCheck entities to list of HealthCheckResultResponse DTOs
     * @param entities List of entities
     * @return List of HealthCheckResultResponse DTOs
     */
    public List<HealthCheckResultResponse> toHealthCheckResponseList(List<ServiceHealthCheck> entities) {
        return entities.stream()
                .map(this::toHealthCheckResponse)
                .collect(Collectors.toList());
    }
}
