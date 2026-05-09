package com.werkflow.admin.designtime.bpmn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response DTO for {@code GET /api/v1/design/bpmn/processes/{processDefId}/variables-at/{activityId}}.
 *
 * <p>Contains the accumulated process variables reachable at the given activity,
 * with provenance indicating which prior task or gateway set each variable.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VariableAtActivityResponse(List<ProcessVariableEntry> variables) {

    /**
     * A single process variable entry with its name, inferred type, and provenance.
     *
     * @param name          variable name as it appears in BPMN expressions
     * @param type          inferred JSON Schema type (string, number, boolean, object, array) or null if unknown
     * @param setByActivity BPMN activity ID of the task that writes this variable
     * @param setByTask     human-readable task name for display in the designer
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProcessVariableEntry(
            String name,
            String type,
            String setByActivity,
            String setByTask
    ) {}
}
