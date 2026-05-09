package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.client.ErpClient;
import com.werkflow.admin.designtime.platform.dto.DepartmentEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches ERP departments for the artifact metadata department picker (visibility scoping, not routing).
 * Per ADR-010: departments are purely a visibility filter, not a routing mechanism.
 */
@Service
@RequiredArgsConstructor
public class DepartmentProjector {

    private final ErpClient erpClient;

    /**
     * Returns tenant departments for the visibility-scope picker.
     * Empty list when ERP is unavailable (OSS mode).
     */
    @Cacheable(value = CacheConfig.PSS_DEPARTMENTS, key = "#tenantCode")
    public List<DepartmentEntry> list(String tenantCode) {
        return erpClient.getDepartments(tenantCode)
                .stream()
                .map(d -> new DepartmentEntry(d.code(), d.name(), 0))
                .collect(Collectors.toList());
    }
}
