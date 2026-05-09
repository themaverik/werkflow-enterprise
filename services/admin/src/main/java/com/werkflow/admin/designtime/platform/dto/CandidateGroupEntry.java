package com.werkflow.admin.designtime.platform.dto;

import java.util.List;

/**
 * A single candidate group entry returned by the unified candidate-groups endpoint.
 * Groups the kind discriminator and tier for BPMN designer rendering.
 */
public record CandidateGroupEntry(
        String key,
        String label,
        String kind,
        int tier,
        boolean readOnly,
        boolean isManagerTier,
        List<String> mappedFromRoles
) {}
