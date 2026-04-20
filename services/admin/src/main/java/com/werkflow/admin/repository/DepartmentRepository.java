package com.werkflow.admin.repository;

import com.werkflow.admin.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByTenantCodeAndActiveTrue(String tenantCode);
    Optional<Department> findByTenantCodeAndCode(String tenantCode, String code);
}
