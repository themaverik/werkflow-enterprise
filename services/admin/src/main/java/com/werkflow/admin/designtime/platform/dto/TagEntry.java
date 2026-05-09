package com.werkflow.admin.designtime.platform.dto;

/**
 * A previously-used tag with its usage count, used for autocomplete in artifact metadata panels.
 */
public record TagEntry(
        String tag,
        long usageCount
) {}
