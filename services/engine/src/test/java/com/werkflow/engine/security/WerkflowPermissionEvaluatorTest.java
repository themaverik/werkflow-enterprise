package com.werkflow.engine.security;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.security.guard.AssetRequestGuard;
import com.werkflow.engine.security.guard.HubManagerGuard;
import com.werkflow.engine.security.guard.TaskGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WerkflowPermissionEvaluatorTest {

    @Mock private PermissionConfig permissionConfig;
    @Mock private KeycloakRoleExtractor roleExtractor;
    @Mock private AdminServiceClient adminServiceClient;
    @Mock private AssetRequestGuard assetRequestGuard;
    @Mock private TaskGuard taskGuard;
    @Mock private HubManagerGuard hubManagerGuard;
    @Mock private Authentication authentication;
    @Mock private Jwt jwt;

    private WerkflowPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        when(assetRequestGuard.supports()).thenReturn("AssetRequest");
        when(taskGuard.supports()).thenReturn("Task");
        when(hubManagerGuard.supports()).thenReturn("HubManager");

        evaluator = new WerkflowPermissionEvaluator(
                permissionConfig, roleExtractor, adminServiceClient,
                List.of(assetRequestGuard, taskGuard, hubManagerGuard));
        evaluator.buildRegistry();
        // buildRegistry() calls guard.supports() to populate the registry; clear those
        // interactions so verifyNoInteractions() in tests only sees decision-path calls.
        clearInvocations(assetRequestGuard, taskGuard, hubManagerGuard);

        // Default: no tenant-specific permissions (tests that fall through YAML check get false)
        lenient().when(adminServiceClient.getTenantRolePermissions(any(), any())).thenReturn(Set.of());
        lenient().when(jwt.getClaimAsString("tenant_code")).thenReturn(null);

        when(authentication.getPrincipal()).thenReturn(jwt);
    }

    // ── Coarse checks ────────────────────────────────────────────────────────

    @Test
    void coarse_roleWithPermission_returnsTrue() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("WORKFLOW_ADMIN"));
        when(permissionConfig.getPermissionsForRoles(List.of("WORKFLOW_ADMIN")))
            .thenReturn(Set.of("AUDIT:VIEW", "WORKFLOW:MANAGE"));

        assertThat(evaluator.hasPermission(authentication, null, "AUDIT:VIEW")).isTrue();
    }

    @Test
    void coarse_roleWithoutPermission_returnsFalse() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("EMPLOYEE"));
        when(permissionConfig.getPermissionsForRoles(List.of("EMPLOYEE")))
            .thenReturn(Set.of("ASSET_REQUEST:SUBMIT", "TASK:VIEW_OWN"));

        assertThat(evaluator.hasPermission(authentication, null, "AUDIT:VIEW")).isFalse();
    }

    @Test
    void coarse_multipleRoles_unionApplied_returnsTrue() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("EMPLOYEE", "WORKFLOW_DESIGNER"));
        when(permissionConfig.getPermissionsForRoles(List.of("EMPLOYEE", "WORKFLOW_DESIGNER")))
            .thenReturn(Set.of("ASSET_REQUEST:SUBMIT", "TASK:VIEW_OWN", "WORKFLOW:DEPLOY"));

        assertThat(evaluator.hasPermission(authentication, null, "WORKFLOW:DEPLOY")).isTrue();
    }

    @Test
    void coarse_permissionNotInAnyRole_returnsFalse() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("EMPLOYEE"));
        when(permissionConfig.getPermissionsForRoles(List.of("EMPLOYEE")))
            .thenReturn(Set.of("ASSET_REQUEST:SUBMIT"));

        assertThat(evaluator.hasPermission(authentication, null, "SYSTEM:ADMIN")).isFalse();
    }

    // ── Fine-grained checks ──────────────────────────────────────────────────

    @Test
    void fineGrained_coarseGateFails_guardNeverCalled() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("EMPLOYEE"));
        when(permissionConfig.getPermissionsForRoles(List.of("EMPLOYEE")))
            .thenReturn(Set.of("ASSET_REQUEST:SUBMIT"));

        // "ASSET_REQUEST:APPROVE" not in user's permissions → coarse gate fails
        boolean result = evaluator.hasPermission(authentication, "req-1", "AssetRequest", "ASSET_REQUEST:APPROVE");

        assertThat(result).isFalse();
        verifyNoInteractions(assetRequestGuard);
    }

    @Test
    void fineGrained_coarseGatePasses_guardReturnsFalse_returnsFalse() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("ASSET_REQUEST_APPROVER"));
        when(permissionConfig.getPermissionsForRoles(List.of("ASSET_REQUEST_APPROVER")))
            .thenReturn(Set.of("ASSET_REQUEST:APPROVE"));
        when(assetRequestGuard.canAct(authentication, "req-1", "APPROVE")).thenReturn(false);

        // Permission "ASSET_REQUEST:APPROVE" → coarse passes, guard denies
        boolean result = evaluator.hasPermission(authentication, "req-1", "AssetRequest", "ASSET_REQUEST:APPROVE");

        assertThat(result).isFalse();
    }

    @Test
    void fineGrained_coarseGatePasses_guardReturnsTrue_returnsTrue() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("ASSET_REQUEST_APPROVER"));
        when(permissionConfig.getPermissionsForRoles(List.of("ASSET_REQUEST_APPROVER")))
            .thenReturn(Set.of("ASSET_REQUEST:APPROVE"));
        when(assetRequestGuard.canAct(authentication, "req-1", "APPROVE")).thenReturn(true);

        boolean result = evaluator.hasPermission(authentication, "req-1", "AssetRequest", "ASSET_REQUEST:APPROVE");

        assertThat(result).isTrue();
    }

    @Test
    void fineGrained_unknownTargetType_coarsePassesSwitchHitsDefault_returnsFalse() {
        when(roleExtractor.extractRoleNames(jwt)).thenReturn(List.of("SUPER_ADMIN"));
        // Mock to include a permission that the coarse gate will find
        when(permissionConfig.getPermissionsForRoles(List.of("SUPER_ADMIN")))
            .thenReturn(Set.of("SYSTEM:ADMIN"));

        // targetType "Widget" is not in the switch — default branch returns false
        // even though coarse gate passes ("SYSTEM:ADMIN" is in user's permissions)
        boolean result = evaluator.hasPermission(authentication, "id-1", "Widget", "SYSTEM:ADMIN");

        assertThat(result).isFalse();
        verifyNoInteractions(assetRequestGuard, taskGuard, hubManagerGuard);
    }
}
