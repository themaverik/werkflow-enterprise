package com.werkflow.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User context extracted from JWT token
 * Contains all user-related information needed for authorization and task routing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtUserContext {

    private String userId;           // preferred_username
    private String email;            // email
    private String fullName;         // name
    private String department;       // department claim
    private List<String> groups;     // groups claim
    private List<String> roles;      // realm_access.roles
    private String managerId;        // manager_id claim (for delegation)
    private Integer doaLevel;        // doa_level claim (for approvals)
    private String tenantCode;       // tenant_code claim

    /**
     * Create JwtUserContext from Spring Security JWT token
     * @param jwt JWT token from Spring Security
     */
    public JwtUserContext(Jwt jwt) {
        this.userId = jwt.getClaimAsString("preferred_username");
        this.email = jwt.getClaimAsString("email");
        this.fullName = jwt.getClaimAsString("name");
        this.department = jwt.getClaimAsString("department");
        this.groups = jwt.getClaimAsStringList("groups");

        // Extract roles from realm_access
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List) {
            this.roles = (List<String>) realmAccess.get("roles");
        } else {
            this.roles = new ArrayList<>();
        }

        this.managerId = jwt.getClaimAsString("manager_id");

        String tc = jwt.getClaimAsString("tenant_code");
        this.tenantCode = (tc != null && !tc.isBlank()) ? tc : "default";

        // Parse doaLevel as Integer
        Object doaLevelClaim = jwt.getClaim("doa_level");
        if (doaLevelClaim != null) {
            if (doaLevelClaim instanceof Integer) {
                this.doaLevel = (Integer) doaLevelClaim;
            } else if (doaLevelClaim instanceof String) {
                try {
                    this.doaLevel = Integer.parseInt((String) doaLevelClaim);
                } catch (NumberFormatException e) {
                    this.doaLevel = null;
                }
            }
        }
    }

    /**
     * Check if user has a specific role
     * @param role Role name to check
     * @return true if user has the role
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user is in a specific group
     * @param group Group name to check
     * @return true if user is in the group
     */
    public boolean isInGroup(String group) {
        return groups != null && groups.contains(group);
    }
}
