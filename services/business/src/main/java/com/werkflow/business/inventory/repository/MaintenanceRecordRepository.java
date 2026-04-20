package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for MaintenanceRecord entity
 */
@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    /**
     * Find maintenance records by asset instance
     */
    List<MaintenanceRecord> findByAssetInstanceOrderByScheduledDateDesc(AssetInstance assetInstance);

    /**
     * Find maintenance records by maintenance type
     */
    List<MaintenanceRecord> findByMaintenanceType(String maintenanceType);

    /**
     * Find maintenance records by scheduled date range
     */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.scheduledDate BETWEEN :startDate AND :endDate")
    List<MaintenanceRecord> findByScheduledDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find incomplete maintenance records
     */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.completedDate IS NULL")
    List<MaintenanceRecord> findIncompleteMaintenanceRecords();

    /**
     * Find overdue maintenance records
     */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.scheduledDate <= :currentDate AND m.completedDate IS NULL")
    List<MaintenanceRecord> findOverdueMaintenanceRecords(@Param("currentDate") LocalDate currentDate);

    /**
     * Find completed maintenance records
     */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.completedDate IS NOT NULL")
    List<MaintenanceRecord> findCompletedMaintenanceRecords();

    /**
     * Find maintenance records for an asset by type
     */
    List<MaintenanceRecord> findByAssetInstanceAndMaintenanceType(AssetInstance assetInstance, String maintenanceType);

    /**
     * Find maintenance records by performed by person
     */
    List<MaintenanceRecord> findByPerformedBy(String performedBy);

    /**
     * Find scheduled maintenance coming due
     */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.nextMaintenanceDate <= :dueDate AND m.completedDate IS NOT NULL")
    List<MaintenanceRecord> findScheduledMaintenanceDue(@Param("dueDate") LocalDate dueDate);

    /**
     * Find expensive maintenance records
     */
    @Query("SELECT m FROM MaintenanceRecord m WHERE m.cost >= :minCost")
    List<MaintenanceRecord> findExpensiveMaintenanceRecords(@Param("minCost") java.math.BigDecimal minCost);
}
