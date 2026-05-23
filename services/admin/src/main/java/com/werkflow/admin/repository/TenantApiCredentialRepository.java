package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantApiCredentialRepository extends JpaRepository<TenantApiCredential, Long> {
    Optional<TenantApiCredential> findByTenantCodeAndCredentialKeyAndRevokedAtIsNull(
        String tenantCode, String credentialKey);
    List<TenantApiCredential> findByTenantCodeAndRevokedAtIsNull(String tenantCode);
    Optional<TenantApiCredential> findByTenantCodeAndConnectorKey(String tenantCode, String connectorKey);

    List<TenantApiCredential> findByTenantCodeAndCredentialRef(String tenantCode, String credentialRef);
}
