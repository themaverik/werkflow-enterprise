package com.werkflow.engine.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts roles and authorities from Keycloak JWT tokens.
 * Handles both realm roles and client-specific roles.
 */
@Component
public class KeycloakRoleExtractor {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Extract all roles from JWT token (realm + client roles)
     *
     * @param jwt JWT token
     * @return Collection of GrantedAuthority
     */
    public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Extract realm roles
        authorities.addAll(extractRealmRoles(jwt));

        // Extract client roles
        authorities.addAll(extractClientRoles(jwt, "werkflow-engine"));
        authorities.addAll(extractClientRoles(jwt, "werkflow-admin-portal"));

        return authorities;
    }

    /**
     * Extract role names as plain uppercase strings (no ROLE_ prefix).
     * Converts hyphens to underscores to match YAML permission matrix keys.
     * Example: workflow-designer → WORKFLOW_DESIGNER
     */
    public List<String> extractRoleNames(Jwt jwt) {
        return extractAuthorities(jwt).stream()
            .map(auth -> auth.getAuthority().substring(ROLE_PREFIX.length()).replace("-", "_"))
            .toList();
    }

    /**
     * Extract realm roles from token
     *
     * @param jwt JWT token
     * @return Collection of realm role authorities
     */
    public Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get(ROLES_CLAIM);
        if (roles == null) {
            return Collections.emptyList();
        }

        return roles.stream()
            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
            .collect(Collectors.toList());
    }

    /**
     * Extract client-specific roles from token
     *
     * @param jwt      JWT token
     * @param clientId Client ID to extract roles for
     * @return Collection of client role authorities
     */
    public Collection<GrantedAuthority> extractClientRoles(Jwt jwt, String clientId) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
        if (clientAccess == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) clientAccess.get(ROLES_CLAIM);
        if (roles == null) {
            return Collections.emptyList();
        }

        return roles.stream()
            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
            .collect(Collectors.toList());
    }

    /**
     * Extract groups from token
     *
     * @param jwt JWT token
     * @return List of group paths
     */
    public List<String> extractGroups(Jwt jwt) {
        return jwt.getClaimAsStringList("groups");
    }

    /**
     * Check if user has specific role
     *
     * @param jwt  JWT token
     * @param role Role name (without ROLE_ prefix)
     * @return true if user has role
     */
    public boolean hasRole(Jwt jwt, String role) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals(ROLE_PREFIX + role.toUpperCase()));
    }

    /**
     * Check if user has any of the specified roles
     *
     * @param jwt   JWT token
     * @param roles List of role names
     * @return true if user has at least one role
     */
    public boolean hasAnyRole(Jwt jwt, String... roles) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        Set<String> authoritySet = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        return Arrays.stream(roles)
            .anyMatch(role -> authoritySet.contains(ROLE_PREFIX + role.toUpperCase()));
    }

    /**
     * Check if user belongs to specific group
     *
     * @param jwt       JWT token
     * @param groupPath Full group path
     * @return true if user is member of group
     */
    public boolean isMemberOfGroup(Jwt jwt, String groupPath) {
        List<String> groups = extractGroups(jwt);
        return groups != null && groups.contains(groupPath);
    }

    /**
     * Check if user belongs to any of the specified groups
     *
     * @param jwt        JWT token
     * @param groupPaths List of group paths
     * @return true if user belongs to at least one group
     */
    public boolean isMemberOfAnyGroup(Jwt jwt, String... groupPaths) {
        List<String> groups = extractGroups(jwt);
        if (groups == null || groups.isEmpty()) {
            return false;
        }

        return Arrays.stream(groupPaths)
            .anyMatch(groups::contains);
    }

    /**
     * Extract custom attribute from token
     *
     * @param jwt           JWT token
     * @param attributeName Attribute name
     * @return Attribute value as String, null if not present
     */
    public String getCustomAttribute(Jwt jwt, String attributeName) {
        return jwt.getClaimAsString(attributeName);
    }

    /**
     * Extract custom attribute as Integer
     *
     * @param jwt           JWT token
     * @param attributeName Attribute name
     * @return Attribute value as Integer, null if not present
     */
    public Integer getCustomAttributeAsInt(Jwt jwt, String attributeName) {
        Object claim = jwt.getClaim(attributeName);
        if (claim == null) {
            return null;
        }
        if (claim instanceof Number) {
            return ((Number) claim).intValue();
        }
        try {
            return Integer.parseInt(claim.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract custom attribute as Boolean
     *
     * @param jwt           JWT token
     * @param attributeName Attribute name
     * @return Attribute value as Boolean, null if not present
     */
    public Boolean getCustomAttributeAsBoolean(Jwt jwt, String attributeName) {
        Object claim = jwt.getClaim(attributeName);
        if (claim == null) {
            return null;
        }
        if (claim instanceof Boolean) {
            return (Boolean) claim;
        }
        return Boolean.parseBoolean(claim.toString());
    }

    /**
     * Get user's department from token
     *
     * @param jwt JWT token
     * @return Department name
     */
    public String getDepartment(Jwt jwt) {
        return getCustomAttribute(jwt, "department");
    }

    /**
     * Get user's DOA level from token
     *
     * @param jwt JWT token
     * @return DOA level (1-4), null if not set
     */
    public Integer getDoaLevel(Jwt jwt) {
        return getCustomAttributeAsInt(jwt, "doa_level");
    }

    /**
     * Get user's manager ID from token
     *
     * @param jwt JWT token
     * @return Manager's Keycloak user ID
     */
    public String getManagerId(Jwt jwt) {
        return getCustomAttribute(jwt, "manager_id");
    }

    /**
     * Get user's cost center from token
     *
     * @param jwt JWT token
     * @return Cost center code
     */
    public String getCostCenter(Jwt jwt) {
        return getCustomAttribute(jwt, "cost_center");
    }

    /**
     * Check if user is department POC
     *
     * @param jwt JWT token
     * @return true if user is POC
     */
    public boolean isPoc(Jwt jwt) {
        Boolean isPoc = getCustomAttributeAsBoolean(jwt, "is_poc");
        return isPoc != null && isPoc;
    }

    /**
     * Get user's hub ID from token
     *
     * @param jwt JWT token
     * @return Hub identifier
     */
    public String getHubId(Jwt jwt) {
        return getCustomAttribute(jwt, "hub_id");
    }

    /**
     * Get user's employee ID from token
     *
     * @param jwt JWT token
     * @return Employee ID
     */
    public String getEmployeeId(Jwt jwt) {
        return getCustomAttribute(jwt, "employee_id");
    }

    /**
     * Get user ID (subject) from token
     *
     * @param jwt JWT token
     * @return Keycloak user ID
     */
    public String getUserId(Jwt jwt) {
        return jwt.getSubject();
    }

    /**
     * Get user email from token
     *
     * @param jwt JWT token
     * @return User email
     */
    public String getUserEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    /**
     * Get user's full name from token
     *
     * @param jwt JWT token
     * @return Full name
     */
    public String getFullName(Jwt jwt) {
        return jwt.getClaimAsString("name");
    }

    /**
     * Get username from token
     *
     * @param jwt JWT token
     * @return Username (preferred_username)
     */
    public String getUsername(Jwt jwt) {
        return jwt.getClaimAsString("preferred_username");
    }
}
