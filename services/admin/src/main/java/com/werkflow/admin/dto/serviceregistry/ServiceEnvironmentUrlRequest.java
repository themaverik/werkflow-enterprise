package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.Environment;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEnvironmentUrlRequest {

    @NotNull(message = "Environment is required")
    private Environment environment;

    @NotBlank(message = "Base URL is required")
    @Size(max = 500, message = "Base URL must not exceed 500 characters")
    @Pattern(regexp = "^https?://.*", message = "Base URL must be a valid HTTP or HTTPS URL")
    private String baseUrl;

    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 100, message = "Priority must not exceed 100")
    private Integer priority;

    private Boolean active;
}
