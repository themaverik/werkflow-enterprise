package com.werkflow.admin.designtime.platform.dto;

/**
 * A single process definition visible to the requesting user, per ADR-010 §3.
 *
 * @param processKey     the stable BPMN process key (unique across the engine)
 * @param name           human-readable display name; may be null for un-named drafts
 * @param departmentCode the owning department, or null when the process is globally visible
 */
public record VisibleProcessEntry(
        String processKey,
        String name,
        String departmentCode
) {}
