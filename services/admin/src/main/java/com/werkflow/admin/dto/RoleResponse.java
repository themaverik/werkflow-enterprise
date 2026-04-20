package com.werkflow.admin.dto;

import com.werkflow.admin.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
    private Role.RoleType type;
    private Long organizationId;
    private String organizationName;
    private List<String> permissions;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
