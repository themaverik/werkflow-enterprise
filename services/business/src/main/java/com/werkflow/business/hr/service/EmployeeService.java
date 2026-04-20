package com.werkflow.business.hr.service;

import com.werkflow.business.hr.dto.EmployeeRequest;
import com.werkflow.business.hr.dto.EmployeeResponse;
import com.werkflow.business.hr.entity.Department;
import com.werkflow.business.hr.entity.Employee;
import com.werkflow.business.hr.entity.EmploymentStatus;
import com.werkflow.business.hr.repository.DepartmentRepository;
import com.werkflow.business.hr.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Employee operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleDisplayService roleDisplayService;

    public List<EmployeeResponse> getAllEmployees() {
        log.debug("Fetching all employees");
        return employeeRepository.findAll().stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public EmployeeResponse getEmployeeById(Long id) {
        log.debug("Fetching employee by id: {}", id);
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + id));
        return convertToResponse(employee);
    }

    public EmployeeResponse getEmployeeByEmail(String email) {
        log.debug("Fetching employee by email: {}", email);
        Employee employee = employeeRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with email: " + email));
        return convertToResponse(employee);
    }

    public EmployeeResponse getEmployeeByKeycloakUserId(String keycloakUserId) {
        log.debug("Fetching employee by keycloakUserId: {}", keycloakUserId);
        Employee employee = employeeRepository.findByKeycloakUserId(keycloakUserId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Employee not found with keycloakUserId: " + keycloakUserId));
        return convertToResponse(employee);
    }

    public List<EmployeeResponse> getEmployeesByOrganization(Long orgId) {
        log.debug("Fetching employees for org: {}", orgId);
        return employeeRepository.findByOrganizationId(orgId).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public List<EmployeeResponse> getEmployeesByDepartment(Long departmentId) {
        log.debug("Fetching employees for department: {}", departmentId);
        return employeeRepository.findByDepartmentId(departmentId).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public List<EmployeeResponse> getEmployeesByDepartmentCode(String code) {
        log.debug("Fetching employees for department code: {}", code);
        return employeeRepository.findByDepartmentCode(code).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public List<EmployeeResponse> getEmployeesByStatus(EmploymentStatus status) {
        log.debug("Fetching employees by status: {}", status);
        return employeeRepository.findByEmploymentStatus(status).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    public List<EmployeeResponse> searchEmployees(String searchTerm) {
        log.debug("Searching employees with term: {}", searchTerm);
        return employeeRepository.searchEmployees(searchTerm).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        log.info("Creating new employee: {}", request.getEmail());

        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }
        if (request.getKeycloakUserId() != null
                && employeeRepository.existsByKeycloakUserId(request.getKeycloakUserId())) {
            throw new IllegalArgumentException(
                "Keycloak user already linked: " + request.getKeycloakUserId());
        }

        Employee employee = convertToEntity(request);
        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with id: {}", savedEmployee.getId());
        return convertToResponse(savedEmployee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        log.info("Updating employee with id: {}", id);

        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + id));

        if (!request.getEmail().equals(employee.getEmail())
                && employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        updateEntityFromRequest(employee, request);
        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Employee updated successfully: {}", id);
        return convertToResponse(updatedEmployee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        log.info("Deleting employee with id: {}", id);
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with id: " + id));
        employeeRepository.delete(employee);
        log.info("Employee deleted successfully: {}", id);
    }

    private Employee convertToEntity(EmployeeRequest request) {
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException(
                    "Department not found with id: " + request.getDepartmentId()));
        }

        validateDeptHeadUniqueness(request.getDepartmentCode(), request.getDoaLevel(), null);

        return Employee.builder()
            .organizationId(request.getOrganizationId())
            .keycloakUserId(request.getKeycloakUserId())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .gender(request.getGender())
            .profilePhotoUrl(request.getProfilePhotoUrl())
            .department(department)
            .departmentCode(request.getDepartmentCode())
            .doaLevel(request.getDoaLevel() != null ? request.getDoaLevel() : 0)
            .officeLocation(request.getOfficeLocation())
            .position(request.getPosition())
            .dateOfJoining(request.getDateOfJoining())
            .employmentStatus(request.getEmploymentStatus())
            .salary(request.getSalary())
            .build();
    }

    private void updateEntityFromRequest(Employee employee, EmployeeRequest request) {
        // Department head uniqueness: only check if doaLevel is changing to 2
        if (request.getDoaLevel() != null && request.getDoaLevel() == 2) {
            validateDeptHeadUniqueness(request.getDepartmentCode(), request.getDoaLevel(), employee.getId());
        }

        employee.setOrganizationId(request.getOrganizationId());
        employee.setKeycloakUserId(request.getKeycloakUserId());
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setPhone(request.getPhone());
        employee.setGender(request.getGender());
        employee.setProfilePhotoUrl(request.getProfilePhotoUrl());
        employee.setDepartmentCode(request.getDepartmentCode());
        employee.setDoaLevel(request.getDoaLevel() != null ? request.getDoaLevel() : 0);
        employee.setOfficeLocation(request.getOfficeLocation());
        employee.setPosition(request.getPosition());
        employee.setDateOfJoining(request.getDateOfJoining());
        employee.setEmploymentStatus(request.getEmploymentStatus());
        employee.setSalary(request.getSalary());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException(
                    "Department not found with id: " + request.getDepartmentId()));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }
    }

    /**
     * Enforces at most one department_head (doaLevel=2) per department code.
     * Pass currentEmployeeId to exclude the employee being updated from the check.
     */
    private void validateDeptHeadUniqueness(String departmentCode, Integer doaLevel, Long currentEmployeeId) {
        if (doaLevel != null && doaLevel == 2 && departmentCode != null) {
            boolean headExists = currentEmployeeId == null
                ? employeeRepository.existsByDepartmentCodeAndDoaLevel(departmentCode, 2)
                : employeeRepository.existsByDepartmentCodeAndDoaLevelAndIdNot(departmentCode, 2, currentEmployeeId);
            if (headExists) {
                throw new IllegalStateException(
                    "A department head already exists for department: " + departmentCode);
            }
        }
    }

    public Map<String, String> getRoleDisplay(Long id, List<String> keycloakRoles) {
        log.debug("Getting role display for employee {} with roles: {}", id, keycloakRoles);
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found: " + id));

        String displayRole = roleDisplayService.getDisplayRole(keycloakRoles, employee.getDepartmentCode());
        return Map.of(
            "displayRole", displayRole,
            "doaLevel", String.valueOf(employee.getDoaLevel())
        );
    }

    private EmployeeResponse convertToResponse(Employee employee) {
        String departmentName = employee.getDepartment() != null
            ? employee.getDepartment().getName()
            : null;

        return EmployeeResponse.builder()
            .id(employee.getId())
            .organizationId(employee.getOrganizationId())
            .keycloakUserId(employee.getKeycloakUserId())
            .firstName(employee.getFirstName())
            .lastName(employee.getLastName())
            .fullName(employee.getFullName())
            .email(employee.getEmail())
            .phone(employee.getPhone())
            .gender(employee.getGender())
            .profilePhotoUrl(employee.getProfilePhotoUrl())
            .departmentId(employee.getDepartmentId())
            .departmentName(departmentName)
            .departmentCode(employee.getDepartmentCode())
            .doaLevel(employee.getDoaLevel())
            .officeLocation(employee.getOfficeLocation())
            .position(employee.getPosition())
            .dateOfJoining(employee.getDateOfJoining())
            .employmentStatus(employee.getEmploymentStatus())
            .salary(employee.getSalary())
            .isActive(employee.getIsActive())
            .createdAt(employee.getCreatedAt())
            .updatedAt(employee.getUpdatedAt())
            .build();
    }
}
