package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Process Definition
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinitionResponse {

    private String id;
    private String key;
    private String name;
    private String description;
    private Integer version;
    private String category;
    private String deploymentId;
    private String resourceName;
    private String tenantId;
    private boolean suspended;
    private boolean hasStartFormKey;
    private boolean hasGraphicalNotation;
    private String startFormKey;
    private boolean hasDmn;
    private boolean hasConnector;
    private String owningDepartment;
}
