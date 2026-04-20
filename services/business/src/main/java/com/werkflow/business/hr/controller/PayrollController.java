package com.werkflow.business.hr.controller;

import com.werkflow.business.hr.dto.PayrollRequest;
import com.werkflow.business.hr.dto.PayrollResponse;
import com.werkflow.business.hr.service.PayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payrolls")
@RequiredArgsConstructor
@Tag(name = "Payrolls", description = "Payroll management APIs")
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping
    @Operation(summary = "Get all payrolls", description = "Retrieve all payroll records")
    public ResponseEntity<List<PayrollResponse>> getAllPayrolls() {
        return ResponseEntity.ok(payrollService.getAllPayrolls());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payroll by ID", description = "Retrieve a payroll record by ID")
    public ResponseEntity<PayrollResponse> getPayrollById(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getPayrollById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get payrolls by employee", description = "Retrieve all payroll records for an employee")
    public ResponseEntity<List<PayrollResponse>> getPayrollsByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(payrollService.getPayrollsByEmployee(employeeId));
    }

    @GetMapping("/period")
    @Operation(summary = "Get payrolls by period", description = "Retrieve payrolls for a specific month and year")
    public ResponseEntity<List<PayrollResponse>> getPayrollsByMonthYear(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        return ResponseEntity.ok(payrollService.getPayrollsByMonthYear(month, year));
    }

    @PostMapping
    @Operation(summary = "Create payroll", description = "Create a new payroll record")
    public ResponseEntity<PayrollResponse> createPayroll(@Valid @RequestBody PayrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(payrollService.createPayroll(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update payroll", description = "Update a payroll record")
    public ResponseEntity<PayrollResponse> updatePayroll(
            @PathVariable Long id,
            @Valid @RequestBody PayrollRequest request) {
        return ResponseEntity.ok(payrollService.updatePayroll(id, request));
    }

    @PutMapping("/{id}/mark-paid")
    @Operation(summary = "Mark payroll as paid", description = "Mark a payroll record as paid")
    public ResponseEntity<PayrollResponse> markAsPaid(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.markAsPaid(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete payroll", description = "Delete a payroll record")
    public ResponseEntity<Void> deletePayroll(@PathVariable Long id) {
        payrollService.deletePayroll(id);
        return ResponseEntity.noContent().build();
    }
}
