package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRegistryRequest {

    @NotBlank(message = "Service name is required")
    @Size(min = 3, max = 100, message = "Service name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Service name must contain only lowercase letters, numbers, and hyphens")
    private String serviceName;

    @NotBlank(message = "Display name is required")
    @Size(max = 200, message = "Display name must not exceed 200 characters")
    private String displayName;

    private String description;

    @NotNull(message = "Service type is required")
    private ServiceType serviceType;

    @NotBlank(message = "Base path is required")
    @Size(max = 255, message = "Base path must not exceed 255 characters")
    @Pattern(regexp = "^/.*", message = "Base path must start with /")
    private String basePath;

    @NotBlank(message = "Version is required")
    @Size(max = 50, message = "Version must not exceed 50 characters")
    private String version;

    private UUID ownerUserId;

    @Size(max = 500, message = "Health check URL must not exceed 500 characters")
    private String healthCheckUrl;

    private Boolean active;

    private Set<String> tags;
}
