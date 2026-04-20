package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.CustodyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for CustodyRecord entity
 */
@Repository
public interface CustodyRecordRepository extends JpaRepository<CustodyRecord, Long> {

    /**
     * Find current custody record for an asset (active custody, no end date)
     */
    @Query("SELECT c FROM CustodyRecord c WHERE c.assetInstance = :assetInstance AND c.endDate IS NULL")
    Optional<CustodyRecord> findCurrentCustody(@Param("assetInstance") AssetInstance assetInstance);

    /**
     * Find custody history for an asset
     */
    List<CustodyRecord> findByAssetInstanceOrderByStartDateDesc(AssetInstance assetInstance);

    /**
     * Find custody records by custodian department
     */
    List<CustodyRecord> findByCustodianDeptId(Long deptId);

    /**
     * Find custody records by custodian user
     */
    List<CustodyRecord> findByCustodianUserId(Long userId);

    /**
     * Find custody records by custody type
     */
    List<CustodyRecord> findByCustodyType(String custodyType);

    /**
     * Find current custody records (active, no end date)
     */
    @Query("SELECT c FROM CustodyRecord c WHERE c.endDate IS NULL")
    List<CustodyRecord> findActiveCustodyRecords();

    /**
     * Find temporary custody records that are past their end date
     */
    @Query("SELECT c FROM CustodyRecord c WHERE c.custodyType = 'TEMPORARY' AND c.endDate <= :currentDate AND c.endDate IS NOT NULL")
    List<CustodyRecord> findOverdueTemporaryCustody(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find custody records by asset instance and custody type
     */
    List<CustodyRecord> findByAssetInstanceAndCustodyType(AssetInstance assetInstance, String custodyType);

    /**
     * Find custody records for specific department and user
     */
    List<CustodyRecord> findByCustodianDeptIdAndCustodianUserId(Long deptId, Long userId);

    /**
     * Find all custody records for a department
     */
    @Query("SELECT c FROM CustodyRecord c WHERE c.custodianDeptId = :deptId AND c.endDate IS NULL")
    List<CustodyRecord> findActiveCustodyByDepartment(@Param("deptId") Long deptId);
}
