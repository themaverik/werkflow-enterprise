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
 * Resolves Flowable candidateGroup identifiers for a user — four-step pipeline.
 *
 * Step 1: fetch user profile from admin-service (for department attribution only).
 * Step 2: emit groups from YAML role-mappings merged with DB role-group mappings.
 * Step 3: emit department visibility group (DEPT:{code}) for query-layer scoping.
 * Step 4: emit DOA authorization group (DOA_L{level}) from JWT doa_level claim (ADR-029).
 *
 * ADR-003: role→group mapping is DB-backed per tenant (role_group_mappings table),
 * merged with YAML fallback entries (admin, super_admin, workflow_designer).
 * ADR-010: department-derived approval routing groups ({deptCode}_APPROVER) removed.
 * Routing is handled entirely by DMN with role-mapped groups.
 * ADR-029: DOA_L* (underscore) is canonical; DOA:L* (colon) format retired.
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

        // Step 4: emit DOA authorization group from JWT doa_level claim — independent of ERP profile (ADR-029)
        Integer doaLevel = userContext.getDoaLevel();
        if (doaLevel != null) {
            resolved.add("DOA_L" + doaLevel);
        }

        if (profile == null) {
            log.debug("FlowableGroupResolver: no profile for user {} — skipping DEPT group", userId);
            return new ArrayList<>(resolved);
        }

        // Step 3: emit department visibility group (scoping only, not approval routing — ADR-010)
        String deptCode = profile.getDepartmentCode();
        if (deptCode != null && !deptCode.isBlank()) {
            resolved.add("DEPT:" + deptCode);
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
