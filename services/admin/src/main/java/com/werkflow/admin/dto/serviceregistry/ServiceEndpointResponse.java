package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEndpointResponse {
    private UUID id;
    private UUID serviceId;
    private String endpointPath;
    private HttpMethod httpMethod;
    private String description;
    private Boolean requiresAuth;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
