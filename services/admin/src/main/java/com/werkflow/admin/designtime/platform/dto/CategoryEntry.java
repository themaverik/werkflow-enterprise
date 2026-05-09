package com.werkflow.admin.designtime.platform.dto;

import java.util.UUID;

/**
 * A tenant-registered category used for artifact catalog grouping.
 */
public record CategoryEntry(
        UUID id,
        String code,
        String displayName,
        String icon,
        String color,
        int displayOrder,
        long artifactCount
) {}
