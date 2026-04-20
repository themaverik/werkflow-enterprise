package com.werkflow.business.hr.repository;

import com.werkflow.business.hr.entity.Department;
import com.werkflow.business.hr.entity.DepartmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Department entity
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    Optional<Department> findByName(String name);

    List<Department> findByIsActive(Boolean isActive);

    List<Department> findByOrganizationId(Long organizationId);

    List<Department> findByOrganizationIdAndDepartmentType(Long organizationId, DepartmentType departmentType);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByCodeAndOrganizationId(String code, Long organizationId);

    boolean existsByNameAndOrganizationId(String name, Long organizationId);
}
