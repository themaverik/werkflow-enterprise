package com.werkflow.admin.dto;

import com.werkflow.admin.entity.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    private Long id;
    private String tenantCode;
    private String name;
    private boolean active;
    private LocalDateTime createdAt;

    /**
     * Maps a Tenant entity to a TenantResponse DTO.
     */
    public static TenantResponse from(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .name(tenant.getName())
                .active(tenant.isActive())
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}
