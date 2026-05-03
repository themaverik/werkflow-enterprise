package com.werkflow.admin.controller;

import com.werkflow.admin.dto.connector.ConnectorPathRequest;
import com.werkflow.admin.dto.connector.ConnectorPathResponse;
import com.werkflow.admin.service.ConnectorPathService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connectors/{connectorKey}/paths")
@RequiredArgsConstructor
public class ConnectorPathController {

    private final ConnectorPathService pathService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ConnectorPathResponse>> list(
            @PathVariable String connectorKey,
            @RequestParam String tenantCode) {
        return ResponseEntity.ok(pathService.list(connectorKey, tenantCode));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ConnectorPathResponse> upsert(
            @PathVariable String connectorKey,
            @RequestParam String tenantCode,
            @Valid @RequestBody ConnectorPathRequest request) {
        return ResponseEntity.ok(pathService.upsert(connectorKey, tenantCode, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String connectorKey,
            @PathVariable Long id,
            @RequestParam String tenantCode) {
        pathService.delete(id, connectorKey, tenantCode);
        return ResponseEntity.noContent().build();
    }
}
