package com.werkflow.admin.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility class to extract custom claims from Keycloak JWT tokens.
 * Provides type-safe access to user attributes like department, DOA level, etc.
 */
@Component
public class JwtClaimsExtractor {

    /**
     * Get user ID (Keycloak UUID) from JWT subject claim
     */
    public String getUserId(Jwt jwt) {
        return jwt.getSubject();
    }

    /**
     * Get email from JWT
     */
    public String getEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    /**
     * Get username (preferred_username) from JWT
     */
    public String getUsername(Jwt jwt) {
        return jwt.getClaimAsString("preferred_username");
    }

    /**
     * Get first name from JWT
     */
    public String getFirstName(Jwt jwt) {
        return jwt.getClaimAsString("given_name");
    }

    /**
     * Get last name from JWT
     */
    public String getLastName(Jwt jwt) {
        return jwt.getClaimAsString("family_name");
    }

    /**
     * Get full name from JWT
     */
    public String getFullName(Jwt jwt) {
        return jwt.getClaimAsString("name");
    }

    /**
     * Get department from custom attribute
     */
    public String getDepartment(Jwt jwt) {
        return jwt.getClaimAsString("department");
    }

    /**
     * Get manager ID from custom attribute
     * Used for task routing to line managers
     */
    public String getManagerId(Jwt jwt) {
        return jwt.getClaimAsString("manager_id");
    }

    /**
     * Get DOA (Delegation of Authority) level from custom attribute
     * Levels: 1 ($0-$1K), 2 ($1K-$10K), 3 ($10K-$100K), 4 (>$100K)
     * Returns 0 if user has no approval authority
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
            return 0;
        }
    }

    /**
     * Get cost center from custom attribute
     */
    public String getCostCenter(Jwt jwt) {
        return jwt.getClaimAsString("cost_center");
    }

    /**
     * Get hub ID from custom attribute (for warehouse managers)
     */
    public String getHubId(Jwt jwt) {
        return jwt.getClaimAsString("hub_id");
    }

    /**
     * Get business unit from custom attribute
     */
    public String getBusinessUnit(Jwt jwt) {
        return jwt.getClaimAsString("business_unit");
    }

    /**
     * Get phone number from custom attribute
     */
    public String getPhone(Jwt jwt) {
        return jwt.getClaimAsString("phone");
    }

    /**
     * Get location from custom attribute
     */
    public String getLocation(Jwt jwt) {
        return jwt.getClaimAsString("location");
    }

    /**
     * Get groups (organizational hierarchy) from JWT
     * Returns group paths like ["/HR Department", "/HR Department/Managers"]
     */
    @SuppressWarnings("unchecked")
    public List<String> getGroups(Jwt jwt) {
        Object groupsObj = jwt.getClaim("groups");
        if (groupsObj instanceof List) {
            return (List<String>) groupsObj;
        }
        return List.of();
    }

    /**
     * Check if user is Point of Contact (POC) for their department
     */
    public Boolean isPOC(Jwt jwt) {
        Object isPOC = jwt.getClaim("is_poc");
        if (isPOC == null) {
            return false;
        }
        if (isPOC instanceof Boolean) {
            return (Boolean) isPOC;
        }
        return Boolean.parseBoolean(isPOC.toString());
    }

    /**
     * Check if user has specific role in realm_access.roles
     */
    public Boolean hasRole(Jwt jwt, String role) {
        @SuppressWarnings("unchecked")
        var realmAccess = (java.util.Map<String, Object>) jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user is in specific group (organizational unit)
     */
    public Boolean isInGroup(Jwt jwt, String groupPath) {
        List<String> groups = getGroups(jwt);
        return groups.stream()
            .anyMatch(group -> group.equals(groupPath) || group.startsWith(groupPath + "/"));
    }

    /**
     * Get all roles from JWT token
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(Jwt jwt) {
        var realmAccess = (java.util.Map<String, Object>) jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles != null ? roles : List.of();
    }
}
