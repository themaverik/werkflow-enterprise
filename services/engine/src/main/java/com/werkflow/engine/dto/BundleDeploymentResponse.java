package com.werkflow.engine.dto;

import java.util.List;

/**
 * Result of a bundle deployment (ADR-026 Phase 1): the deployed process plus the
 * bundle identity and which referenced decisions were pinned into the bundle.
 *
 * @param process          the deployed process definition
 * @param processKey       the process key the bundle is keyed on
 * @param bundleVersion    monotonic bundle version for (tenant, processKey)
 * @param parentDeploymentId the shared parent deployment id linking the artifacts
 * @param bundledDecisions decisionRefs successfully deployed into the bundle
 * @param unbundledDecisions referenced decisionRefs that could not be resolved
 *        (they will resolve to latest at runtime)
 */
public record BundleDeploymentResponse(
        ProcessDefinitionResponse process,
        String processKey,
        int bundleVersion,
        String parentDeploymentId,
        List<String> bundledDecisions,
        List<String> unbundledDecisions) {
}
