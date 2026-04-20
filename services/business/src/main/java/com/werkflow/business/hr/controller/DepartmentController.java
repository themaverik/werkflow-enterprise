package com.werkflow.business.hr.controller;

import com.werkflow.business.hr.dto.DepartmentRequest;
import com.werkflow.business.hr.dto.DepartmentResponse;
import com.werkflow.business.hr.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Department operations
 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department management APIs")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "Get all departments", description = "Retrieve a list of all departments")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID", description = "Retrieve a department by its ID")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get department by code", description = "Retrieve a department by its code")
    public ResponseEntity<DepartmentResponse> getDepartmentByCode(@PathVariable String code) {
        return ResponseEntity.ok(departmentService.getDepartmentByCode(code));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active departments", description = "Retrieve all active departments")
    public ResponseEntity<List<DepartmentResponse>> getActiveDepartments() {
        return ResponseEntity.ok(departmentService.getActiveDepartments());
    }

    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get departments by organization", description = "Retrieve all departments for an organization")
    public ResponseEntity<List<DepartmentResponse>> getDepartmentsByOrganization(@PathVariable Long organizationId) {
        return ResponseEntity.ok(departmentService.getDepartmentsByOrganization(organizationId));
    }

    @PostMapping
    @Operation(summary = "Create department", description = "Create a new department")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(departmentService.createDepartment(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update department", description = "Update an existing department")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete department", description = "Delete a department")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
