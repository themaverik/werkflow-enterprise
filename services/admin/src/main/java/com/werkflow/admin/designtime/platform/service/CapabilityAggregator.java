package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.dto.CandidateGroupEntry;
import com.werkflow.admin.designtime.platform.dto.PlatformCapabilityResponse;
import com.werkflow.admin.designtime.platform.dto.VisibilityPolicyEntry;
import com.werkflow.admin.entity.ConfigurationVariable;
import com.werkflow.admin.repository.ConfigurationVariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Composes the full capability discovery response from all PSS data sources.
 * Used by all three designers on load to enable/disable sections.
 */
@Service
@RequiredArgsConstructor
public class CapabilityAggregator {

    private final ConfigurationVariableRepository configRepo;
    private final CandidateGroupsAggregator candidateGroupsAggregator;
    private final CategoryProjector categoryProjector;
    private final VisibilityPolicyProjector visibilityPolicyProjector;
    private final DepartmentProjector departmentProjector;

    /**
     * Returns the full capability state for the tenant.
     */
    @Cacheable(value = CacheConfig.PSS_CAPABILITIES, key = "#tenantCode")
    @Transactional(readOnly = true)
    public PlatformCapabilityResponse aggregate(String tenantCode) {
        List<ConfigurationVariable> allVars = configRepo.findByTenantCodeOrderByVarKey(tenantCode);
        boolean erpConnected = !departmentProjector.list(tenantCode).isEmpty();

        // configVars summary
        List<ConfigurationVariable> doaVars = allVars.stream()
                .filter(v -> "DOA_THRESHOLD".equals(v.getVarType())).collect(Collectors.toList());
        List<String> monetaryLevels = doaVars.stream().map(ConfigurationVariable::getVarKey).collect(Collectors.toList());
        String currency = allVars.stream()
                .filter(v -> "currency".equals(v.getVarKey()))
                .map(ConfigurationVariable::getVarValue)
                .findFirst().orElse("USD");
        List<String> types = doaVars.isEmpty() ? List.of() : List.of("DOA_THRESHOLD");

        // custodyVars summary
        List<ConfigurationVariable> custodyVars = allVars.stream()
                .filter(v -> "GROUP".equals(v.getVarType())).collect(Collectors.toList());
        List<String> custodyKeys = custodyVars.stream()
                .map(ConfigurationVariable::getVarKey).distinct().collect(Collectors.toList());

        // roleMappings summary
        List<CandidateGroupEntry> groups = candidateGroupsAggregator.aggregate(tenantCode);
        long tier1Count = groups.stream().filter(g -> g.tier() == 1).count();
        long tier2Count = groups.stream().filter(g -> g.tier() == 2).count();
        List<String> managerTierGroups = groups.stream()
                .filter(CandidateGroupEntry::isManagerTier)
                .map(CandidateGroupEntry::key)
                .collect(Collectors.toList());

        // departments
        int deptCount = departmentProjector.list(tenantCode).size();

        // categories
        long catCount = categoryProjector.list(tenantCode).size();

        // visibility policy
        VisibilityPolicyEntry vp = visibilityPolicyProjector.project(tenantCode);

        String tier = erpConnected ? "ENTERPRISE" : "OSS";

        return new PlatformCapabilityResponse(
                tier,
                erpConnected,
                new PlatformCapabilityResponse.ConfiguredSections(
                        new PlatformCapabilityResponse.ConfigVarsSummary(
                                doaVars.size(), types, monetaryLevels, currency),
                        new PlatformCapabilityResponse.CustodyVarsSummary(
                                custodyVars.size(), custodyKeys),
                        new PlatformCapabilityResponse.RoleMappingsSummary(
                                (int) tier1Count, (int) tier2Count, managerTierGroups),
                        new PlatformCapabilityResponse.DepartmentsSummary(deptCount),
                        new PlatformCapabilityResponse.CategoriesSummary((int) catCount),
                        new PlatformCapabilityResponse.VisibilityPolicySummary(vp.managerScope())
                )
        );
    }
}
