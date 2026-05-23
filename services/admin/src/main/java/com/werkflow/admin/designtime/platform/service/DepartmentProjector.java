package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.dto.DepartmentEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fetches ERP departments for the artifact metadata department picker (visibility scoping, not routing).
 * Per ADR-010: departments are purely a visibility filter, not a routing mechanism.
 *
 * <p>Per ADR-023: routes through the governed connector abstraction via {@link ErpMetadataReader},
 * never a bespoke HTTP client. Degrades to an empty list when ERP is unavailable or the
 * {@code hr-service} connector is unregistered.
 */
@Service
@RequiredArgsConstructor
public class DepartmentProjector {

    private static final String DEPARTMENTS_PATH = "/departments";

    private final ErpMetadataReader erpMetadataReader;

    /**
     * Returns tenant departments for the visibility-scope picker.
     * Empty list when the connector is unregistered or ERP is unavailable.
     */
    @Cacheable(value = CacheConfig.PSS_DEPARTMENTS, key = "#tenantCode")
    public List<DepartmentEntry> list(String tenantCode) {
        return erpMetadataReader.readCollection(tenantCode, DEPARTMENTS_PATH).stream()
                .filter(node -> {
                    String code = node.path("code").asText(null);
                    return code != null && !code.isBlank();
                })
                .map(node -> {
                    String code = node.path("code").asText();
                    return new DepartmentEntry(code, node.path("name").asText(code), 0);
                })
                .toList();
    }
}
