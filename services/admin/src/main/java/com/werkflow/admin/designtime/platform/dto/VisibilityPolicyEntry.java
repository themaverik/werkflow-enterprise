package com.werkflow.admin.designtime.platform.dto;

import java.util.List;

/**
 * Tenant visibility policy response — governs cross-department artifact visibility for manager-tier groups.
 */
public record VisibilityPolicyEntry(
        String managerScope,
        List<String> managerTierGroups
) {}
