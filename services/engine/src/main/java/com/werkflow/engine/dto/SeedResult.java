package com.werkflow.engine.dto;

import java.util.List;

/**
 * Aggregate result of seeding example workflows for one tenant (ADR-031 Phase C).
 *
 * @param tenantId     the tenant that was seeded
 * @param tenantFolder the classpath folder resolved for this tenant ({@code default} when no
 *                     tenant-specific override exists)
 * @param workflows    per-workflow outcomes
 * @param deployed     count of workflows newly deployed in this run
 * @param skipped      count of workflows already present (idempotent skip)
 * @param failed       count of workflows that failed to deploy
 */
public record SeedResult(
        String tenantId,
        String tenantFolder,
        List<WorkflowSeedResult> workflows,
        int deployed,
        int skipped,
        int failed
) {
    public static SeedResult of(String tenantId, String tenantFolder, List<WorkflowSeedResult> workflows) {
        int d = 0, s = 0, f = 0;
        for (WorkflowSeedResult w : workflows) {
            switch (w.status()) {
                case "DEPLOYED" -> d++;
                case "SKIPPED"  -> s++;
                default         -> f++;
            }
        }
        return new SeedResult(tenantId, tenantFolder, List.copyOf(workflows), d, s, f);
    }
}
