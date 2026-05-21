package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TenantCredential} metadata rows.
 * Secret material lives in OpenBao; this repo never touches it.
 */
@Repository
public interface TenantCredentialRepository extends JpaRepository<TenantCredential, UUID> {

    List<TenantCredential> findByTenantId(String tenantId);

    List<TenantCredential> findByTenantIdAndCredentialType(String tenantId, String credentialType);

    Optional<TenantCredential> findByTenantIdAndCredentialTypeAndLabel(
        String tenantId, String credentialType, String label);

    boolean existsByTenantIdAndCredentialTypeAndLabel(
        String tenantId, String credentialType, String label);
}
