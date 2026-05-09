package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.designtime.platform.dto.ProcessVariableCatalogDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Static registry of process variables automatically set by enterprise delegates.
 * Used by the form designer variable-binding dropdown and BPMN expression builder.
 * Static because these are defined by the platform, not tenant configuration.
 */
@Service
public class ProcessVariableCatalog {

    private static final ProcessVariableCatalogDto CATALOG = new ProcessVariableCatalogDto(
            List.of(
                    new ProcessVariableCatalogDto.VariableEntry(
                            "owningDepartment", "string",
                            "Submitter's department (set by SetOwningDepartmentDelegate); " +
                            "used for attribution and visibility, NOT routing (ADR-010)",
                            "ERP user profile", "erpConnected"),
                    new ProcessVariableCatalogDto.VariableEntry(
                            "tenantId", "string", "Tenant identifier", "engine", null),
                    new ProcessVariableCatalogDto.VariableEntry(
                            "submitterId", "string", "Submitter Keycloak user ID", "JWT", null),
                    new ProcessVariableCatalogDto.VariableEntry(
                            "submittedAt", "date and time", "Process submission timestamp", "engine", null)
            ),
            List.of(
                    new ProcessVariableCatalogDto.VariableEntry(
                            "targetDepartment", "string",
                            "Receiving department for transfer workflows", "form input", null),
                    new ProcessVariableCatalogDto.VariableEntry(
                            "assignedGroup", "string",
                            "Result of upstream DMN custody/DOA routing — comma-separated candidate groups",
                            "DMN output", null)
            )
    );

    /** Returns the static platform variable catalog. */
    public ProcessVariableCatalogDto getCatalog() {
        return CATALOG;
    }
}
