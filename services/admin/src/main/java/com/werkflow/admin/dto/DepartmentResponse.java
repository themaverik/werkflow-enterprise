package com.werkflow.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class DepartmentResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String tenantCode;
    private Long organizationId;
    private boolean active;
}
