package com.werkflow.business.hr.controller;

import com.werkflow.business.hr.dto.AttendanceRequest;
import com.werkflow.business.hr.dto.AttendanceResponse;
import com.werkflow.business.hr.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/attendances")
@RequiredArgsConstructor
@Tag(name = "Attendances", description = "Attendance management APIs")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    @Operation(summary = "Get all attendances", description = "Retrieve all attendance records")
    public ResponseEntity<List<AttendanceResponse>> getAllAttendances() {
        return ResponseEntity.ok(attendanceService.getAllAttendances());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get attendance by ID", description = "Retrieve an attendance record by ID")
    public ResponseEntity<AttendanceResponse> getAttendanceById(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.getAttendanceById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get attendances by employee", description = "Retrieve all attendances for an employee")
    public ResponseEntity<List<AttendanceResponse>> getAttendancesByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(attendanceService.getAttendancesByEmployee(employeeId));
    }

    @GetMapping("/employee/{employeeId}/range")
    @Operation(summary = "Get attendances by date range", description = "Retrieve attendances for an employee within a date range")
    public ResponseEntity<List<AttendanceResponse>> getAttendancesByDateRange(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(attendanceService.getAttendancesByDateRange(employeeId, startDate, endDate));
    }

    @PostMapping
    @Operation(summary = "Create attendance", description = "Create a new attendance record")
    public ResponseEntity<AttendanceResponse> createAttendance(@Valid @RequestBody AttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(attendanceService.createAttendance(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update attendance", description = "Update an attendance record")
    public ResponseEntity<AttendanceResponse> updateAttendance(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.updateAttendance(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete attendance", description = "Delete an attendance record")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.noContent().build();
    }
}
