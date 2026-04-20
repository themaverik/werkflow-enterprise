package com.werkflow.admin.dto;

import lombok.Data;

@Data
public class DepartmentRequest {
    private String name;
    private String code;
    private String description;
    private String tenantCode;
    private Long organizationId;
}
