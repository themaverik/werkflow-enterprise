package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.Environment;
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
public class ServiceEnvironmentUrlResponse {
    private UUID id;
    private UUID serviceId;
    private Environment environment;
    private String baseUrl;
    private Integer priority;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
