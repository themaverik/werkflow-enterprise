package com.werkflow.admin.controller;

import com.werkflow.admin.dto.ConfigVarRequest;
import com.werkflow.admin.dto.ConfigVarResponse;
import com.werkflow.admin.service.ConfigurationVariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/vars")
@RequiredArgsConstructor
public class ConfigurationVariableController {

    private final ConfigurationVariableService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConfigVarResponse>> list(@RequestParam String tenantCode) {
        return ResponseEntity.ok(service.listByTenant(tenantCode));
    }

    /** Returns key→value map for FEEL context injection (internal use by engine). */
    @GetMapping("/map")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> varMap(@RequestParam String tenantCode) {
        return ResponseEntity.ok(service.getVarMap(tenantCode));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ConfigVarResponse> create(@Valid @RequestBody ConfigVarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ConfigVarResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ConfigVarRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
