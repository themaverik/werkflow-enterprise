package com.werkflow.admin.repository;

import com.werkflow.admin.entity.ConfigurationVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationVariableRepository extends JpaRepository<ConfigurationVariable, Long> {

    List<ConfigurationVariable> findByTenantCodeOrderByVarKey(String tenantCode);

    Optional<ConfigurationVariable> findByTenantCodeAndVarKey(String tenantCode, String varKey);

    boolean existsByTenantCodeAndVarKey(String tenantCode, String varKey);
}
