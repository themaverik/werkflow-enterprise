package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.client.EngineClient;
import com.werkflow.admin.designtime.platform.dto.CandidateGroupEntry;
import com.werkflow.admin.entity.RoleGroupMapping;
import com.werkflow.admin.repository.RoleGroupMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Merges YAML Tier 1 and DB Tier 2 role-to-group mappings into a deduplicated
 * candidate-group list for the PSS endpoint.
 * Per ADR-010: excludes any group name ending in _APPROVER.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateGroupsAggregator {

    private final EngineClient engineClient;
    private final RoleGroupMappingRepository roleGroupMappingRepository;

    /**
     * Returns deduplicated candidate groups for the tenant, grouped by kind (SYSTEM vs BUSINESS).
     */
    @Cacheable(value = CacheConfig.PSS_CANDIDATE_GROUPS, key = "#tenantCode", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<CandidateGroupEntry> aggregate(String tenantCode) {
        Map<String, List<String>> tier1 = engineClient.getTier1RoleMappings();
        List<RoleGroupMapping> tier2Rows = roleGroupMappingRepository
                .findByTenantCodeOrderByRoleName(tenantCode);

        // Tier 1: SYSTEM groups — group by candidateGroup, collect roles
        Map<String, List<String>> systemGroupToRoles = new LinkedHashMap<>();
        tier1.forEach((role, groups) -> groups.forEach(g -> {
            if (!isApproverSuffix(g)) {
                systemGroupToRoles.computeIfAbsent(g, k -> new ArrayList<>()).add(role);
            }
        }));

        // Tier 2: BUSINESS groups — group by candidateGroup, collect roles and manager flag
        Map<String, Tier2Accumulator> businessGroupAccumulators = new LinkedHashMap<>();
        for (RoleGroupMapping row : tier2Rows) {
            if (isApproverSuffix(row.getGroupName())) continue;
            businessGroupAccumulators
                    .computeIfAbsent(row.getGroupName(), k -> new Tier2Accumulator(row.isManagerTier()))
                    .addRole(row.getRoleName());
        }

        // Union BPMN-discovered groups: add any group from deployed BPMNs that is not already
        // present in Tier-1 (system) or in the saved Tier-2 mappings. Groups discovered this
        // way are surfaced with no mapped roles (empty list) so the admin can create the mapping.
        // Engine failures are non-fatal: a warning is logged and we fall back to saved-only.
        List<String> bpmnGroups;
        try {
            bpmnGroups = engineClient.getBpmnCandidateGroups(tenantCode);
        } catch (Exception e) {
            log.warn("CandidateGroupsAggregator: failed to fetch BPMN candidate groups for tenant='{}', "
                    + "falling back to saved-mappings-only — {}", tenantCode, e.getMessage());
            bpmnGroups = List.of();
        }
        for (String group : bpmnGroups) {
            if (!systemGroupToRoles.containsKey(group) && !businessGroupAccumulators.containsKey(group)) {
                businessGroupAccumulators.put(group, new Tier2Accumulator(false));
            }
        }

        List<CandidateGroupEntry> result = new ArrayList<>();

        systemGroupToRoles.forEach((group, roles) ->
                result.add(new CandidateGroupEntry(group, toLabel(group), "SYSTEM", 1, true, false, roles)));

        businessGroupAccumulators.forEach((group, acc) ->
                result.add(new CandidateGroupEntry(group, toLabel(group), "BUSINESS", 2, false,
                        acc.isManagerTier, acc.roles)));

        return Collections.unmodifiableList(result);
    }

    /** Evicts cache when role mappings change — call from mutation endpoints. */
    @CacheEvict(value = {CacheConfig.PSS_CANDIDATE_GROUPS, CacheConfig.PSS_CAPABILITIES}, key = "#tenantCode")
    public void evict(String tenantCode) {}

    /** ADR-010 clean-break: reject any group that is a pure department-routing group. */
    private boolean isApproverSuffix(String groupName) {
        return groupName != null && groupName.endsWith("_APPROVER");
    }

    private String toLabel(String groupId) {
        return Arrays.stream(groupId.replace("_", " ").split(" "))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private static final class Tier2Accumulator {
        final boolean isManagerTier;
        final List<String> roles = new ArrayList<>();

        Tier2Accumulator(boolean isManagerTier) { this.isManagerTier = isManagerTier; }
        void addRole(String role) { roles.add(role); }
    }
}
