package com.werkflow.engine.security;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.security.guard.DomainGuard;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WerkflowPermissionEvaluator implements PermissionEvaluator {

    private final PermissionConfig permissionConfig;
    private final KeycloakRoleExtractor roleExtractor;
    private final AdminServiceClient adminServiceClient;
    private final List<DomainGuard> guards;

    /** Populated on startup from the registered DomainGuard beans. */
    private Map<String, DomainGuard> guardRegistry;

    public WerkflowPermissionEvaluator(PermissionConfig permissionConfig,
                                        KeycloakRoleExtractor roleExtractor,
                                        AdminServiceClient adminServiceClient,
                                        List<DomainGuard> guards) {
        this.permissionConfig = permissionConfig;
        this.roleExtractor = roleExtractor;
        this.adminServiceClient = adminServiceClient;
        this.guards = guards;
    }

    @PostConstruct
    void buildRegistry() {
        guardRegistry = guards.stream()
            .collect(Collectors.toMap(DomainGuard::supports, Function.identity()));
    }

    /**
     * Coarse check — role has permission in YAML matrix first, then tenant DB table.
     * Usage: hasPermission(null, 'RESOURCE:ACTION')
     */
    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (!(auth.getPrincipal() instanceof Jwt jwt)) return false;
        List<String> roles = roleExtractor.extractRoleNames(jwt);
        // Check YAML-defined system permissions first (fast path)
        Set<String> yamlPerms = permissionConfig.getPermissionsForRoles(roles);
        if (yamlPerms.contains(permission.toString())) return true;
        // Fall back to tenant-specific DB permissions
        // JWT uses "tenant_id" claim (not "tenant_code") per Keycloak mapper config
        String tenantCode = jwt.getClaimAsString("tenant_id");
        if (tenantCode == null || tenantCode.isBlank()) tenantCode = "default";
        try {
            Set<String> tenantPerms = adminServiceClient.getTenantRolePermissions(tenantCode, roles);
            return tenantPerms.contains(permission.toString());
        } catch (Exception e) {
            log.warn("Failed to fetch tenant permissions from admin service for tenant '{}': {}",
                tenantCode, e.getMessage());
            return false;
        }
    }

    /**
     * Fine-grained check — coarse gate first, then domain guard.
     *
     * The {@code permission} parameter must be the full YAML key (e.g. 'ASSET_REQUEST:APPROVE').
     * Usage: hasPermission(#requestId, 'AssetRequest', 'ASSET_REQUEST:APPROVE')
     *
     * The action passed to each guard is the suffix after ':' (e.g. 'APPROVE').
     * Guards are discovered automatically — add a {@link DomainGuard} bean to extend.
     */
    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId,
                                  String targetType, Object permission) {
        if (!hasPermission(auth, null, permission)) return false;
        String[] parts = permission.toString().split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid permission key (expected 'RESOURCE:ACTION'): " + permission);
        }
        String action = parts[1];
        DomainGuard guard = guardRegistry.get(targetType);
        if (guard == null) return false;
        return guard.canAct(auth, targetId, action);
    }
}
