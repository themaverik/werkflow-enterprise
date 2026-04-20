package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.Environment;
import com.werkflow.admin.entity.serviceregistry.HealthStatus;
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
public class HealthCheckResultResponse {
    private UUID id;
    private UUID serviceId;
    private String serviceName;
    private Environment environment;
    private OffsetDateTime checkedAt;
    private HealthStatus status;
    private Integer responseTimeMs;
    private String errorMessage;
}
