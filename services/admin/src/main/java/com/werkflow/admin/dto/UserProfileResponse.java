package com.werkflow.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Internal response for engine-service user profile lookups.
 * Contains only the fields needed for FlowableGroupResolver.
 */
@Data @Builder
public class UserProfileResponse {
    private String keycloakId;
    private String tenantCode;
    private Integer doaLevel;       // null means no DoA authority
    private String departmentCode;  // null means no department assigned
}
