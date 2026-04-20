package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.AssetInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AssetInstance entity
 */
@Repository
public interface AssetInstanceRepository extends JpaRepository<AssetInstance, Long> {

    /**
     * Find asset instance by asset tag (barcode/QR code)
     */
    Optional<AssetInstance> findByAssetTag(String assetTag);

    /**
     * Find asset instances by asset definition
     */
    List<AssetInstance> findByAssetDefinition(AssetDefinition assetDefinition);

    /**
     * Find asset instances by status
     */
    List<AssetInstance> findByStatus(String status);

    /**
     * Find asset instances by condition
     */
    List<AssetInstance> findByCondition(String condition);

    /**
     * Find asset instances by current location
     */
    List<AssetInstance> findByCurrentLocation(String location);

    /**
     * Find available assets (status = AVAILABLE)
     */
    @Query("SELECT a FROM AssetInstance a WHERE a.status = 'AVAILABLE'")
    List<AssetInstance> findAvailableAssets();

    /**
     * Find assets in use
     */
    @Query("SELECT a FROM AssetInstance a WHERE a.status = 'IN_USE'")
    List<AssetInstance> findAssetsInUse();

    /**
     * Find assets requiring maintenance
     */
    @Query("SELECT a FROM AssetInstance a WHERE a.status = 'MAINTENANCE'")
    List<AssetInstance> findAssetsRequiringMaintenance();

    /**
     * Find assets with warranty expiring soon
     */
    @Query("SELECT a FROM AssetInstance a WHERE a.warrantyExpiryDate <= :expiryDate AND a.status != 'DISPOSED' AND a.status != 'RETIRED'")
    List<AssetInstance> findAssetsWithExpiringWarranty(@Param("expiryDate") LocalDate expiryDate);

    /**
     * Find assets by asset definition and status
     */
    List<AssetInstance> findByAssetDefinitionAndStatus(AssetDefinition assetDefinition, String status);

    /**
     * Search assets by asset tag or serial number
     */
    @Query("SELECT a FROM AssetInstance a WHERE " +
           "LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<AssetInstance> searchAssets(@Param("searchTerm") String searchTerm);

    /**
     * Count assets by status
     */
    long countByStatus(String status);

    /**
     * Count assets by asset definition ID and status
     */
    long countByAssetDefinitionIdAndStatus(Long assetDefinitionId, String status);

    /**
     * Find assets in poor or damaged condition
     */
    @Query("SELECT a FROM AssetInstance a WHERE a.condition IN ('POOR', 'DAMAGED', 'NEEDS_REPAIR')")
    List<AssetInstance> findAssetsNeedingAttention();
}
