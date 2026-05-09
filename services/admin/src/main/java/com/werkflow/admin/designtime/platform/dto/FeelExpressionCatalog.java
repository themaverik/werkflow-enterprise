package com.werkflow.admin.designtime.platform.dto;

import java.util.List;

/**
 * FEEL expression catalog for DMN cell autocomplete, grouped by source (configVars, custodyVars).
 */
public record FeelExpressionCatalog(
        ConfigVarsSection configVars,
        CustodyVarsSection custodyVars
) {

    public record ConfigVarsSection(List<MonetaryEntry> monetary) {}

    public record MonetaryEntry(
            String key,
            String label,
            String value,
            String currency,
            List<String> feelExpressions
    ) {}

    public record CustodyVarsSection(
            List<GroupResolutionEntry> groupResolutions,
            List<String> lookupExpressions
    ) {}

    public record GroupResolutionEntry(
            String key,
            List<String> candidateGroups,
            String feelExpression
    ) {}
}
