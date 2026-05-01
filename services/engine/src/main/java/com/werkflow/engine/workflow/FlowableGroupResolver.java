package com.werkflow.engine.workflow;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.client.UserProfileDto;
import com.werkflow.engine.dto.JwtUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Flowable candidateGroup identifiers for a user — three-step pipeline.
 *
 * ADR-003: DoA cumulative loop and cross-dept compound groups removed.
 * Role→group mapping is now DB-backed per tenant (role_group_mappings table),
 * merged with YAML fallback entries (admin, super_admin, workflow_designer).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowableGroupResolver implements UserGroupLookupProxy {

    private final FlowableGroupProperties properties;
    private final AdminServiceClient adminServiceClient;

    @Override
    public List<String> resolveGroups(JwtUserContext userContext) {
        Set<String> resolved = new LinkedHashSet<>();
        String userId     = userContext.getUserId();
        String tenantCode = userContext.getTenantCode() != null ? userContext.getTenantCode() : "default";

        // Step 1: resolve user record from admin-service (departmentCode)
        UserProfileDto profile = null;
        try {
            profile = adminServiceClient.getUserProfile(userId, tenantCode);
        } catch (Exception e) {
            log.warn("FlowableGroupResolver: admin-service unavailable for user {} — degrading to YAML only: {}",
                userId, e.getMessage());
        }

        // Step 2: emit groups from YAML role-mappings merged with DB role-group mappings
        List<String> roles = userContext.getRoles();
        if (roles != null) {
            Map<String, List<String>> yamlMappings = properties.getRoleMappings();
            Map<String, List<String>> dbMappings   = fetchDbRoleMappings(tenantCode);

            for (String role : roles) {
                String key = role.toLowerCase();
                List<String> yamlGroups = yamlMappings.get(key);
                if (yamlGroups != null) resolved.addAll(yamlGroups);

                List<String> dbGroups = dbMappings.get(key);
                if (dbGroups != null) resolved.addAll(dbGroups);

                if (yamlGroups == null && dbGroups == null) {
                    log.debug("No role mapping for '{}' — skipped", role);
                }
            }
        }

        if (profile == null) {
            log.debug("FlowableGroupResolver: no profile for user {} — returning mapped groups only", userId);
            return new ArrayList<>(resolved);
        }

        // Step 3: emit department visibility group (scoping, not approval routing)
        String deptCode = profile.getDepartmentCode();
        if (deptCode != null && !deptCode.isBlank()) {
            resolved.add("DEPT:" + deptCode);

            // Step 4 (ERP tier): emit department approval-routing group — ADR-005
            resolved.add(deptCode + "_APPROVER");
        }

        log.debug("Resolved groups for user {}: {}", userId, resolved);
        return new ArrayList<>(resolved);
    }

    private Map<String, List<String>> fetchDbRoleMappings(String tenantCode) {
        try {
            return adminServiceClient.getRoleMappings(tenantCode);
        } catch (Exception e) {
            log.warn("FlowableGroupResolver: failed to fetch DB role mappings for tenant {} — {}",
                tenantCode, e.getMessage());
            return Map.of();
        }
    }
}
