package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantServiceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantServiceEndpointRepository extends JpaRepository<TenantServiceEndpoint, Long> {
    Optional<TenantServiceEndpoint> findByTenantCodeAndServiceKeyAndEnvironmentAndActiveTrue(
        String tenantCode, String serviceKey, String environment);
    List<TenantServiceEndpoint> findByTenantCodeAndActiveTrue(String tenantCode);
    List<TenantServiceEndpoint> findByTenantCodeAndConnectorKey(String tenantCode, String connectorKey);
    Optional<TenantServiceEndpoint> findByTenantCodeAndConnectorKeyAndEnvironment(
        String tenantCode, String connectorKey, String environment);
}
