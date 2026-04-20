package com.werkflow.engine.util;

import com.werkflow.engine.dto.JwtUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class to extract custom claims from Keycloak JWT tokens
 * Provides type-safe access to user attributes for task authorization and routing
 */
@Component
@Slf4j
public class JwtClaimsExtractor {

    /**
     * Extract complete user context from JWT token
     * @param jwt JWT token from authentication
     * @return JwtUserContext with all user information
     */
    public JwtUserContext extractUserContext(Jwt jwt) {
        return JwtUserContext.builder()
                .userId(getUserId(jwt))
                .email(getEmail(jwt))
                .fullName(getFullName(jwt))
                .department(getDepartment(jwt))
                .groups(getUserGroups(jwt))
                .roles(getUserRoles(jwt))
                .managerId(getManagerId(jwt))
                .doaLevel(getDoaLevel(jwt))
                .tenantCode(getTenantCode(jwt))
                .build();
    }

    /**
     * Get user ID (preferred_username)
     * @param jwt JWT token
     * @return User ID
     */
    public String getUserId(Jwt jwt) {
        return jwt.getClaimAsString("preferred_username");
    }

    /**
     * Get email from JWT
     * @param jwt JWT token
     * @return Email address
     */
    public String getEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    /**
     * Get full name from JWT
     * @param jwt JWT token
     * @return Full name
     */
    public String getFullName(Jwt jwt) {
        return jwt.getClaimAsString("name");
    }

    /**
     * Get department from JWT claims
     * @param jwt JWT token
     * @return Department name
     */
    public String getDepartment(Jwt jwt) {
        return jwt.getClaimAsString("department");
    }

    /**
     * Get manager ID from JWT claims
     * Used for task routing to line managers
     * @param jwt JWT token
     * @return Manager ID
     */
    public String getManagerId(Jwt jwt) {
        return jwt.getClaimAsString("manager_id");
    }

    /**
     * Get DOA (Delegation of Authority) level from JWT claims
     * Levels: 1 ($0-$1K), 2 ($1K-$10K), 3 ($10K-$100K), 4 (>$100K)
     * Returns 0 if user has no approval authority
     * @param jwt JWT token
     * @return DOA level (0-4)
     */
    public Integer getDoaLevel(Jwt jwt) {
        Object doaLevel = jwt.getClaim("doa_level");
        if (doaLevel == null) {
            return 0;
        }
        if (doaLevel instanceof Number) {
            return ((Number) doaLevel).intValue();
        }
        try {
            return Integer.parseInt(doaLevel.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid DOA level format for user {}: {}", getUserId(jwt), doaLevel);
            return 0;
        }
    }

    /**
     * Get user groups from JWT claims
     * Returns list of group IDs/paths user belongs to
     * @param jwt JWT token
     * @return List of group identifiers
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserGroups(Jwt jwt) {
        Object groupsClaim = jwt.getClaim("groups");

        if (groupsClaim instanceof List) {
            return ((List<?>) groupsClaim).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        log.debug("User {} has no groups claim", getUserId(jwt));
        return Collections.emptyList();
    }

    /**
     * Get user roles from realm_access claim
     * @param jwt JWT token
     * @return List of role names
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");

        if (realmAccess != null && realmAccess.containsKey("roles")) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List) {
                return ((List<?>) rolesObj).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }
        }

        log.debug("User {} has no roles in realm_access", getUserId(jwt));
        return Collections.emptyList();
    }

    /**
     * Check if user has a specific role
     * @param jwt JWT token
     * @param role Role name to check
     * @return true if user has the role
     */
    public String getTenantCode(Jwt jwt) {
        String tc = jwt.getClaimAsString("tenant_code");
        return (tc != null && !tc.isBlank()) ? tc : "default";
    }

    public boolean hasRole(Jwt jwt, String role) {
        List<String> roles = getUserRoles(jwt);
        return roles.contains(role);
    }

    /**
     * Check if user is in a specific group
     * @param jwt JWT token
     * @param group Group name to check
     * @return true if user is in the group
     */
    public boolean isInGroup(Jwt jwt, String group) {
        List<String> groups = getUserGroups(jwt);
        return groups.contains(group);
    }
}
