package com.werkflow.engine.dto;

import java.util.List;

/**
 * Per-workflow outcome from a single example seeding operation.
 *
 * @param processKey   BPMN process key (equals the filename without {@code .bpmn20.xml})
 * @param status       {@code DEPLOYED}, {@code SKIPPED} (already existed), or {@code FAILED}
 * @param newForms     form keys that were saved in this run (empty if all were pre-existing)
 * @param newDmns      DMN filenames that were deployed in this run (empty if all were pre-existing)
 * @param bpmnDeployed {@code true} if the BPMN was newly deployed in this run
 * @param error        non-null only when status is {@code FAILED}
 */
public record WorkflowSeedResult(
        String processKey,
        String status,
        List<String> newForms,
        List<String> newDmns,
        boolean bpmnDeployed,
        String error
) {
    public static WorkflowSeedResult skipped(String processKey) {
        return new WorkflowSeedResult(processKey, "SKIPPED", List.of(), List.of(), false, null);
    }

    public static WorkflowSeedResult deployed(String processKey, List<String> newForms, List<String> newDmns) {
        return new WorkflowSeedResult(processKey, "DEPLOYED", newForms, newDmns, true, null);
    }

    public static WorkflowSeedResult failed(String processKey, String error) {
        return new WorkflowSeedResult(processKey, "FAILED", List.of(), List.of(), false, error);
    }
}
