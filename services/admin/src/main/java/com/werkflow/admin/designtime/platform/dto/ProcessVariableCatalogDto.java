package com.werkflow.admin.designtime.platform.dto;

import java.util.List;

/**
 * Static catalog of process variables automatically set by enterprise delegates.
 * Used by form designer variable-binding dropdowns and BPMN expression builders.
 */
public record ProcessVariableCatalogDto(
        List<VariableEntry> alwaysSet,
        List<VariableEntry> conditionallySet
) {

    public record VariableEntry(
            String name,
            String type,
            String description,
            String source,
            String availableWhen
    ) {}
}
