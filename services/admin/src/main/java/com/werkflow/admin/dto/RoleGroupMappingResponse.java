package com.werkflow.admin.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for role-to-group mappings.
 *
 * <p>tier values:
 * <ul>
 *   <li>1 — Keycloak realm-level roles (read-only, sourced from IdP)</li>
 *   <li>2 — Tenant custom group mappings stored in DB</li>
 * </ul>
 */
public record RoleGroupMappingResponse(
    Long id,
    String tenantCode,
    String roleName,
    String groupName,
    Integer tier,
    LocalDateTime createdAt,
    Boolean isManagerTier
) {}
