package com.werkflow.engine.controller;

import com.werkflow.engine.workflow.DoaThreshold;
import com.werkflow.engine.workflow.DoaThresholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doa-thresholds")
@RequiredArgsConstructor
@Tag(name = "DoA Thresholds", description = "Delegation of Authority threshold configuration")
@SecurityRequirement(name = "bearer-jwt")
public class DoaThresholdController {

    private final DoaThresholdService doaThresholdService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get DoA thresholds for a tenant")
    public ResponseEntity<List<DoaThreshold>> getDoaThresholds(
        @RequestParam(defaultValue = "default") String tenantId
    ) {
        return ResponseEntity.ok(doaThresholdService.getThresholds(tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update a DoA threshold")
    public ResponseEntity<DoaThreshold> updateDoaThreshold(
        @PathVariable Long id,
        @RequestBody DoaThreshold patch
    ) {
        return ResponseEntity.ok(doaThresholdService.updateThreshold(id, patch));
    }
}
