package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantDatasource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantDatasourceRepository extends JpaRepository<TenantDatasource, UUID> {

    List<TenantDatasource> findByTenantId(String tenantId);

    Optional<TenantDatasource> findByTenantIdAndRef(String tenantId, String ref);

    boolean existsByTenantIdAndRef(String tenantId, String ref);
}
