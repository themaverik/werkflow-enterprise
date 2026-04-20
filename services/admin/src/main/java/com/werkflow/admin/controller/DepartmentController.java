package com.werkflow.admin.controller;

import com.werkflow.admin.dto.DepartmentRequest;
import com.werkflow.admin.dto.DepartmentResponse;
import com.werkflow.admin.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DepartmentResponse>> list(@RequestParam String tenantCode) {
        return ResponseEntity.ok(departmentService.listByTenant(tenantCode));
    }

    @GetMapping("/codes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> listCodes(@RequestParam String tenantCode) {
        return ResponseEntity.ok(departmentService.getActiveCodes(tenantCode));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<DepartmentResponse> create(@RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.create(request));
    }
}
