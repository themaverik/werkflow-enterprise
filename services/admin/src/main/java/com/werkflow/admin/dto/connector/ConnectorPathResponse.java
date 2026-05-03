package com.werkflow.admin.dto.connector;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConnectorPathResponse {
    private Long id;
    private String connectorKey;
    private String tenantCode;
    private String path;
    private String httpMethod;
    private String interactionType;
    private String description;
    private String requestSchema;
    private String responseSchema;
    private String variableMappings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
