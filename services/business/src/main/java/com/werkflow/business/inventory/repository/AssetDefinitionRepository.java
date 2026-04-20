package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetCategory;
import com.werkflow.business.inventory.entity.AssetDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AssetDefinition entity
 */
@Repository
public interface AssetDefinitionRepository extends JpaRepository<AssetDefinition, Long> {

    /**
     * Find asset definition by SKU
     */
    Optional<AssetDefinition> findBySku(String sku);

    /**
     * Find asset definitions by category
     */
    List<AssetDefinition> findByCategory(AssetCategory category);

    /**
     * Find asset definitions by category ID
     */
    List<AssetDefinition> findByCategoryIdAndActiveTrue(Long categoryId);

    /**
     * Find all active asset definitions
     */
    List<AssetDefinition> findByActiveTrue();

    /**
     * Find asset definitions requiring maintenance
     */
    List<AssetDefinition> findByRequiresMaintenanceTrueAndActiveTrue();

    /**
     * Find asset definitions by manufacturer
     */
    List<AssetDefinition> findByManufacturerAndActiveTrue(String manufacturer);

    /**
     * Find asset definitions by price range
     */
    @Query("SELECT a FROM AssetDefinition a WHERE a.unitCost >= :minPrice AND a.unitCost <= :maxPrice AND a.active = true")
    List<AssetDefinition> findByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Search asset definitions by name or SKU
     */
    @Query("SELECT a FROM AssetDefinition a WHERE " +
           "(LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a.sku) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND a.active = true")
    List<AssetDefinition> searchDefinitions(@Param("searchTerm") String searchTerm);

    /**
     * Count assets by category
     */
    long countByCategory(AssetCategory category);
}
