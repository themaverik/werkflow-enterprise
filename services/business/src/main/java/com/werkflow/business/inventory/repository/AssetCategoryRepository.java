package com.werkflow.business.inventory.repository;

import com.werkflow.business.inventory.entity.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AssetCategory entity
 */
@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {

    /**
     * Find category by code
     */
    Optional<AssetCategory> findByCode(String code);

    /**
     * Find category by name
     */
    Optional<AssetCategory> findByName(String name);

    /**
     * Find all active categories
     */
    List<AssetCategory> findByActiveTrue();

    /**
     * Find all inactive categories
     */
    List<AssetCategory> findByActiveFalse();

    /**
     * Find root categories (no parent)
     */
    @Query("SELECT c FROM AssetCategory c WHERE c.parentCategory IS NULL AND c.active = true")
    List<AssetCategory> findRootCategories();

    /**
     * Find all root categories (no parent), including inactive
     */
    List<AssetCategory> findByParentCategoryIsNull();

    /**
     * Find all active subcategories (those with a parent)
     */
    List<AssetCategory> findByParentCategoryIsNotNullAndActiveTrue();

    /**
     * Find child categories by parent code
     */
    List<AssetCategory> findByParentCategoryCode(String parentCode);

    /**
     * Find child categories by parent ID
     */
    List<AssetCategory> findByParentCategoryIdAndActiveTrue(Long parentCategoryId);

    /**
     * Find categories by custodian department code
     */
    List<AssetCategory> findByCustodianDeptCode(String custodianDeptCode);

    /**
     * Find categories requiring approval
     */
    List<AssetCategory> findByRequiresApprovalTrueAndActiveTrue();

    /**
     * Search categories by name or code
     */
    @Query("SELECT c FROM AssetCategory c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.code) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND c.active = true")
    List<AssetCategory> searchCategories(@Param("searchTerm") String searchTerm);
}
