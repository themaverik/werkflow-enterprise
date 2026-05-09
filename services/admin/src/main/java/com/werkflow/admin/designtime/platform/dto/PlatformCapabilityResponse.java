package com.werkflow.admin.designtime.platform.dto;

import java.util.List;

/**
 * Capability discovery response — drives capability-aware UX in all three designers.
 * Frontend reads this once on load to enable/disable designer sections.
 */
public record PlatformCapabilityResponse(
        String tier,
        boolean erpConnected,
        ConfiguredSections configured
) {

    public record ConfiguredSections(
            ConfigVarsSummary configVars,
            CustodyVarsSummary custodyVars,
            RoleMappingsSummary roleMappings,
            DepartmentsSummary departments,
            CategoriesSummary categories,
            VisibilityPolicySummary visibilityPolicy
    ) {}

    public record ConfigVarsSummary(
            int count,
            List<String> types,
            List<String> monetaryLevels,
            String currency
    ) {}

    public record CustodyVarsSummary(
            int count,
            List<String> keys
    ) {}

    public record RoleMappingsSummary(
            int tier1Count,
            int tier2Count,
            List<String> managerTierGroups
    ) {}

    public record DepartmentsSummary(int count) {}

    public record CategoriesSummary(int count) {}

    public record VisibilityPolicySummary(String managerScope) {}
}
