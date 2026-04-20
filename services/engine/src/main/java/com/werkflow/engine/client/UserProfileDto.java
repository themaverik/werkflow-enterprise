package com.werkflow.engine.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class UserProfileDto {
    private String keycloakId;
    private String tenantCode;
    private Integer doaLevel;       // null = no DoA authority
    private String departmentCode;  // null = no department
}
