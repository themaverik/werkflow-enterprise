package com.werkflow.admin.repository;

import com.werkflow.admin.entity.TenantCustodyMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantCustodyMappingRepository extends JpaRepository<TenantCustodyMapping, Long> {
    List<TenantCustodyMapping> findByTenantCodeOrderByCategoryKey(String tenantCode);
}
