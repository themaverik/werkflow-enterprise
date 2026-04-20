package com.werkflow.engine.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoaThresholdRepository extends JpaRepository<DoaThreshold, Long> {

    Optional<DoaThreshold> findByTenantIdAndDoaLevel(String tenantId, String doaLevel);

    /**
     * Returns thresholds ordered numerically by DOA level (DOA_L1 < DOA_L2 < ... < DOA_L10).
     * Extracts the numeric suffix via CAST to avoid VARCHAR lexicographic ordering
     * (which would sort DOA_L10 before DOA_L2).
     */
    @Query("SELECT d FROM DoaThreshold d WHERE d.tenantId = :tenantId " +
           "ORDER BY CAST(SUBSTRING(d.doaLevel, 6) AS INTEGER)")
    List<DoaThreshold> findByTenantIdOrderByDoaLevel(@Param("tenantId") String tenantId);
}
