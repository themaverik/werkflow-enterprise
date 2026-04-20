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
 * Resolves Flowable candidateGroup identifiers for a user — five-step pipeline.
 *
 * Phase 2 note: emits BOTH old format (DOA_L1) and new prefix format (DOA:L1) simultaneously
 * so existing BPMN candidateGroups continue to match while Phase 3 migrates them.
 * Phase 3 removes old-format output once all BPMN is updated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowableGroupResolver {

    private final FlowableGroupProperties properties;
    private final AdminServiceClient adminServiceClient;

    public List<String> resolveGroups(JwtUserContext userContext) {
        Set<String> resolved = new LinkedHashSet<>();
        String userId     = userContext.getUserId();
        String tenantCode = userContext.getTenantCode() != null ? userContext.getTenantCode() : "default";

        // Step 1: resolve user record from admin-service (doaLevel, departmentCode)
        UserProfileDto profile = null;
        try {
            profile = adminServiceClient.getUserProfile(userId, tenantCode);
        } catch (Exception e) {
            log.warn("FlowableGroupResolver: admin-service unavailable for user {} — degrading to YAML only: {}",
                userId, e.getMessage());
        }

        // Step 2: emit system groups from Keycloak roles via YAML role-mappings
        List<String> roles = userContext.getRoles();
        if (roles != null) {
            Map<String, List<String>> roleMappings = properties.getRoleMappings();
            for (String role : roles) {
                List<String> groups = roleMappings.get(role.toLowerCase());
                if (groups != null) resolved.addAll(groups);
                else log.debug("No role mapping for '{}' — skipped", role);
            }
        }

        if (profile == null) {
            log.debug("FlowableGroupResolver: no profile for user {} — returning YAML groups only", userId);
            return new ArrayList<>(resolved);
        }

        // Step 3: emit DoA groups (cumulative), both old and new format
        Integer doaLevel = profile.getDoaLevel();
        if (doaLevel != null && doaLevel > 0) {
            for (int level = 1; level <= doaLevel; level++) {
                resolved.add("DOA:L" + level);  // new prefix format (Phase 3+)
                resolved.add("DOA_L" + level);  // old format (Phase 2 backward compat)
            }
        }

        // Step 4: emit department groups and compound groups
        String deptCode = profile.getDepartmentCode();
        if (deptCode != null && !deptCode.isBlank()) {
            resolved.add("DEPT:" + deptCode);
            if (doaLevel != null && doaLevel > 0) {
                for (int level = 1; level <= doaLevel; level++) {
                    resolved.add("DEPT:" + deptCode + "::DOA:L" + level);
                }
            }

            // Cross-dept authority: if doaLevel >= tenant threshold, emit compounds for ALL depts
            if (doaLevel != null) {
                int threshold = adminServiceClient.getTenantCrossDeptThreshold(tenantCode);
                if (doaLevel >= threshold) {
                    List<String> allCodes = adminServiceClient.getTenantDepartmentCodes(tenantCode);
                    for (String code : allCodes) {
                        if (!code.equals(deptCode)) {
                            resolved.add("DEPT:" + code);
                            for (int level = 1; level <= doaLevel; level++) {
                                resolved.add("DEPT:" + code + "::DOA:L" + level);
                            }
                        }
                    }
                }
            }
        }

        // Step 5: deduplicated (LinkedHashSet preserves insertion order)
        log.debug("Resolved groups for user {}: {}", userId, resolved);
        return new ArrayList<>(resolved);
    }
}
