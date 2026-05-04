package com.werkflow.admin.controller;

import com.werkflow.admin.dto.connector.ConnectorPathRequest;
import com.werkflow.admin.dto.connector.ConnectorPathResponse;
import com.werkflow.admin.security.JwtClaimsExtractor;
import com.werkflow.admin.service.ConnectorPathService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connectors/{connectorKey}/paths")
@RequiredArgsConstructor
public class ConnectorPathController {

    private final ConnectorPathService pathService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    private String resolveTenant(String tenantCode, Jwt jwt) {
        return (tenantCode != null && !tenantCode.isBlank()) ? tenantCode : jwtClaimsExtractor.getTenantId(jwt);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ConnectorPathResponse>> list(
            @PathVariable String connectorKey,
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(pathService.list(connectorKey, resolveTenant(tenantCode, jwt)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ConnectorPathResponse> upsert(
            @PathVariable String connectorKey,
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @Valid @RequestBody ConnectorPathRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(pathService.upsert(connectorKey, resolveTenant(tenantCode, jwt), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String connectorKey,
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String tenantCode,
            @AuthenticationPrincipal Jwt jwt) {
        pathService.delete(id, connectorKey, resolveTenant(tenantCode, jwt));
        return ResponseEntity.noContent().build();
    }
}
