package com.werkflow.admin.service;

import com.werkflow.admin.config.RoleConfigProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoleConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RoleConfigService.class);

    private final RoleConfigProperties roleConfigProperties;

    public RoleConfigService(RoleConfigProperties roleConfigProperties) {
        this.roleConfigProperties = roleConfigProperties;

        // Log loaded routes on startup
        if (roleConfigProperties.getRoutes() != null && !roleConfigProperties.getRoutes().isEmpty()) {
            logger.info("Route Configuration loaded successfully with {} routes:", roleConfigProperties.getRoutes().size());
            roleConfigProperties.getRoutes().forEach((route, roles) ->
                logger.info("  Route '{}' -> Required roles: {}", route, roles)
            );
        } else {
            logger.warn("No route configuration loaded! RoleConfigProperties.routes is empty or null");
        }
    }

    /**
     * Get required roles for a specific route path.
     *
     * @param routePath The route path (e.g., "/studio", "/services")
     * @return List of required role names, empty if no specific config
     */
    public List<String> getRequiredRolesForRoute(String routePath) {
        if (routePath == null || routePath.isEmpty()) {
            return new ArrayList<>();
        }

        // Normalize path - remove leading slash for matching against YAML keys
        String normalizedPath = routePath.startsWith("/") ? routePath.substring(1) : routePath;

        // Check exact match first
        if (roleConfigProperties.getRoutes() != null &&
            roleConfigProperties.getRoutes().containsKey(normalizedPath)) {
            String roles = roleConfigProperties.getRoutes().get(normalizedPath);
            return parseRoles(roles);
        }

        // Check wildcard patterns
        if (roleConfigProperties.getRoutes() != null) {
            for (Map.Entry<String, String> entry : roleConfigProperties.getRoutes().entrySet()) {
                String pattern = entry.getKey();
                if (pattern.endsWith("/**")) {
                    String prefix = pattern.substring(0, pattern.length() - 3);
                    if (normalizedPath.startsWith(prefix)) {
                        return parseRoles(entry.getValue());
                    }
                }
            }
        }

        return new ArrayList<>();
    }

    /**
     * Check if a user has the required roles for a route.
     *
     * @param routePath  The route path
     * @param authentication Spring Security authentication
     * @return true if user has at least one of the required roles
     */
    public boolean userHasRequiredRoles(String routePath, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        List<String> requiredRoles = getRequiredRolesForRoute(routePath);

        // If no specific role config for route, allow authenticated users
        if (requiredRoles.isEmpty()) {
            return true;
        }

        // Check if user has any of the required roles
        return userHasAnyRole(authentication, requiredRoles);
    }

    /**
     * Check if a user has any of the specified roles.
     *
     * @param authentication Spring Security authentication
     * @param requiredRoles  List of role names to check
     * @return true if user has at least one role
     */
    public boolean userHasAnyRole(Authentication authentication, List<String> requiredRoles) {
        if (authentication == null || requiredRoles == null || requiredRoles.isEmpty()) {
            return false;
        }

        List<String> userRoles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(auth -> auth.replace("ROLE_", ""))
            .collect(Collectors.toList());

        return requiredRoles.stream()
            .anyMatch(requiredRole ->
                userRoles.stream()
                    .anyMatch(userRole -> userRole.equalsIgnoreCase(requiredRole))
            );
    }

    /**
     * Check if a user has all the specified roles.
     *
     * @param authentication Spring Security authentication
     * @param requiredRoles  List of role names to check
     * @return true if user has all roles
     */
    public boolean userHasAllRoles(Authentication authentication, List<String> requiredRoles) {
        if (authentication == null || requiredRoles == null || requiredRoles.isEmpty()) {
            return false;
        }

        List<String> userRoles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(auth -> auth.replace("ROLE_", ""))
            .collect(Collectors.toList());

        return requiredRoles.stream()
            .allMatch(requiredRole ->
                userRoles.stream()
                    .anyMatch(userRole -> userRole.equalsIgnoreCase(requiredRole))
            );
    }

    /**
     * Get all configured routes and their required roles.
     *
     * @return Map of route path to required roles
     */
    public Map<String, List<String>> getAllConfiguredRoutes() {
        Map<String, List<String>> result = new HashMap<>();
        if (roleConfigProperties.getRoutes() != null) {
            roleConfigProperties.getRoutes().forEach((path, roles) ->
                result.put(path, parseRoles(roles))
            );
        }
        return result;
    }

    /**
     * Parse comma-separated role string into list.
     *
     * @param roleString Comma-separated roles (e.g., "HR_ADMIN,ADMIN,SUPER_ADMIN")
     * @return List of role names
     */
    private List<String> parseRoles(String roleString) {
        if (roleString == null || roleString.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return List.of(roleString.split(","))
            .stream()
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Log access denial for audit purposes.
     *
     * @param routePath     The route path
     * @param authentication Spring Security authentication
     */
    public void logAccessDenial(String routePath, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "ANONYMOUS";
        List<String> requiredRoles = getRequiredRolesForRoute(routePath);
        List<String> userRoles = authentication != null ?
            authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList())
            : List.of();

        logger.warn(
            "Access denied for user '{}' to route '{}'. Required roles: {}, User roles: {}",
            username, routePath, requiredRoles, userRoles
        );
    }

}
