package com.werkflow.admin.designtime.platform.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Projection of the ERP department response used only for PSS department listing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpDepartmentResponse(
        Long id,
        String name,
        String code
) {}
