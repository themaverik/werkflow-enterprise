package com.werkflow.engine.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "werkflow")
public class PermissionConfig {

    /**
     * Flat permission sets per role. Each role lists only its own permissions;
     * inherited permissions are added at runtime via {@code role-hierarchy}.
     *
     * Platform roles: EMPLOYEE, WORKFLOW_DESIGNER, WORKFLOW_ADMIN, ADMIN, SUPER_ADMIN.
     * Business roles (Department Manager, Finance Director, etc.) are sourced from
     * the tenant DB at runtime — do not define them here.
     */
    private Map<String, Set<String>> permissions = new HashMap<>();

    /**
     * Defines which role each entry inherits from (child → parent).
     * getPermissionsForRoles() expands the full transitive permission set automatically.
     *
     * Example:
     *   ADMIN → WORKFLOW_ADMIN → EMPLOYEE
     */
    private Map<String, String> roleHierarchy = new HashMap<>();

    public Map<String, Set<String>> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Set<String>> permissions) {
        this.permissions = permissions;
    }

    public Map<String, String> getRoleHierarchy() {
        return roleHierarchy;
    }

    public void setRoleHierarchy(Map<String, String> roleHierarchy) {
        this.roleHierarchy = roleHierarchy;
    }

    /**
     * Returns the union of all permissions granted to the given roles, including inherited permissions.
     */
    public Set<String> getPermissionsForRoles(List<String> roles) {
        return roles.stream()
            .flatMap(role -> expandPermissions(role).stream())
            .collect(Collectors.toSet());
    }

    /**
     * Expands the full permission set for a role by following the inheritance chain.
     * Stops if a cycle is detected or the parent role is not defined.
     */
    private Set<String> expandPermissions(String role) {
        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        String current = role;
        while (current != null && visited.add(current)) {
            result.addAll(permissions.getOrDefault(current, Collections.emptySet()));
            current = roleHierarchy.get(current);
        }
        return result;
    }
}
