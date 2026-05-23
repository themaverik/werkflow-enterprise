package com.werkflow.admin.dto.connector;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConnectorResponse {
    private Long endpointId;
    private String tenantCode;
    private String connectorKey;
    private String displayName;
    private String baseUrl;
    private String environment;
    private boolean active;
    private String connectorType;
    private String authScheme;
    private String credentialRef;
    private String sampleSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
