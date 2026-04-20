package com.werkflow.business.hr.service;

import com.werkflow.business.hr.dto.DepartmentRequest;
import com.werkflow.business.hr.dto.DepartmentResponse;
import com.werkflow.business.hr.entity.Department;
import com.werkflow.business.hr.repository.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Department operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<DepartmentResponse> getAllDepartments() {
        log.debug("Fetching all departments");
        return departmentRepository.findAll().stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public DepartmentResponse getDepartmentById(Long id) {
        log.debug("Fetching department by id: {}", id);
        Department department = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));
        return convertToResponse(department);
    }

    public DepartmentResponse getDepartmentByCode(String code) {
        log.debug("Fetching department by code: {}", code);
        Department department = departmentRepository.findByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Department not found with code: " + code));
        return convertToResponse(department);
    }

    public List<DepartmentResponse> getActiveDepartments() {
        log.debug("Fetching active departments");
        return departmentRepository.findByIsActive(true).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public List<DepartmentResponse> getDepartmentsByOrganization(Long organizationId) {
        log.debug("Fetching departments for organization: {}", organizationId);
        return departmentRepository.findByOrganizationId(organizationId).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        log.info("Creating new department: {}", request.getName());

        if (departmentRepository.existsByCodeAndOrganizationId(request.getCode(), request.getOrganizationId())) {
            throw new IllegalArgumentException("Department code already exists in this organization: " + request.getCode());
        }
        if (departmentRepository.existsByNameAndOrganizationId(request.getName(), request.getOrganizationId())) {
            throw new IllegalArgumentException("Department name already exists in this organization: " + request.getName());
        }

        Department department = convertToEntity(request);
        Department savedDepartment = departmentRepository.save(department);
        log.info("Department created successfully with id: {}", savedDepartment.getId());
        return convertToResponse(savedDepartment);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        log.info("Updating department with id: {}", id);

        Department department = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));

        if (!request.getCode().equals(department.getCode())
            && departmentRepository.existsByCodeAndOrganizationId(request.getCode(), request.getOrganizationId())) {
            throw new IllegalArgumentException("Department code already exists in this organization: " + request.getCode());
        }
        if (!request.getName().equals(department.getName())
            && departmentRepository.existsByNameAndOrganizationId(request.getName(), request.getOrganizationId())) {
            throw new IllegalArgumentException("Department name already exists in this organization: " + request.getName());
        }

        updateEntityFromRequest(department, request);
        Department updatedDepartment = departmentRepository.save(department);
        log.info("Department updated successfully: {}", id);
        return convertToResponse(updatedDepartment);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        log.info("Deactivating department with id: {}", id);
        Department department = departmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));
        department.setIsActive(false);
        departmentRepository.save(department);
        log.info("Department deactivated successfully: {}", id);
    }

    private Department convertToEntity(DepartmentRequest request) {
        return Department.builder()
            .name(request.getName())
            .code(request.getCode())
            .organizationId(request.getOrganizationId())
            .departmentType(request.getDepartmentType())
            .leadUserId(request.getLeadUserId())
            .officeLocation(request.getOfficeLocation())
            .departmentEmail(request.getDepartmentEmail())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();
    }

    private void updateEntityFromRequest(Department department, DepartmentRequest request) {
        department.setName(request.getName());
        department.setCode(request.getCode());
        department.setOrganizationId(request.getOrganizationId());
        department.setDepartmentType(request.getDepartmentType());
        department.setLeadUserId(request.getLeadUserId());
        department.setOfficeLocation(request.getOfficeLocation());
        department.setDepartmentEmail(request.getDepartmentEmail());
        department.setIsActive(request.getIsActive());
    }

    private DepartmentResponse convertToResponse(Department department) {
        return DepartmentResponse.builder()
            .id(department.getId())
            .name(department.getName())
            .code(department.getCode())
            .organizationId(department.getOrganizationId())
            .departmentType(department.getDepartmentType())
            .leadUserId(department.getLeadUserId())
            .officeLocation(department.getOfficeLocation())
            .departmentEmail(department.getDepartmentEmail())
            .isActive(department.getIsActive())
            .createdAt(department.getCreatedAt())
            .updatedAt(department.getUpdatedAt())
            .build();
    }
}
