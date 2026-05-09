package com.werkflow.admin.designtime.platform.dto;

/**
 * A tenant ERP department, used by the artifact metadata department picker (visibility scoping only).
 * Not used for routing — per ADR-010.
 */
public record DepartmentEntry(
        String code,
        String displayName,
        int memberCount
) {}
