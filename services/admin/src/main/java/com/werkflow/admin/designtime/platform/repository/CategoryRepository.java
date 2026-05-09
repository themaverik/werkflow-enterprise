package com.werkflow.admin.designtime.platform.repository;

import com.werkflow.admin.designtime.platform.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence access for tenant-registered categories. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByTenantIdOrderByDisplayOrder(String tenantId);

    Optional<Category> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);

    long countByTenantId(String tenantId);
}
