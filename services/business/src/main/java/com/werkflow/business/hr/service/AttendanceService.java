package com.werkflow.business.hr.service;

import com.werkflow.business.hr.dto.AttendanceRequest;
import com.werkflow.business.hr.dto.AttendanceResponse;
import com.werkflow.business.hr.entity.Attendance;
import com.werkflow.business.hr.entity.Employee;
import com.werkflow.business.hr.repository.AttendanceRepository;
import com.werkflow.business.hr.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    public List<AttendanceResponse> getAllAttendances() {
        return attendanceRepository.findAll().stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public AttendanceResponse getAttendanceById(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Attendance not found"));
        return convertToResponse(attendance);
    }

    public List<AttendanceResponse> getAttendancesByEmployee(Long employeeId) {
        return attendanceRepository.findByEmployeeId(employeeId).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public List<AttendanceResponse> getAttendancesByDateRange(Long employeeId,
                                                              LocalDate startDate,
                                                              LocalDate endDate) {
        return attendanceRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceResponse createAttendance(AttendanceRequest request) {
        log.info("Creating attendance for employee: {}", request.getEmployeeId());

        Employee employee = employeeRepository.findById(request.getEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        // Check if attendance already exists
        if (attendanceRepository.existsByEmployeeIdAndAttendanceDate(
            request.getEmployeeId(), request.getAttendanceDate())) {
            throw new IllegalStateException("Attendance already exists for this date");
        }

        Attendance attendance = Attendance.builder()
            .employee(employee)
            .attendanceDate(request.getAttendanceDate())
            .checkInTime(request.getCheckInTime())
            .checkOutTime(request.getCheckOutTime())
            .status(request.getStatus())
            .remarks(request.getRemarks())
            .build();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Attendance created with id: {}", saved.getId());
        return convertToResponse(saved);
    }

    @Transactional
    public AttendanceResponse updateAttendance(Long id, AttendanceRequest request) {
        Attendance attendance = attendanceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Attendance not found"));

        attendance.setCheckInTime(request.getCheckInTime());
        attendance.setCheckOutTime(request.getCheckOutTime());
        attendance.setStatus(request.getStatus());
        attendance.setRemarks(request.getRemarks());

        return convertToResponse(attendanceRepository.save(attendance));
    }

    @Transactional
    public void deleteAttendance(Long id) {
        attendanceRepository.deleteById(id);
    }

    private AttendanceResponse convertToResponse(Attendance attendance) {
        return AttendanceResponse.builder()
            .id(attendance.getId())
            .employeeId(attendance.getEmployee().getId())
            .employeeName(attendance.getEmployee().getFullName())
            .attendanceDate(attendance.getAttendanceDate())
            .checkInTime(attendance.getCheckInTime())
            .checkOutTime(attendance.getCheckOutTime())
            .status(attendance.getStatus())
            .remarks(attendance.getRemarks())
            .workedHours(attendance.getWorkedHours())
            .createdAt(attendance.getCreatedAt())
            .updatedAt(attendance.getUpdatedAt())
            .build();
    }
}
