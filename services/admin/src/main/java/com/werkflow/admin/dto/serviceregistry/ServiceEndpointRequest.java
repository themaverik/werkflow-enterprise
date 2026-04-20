package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.HttpMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEndpointRequest {

    @NotBlank(message = "Endpoint path is required")
    @Size(max = 500, message = "Endpoint path must not exceed 500 characters")
    @Pattern(regexp = "^/.*", message = "Endpoint path must start with /")
    private String endpointPath;

    @NotNull(message = "HTTP method is required")
    private HttpMethod httpMethod;

    private String description;

    @NotNull(message = "Requires auth flag is required")
    private Boolean requiresAuth;

    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 300, message = "Timeout must not exceed 300 seconds")
    private Integer timeoutSeconds;

    @Min(value = 0, message = "Retry count must be non-negative")
    @Max(value = 5, message = "Retry count must not exceed 5")
    private Integer retryCount;

    private Boolean active;
}
