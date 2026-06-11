package com.werkflow.admin.dto;

public record EngineSeedResult(
    String tenantId,
    String tenantFolder,
    int deployed,
    int skipped,
    int failed
) {}
