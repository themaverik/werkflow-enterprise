package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantConnectorPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantConnectorPathRepository extends JpaRepository<TenantConnectorPath, Long> {

    List<TenantConnectorPath> findByConnectorKeyAndTenantCode(String connectorKey, String tenantCode);

    Optional<TenantConnectorPath> findByConnectorKeyAndTenantCodeAndPathAndHttpMethod(
            String connectorKey, String tenantCode, String path, String httpMethod);

    void deleteByConnectorKeyAndTenantCode(String connectorKey, String tenantCode);
}
