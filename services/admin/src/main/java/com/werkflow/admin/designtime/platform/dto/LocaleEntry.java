package com.werkflow.admin.designtime.platform.dto;

/**
 * Tenant locale settings. Drives DMN cell display formatting and monetary field rendering.
 * Returned by GET /api/v1/design/platform/locale.
 */
public record LocaleEntry(
        String currencyCode,
        String locale,
        String numberFormat,
        String dateFormat,
        String timezone
) {
    /** Safe USD default for tenants with no LOCALE config var. */
    public static LocaleEntry defaultUsd() {
        return new LocaleEntry("USD", "en-US", "en-US", "MM/DD/YYYY", "UTC");
    }
}
