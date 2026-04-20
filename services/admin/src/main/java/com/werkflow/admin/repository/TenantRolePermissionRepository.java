package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantRolePermissionRepository extends JpaRepository<TenantRolePermission, Long> {
    List<TenantRolePermission> findByTenantCodeAndRoleNameIn(String tenantCode, List<String> roleNames);
}
