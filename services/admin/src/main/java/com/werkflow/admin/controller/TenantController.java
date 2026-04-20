package com.werkflow.admin.controller;

import com.werkflow.admin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;

    @GetMapping("/{tenantCode}/cross-dept-threshold")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> getCrossDeptThreshold(@PathVariable String tenantCode) {
        return tenantRepository.findByTenantCode(tenantCode)
            .map(t -> ResponseEntity.ok(t.getCrossDeptDoaThreshold()))
            .orElse(ResponseEntity.ok(4));
    }
}
