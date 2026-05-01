package com.werkflow.engine.controller;

import com.werkflow.engine.action.TenantEndpointResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/internal/cache")
@RequiredArgsConstructor
public class CacheController {

    private final TenantEndpointResolver endpointResolver;

    /**
     * Evicts a single connector endpoint from the resolver cache.
     * Called by the admin service when a TenantServiceEndpoint is updated or deactivated.
     *
     * POST /internal/cache/endpoints/invalidate?tenantCode=X&connectorKey=Y
     */
    @PostMapping("/endpoints/invalidate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> invalidateEndpoint(
            @RequestParam String tenantCode,
            @RequestParam String connectorKey) {
        endpointResolver.invalidate(tenantCode, connectorKey);
        return ResponseEntity.noContent().build();
    }
}
