package com.werkflow.admin.repository;

import com.werkflow.admin.entity.RoleGroupMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleGroupMappingRepository extends JpaRepository<RoleGroupMapping, Long> {

    List<RoleGroupMapping> findByTenantCodeOrderByRoleName(String tenantCode);

    boolean existsByTenantCodeAndRoleNameAndGroupName(String tenantCode, String roleName, String groupName);
}
