package com.werkflow.admin.service;

import com.werkflow.admin.dto.DepartmentRequest;
import com.werkflow.admin.dto.DepartmentResponse;
import com.werkflow.admin.entity.Department;
import com.werkflow.admin.entity.Organization;
import com.werkflow.admin.repository.DepartmentRepository;
import com.werkflow.admin.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listByTenant(String tenantCode) {
        return departmentRepository.findByTenantCodeAndActiveTrue(tenantCode)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        Organization org = organizationRepository.findById(request.getOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found: " + request.getOrganizationId()));
        Department dept = new Department();
        dept.setName(request.getName());
        dept.setCode(request.getCode());
        dept.setDescription(request.getDescription());
        dept.setTenantCode(request.getTenantCode());
        dept.setOrganization(org);
        return toResponse(departmentRepository.save(dept));
    }

    @Transactional(readOnly = true)
    public List<String> getActiveCodes(String tenantCode) {
        return departmentRepository.findByTenantCodeAndActiveTrue(tenantCode)
            .stream().map(Department::getCode).filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toList());
    }

    private DepartmentResponse toResponse(Department d) {
        return DepartmentResponse.builder()
            .id(d.getId()).name(d.getName()).code(d.getCode())
            .description(d.getDescription()).tenantCode(d.getTenantCode())
            .organizationId(d.getOrganization().getId()).active(d.isActive())
            .build();
    }
}
