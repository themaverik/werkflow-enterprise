package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.dto.AssetCategoryResponseDto;
import com.werkflow.business.inventory.entity.AssetCategory;
import com.werkflow.business.inventory.repository.AssetCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

/**
 * Service for AssetCategory operations
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssetCategoryService {

    private final AssetCategoryRepository categoryRepository;

    /**
     * Create a new asset category
     */
    public AssetCategory createCategory(AssetCategory category) {
        log.info("Creating new asset category: {}", category.getName());

        if (category.getCode() != null) {
            if (categoryRepository.findByCode(category.getCode()).isPresent()) {
                throw new IllegalArgumentException("Category code already exists: " + category.getCode());
            }
        }

        AssetCategory saved = categoryRepository.save(category);
        log.info("Asset category created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Get asset category by ID
     */
    @Transactional(readOnly = true)
    public AssetCategory getCategoryById(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Asset category not found with id: " + id));
    }

    /**
     * Get asset category by code
     */
    @Transactional(readOnly = true)
    public AssetCategory getCategoryByCode(String code) {
        return categoryRepository.findByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Asset category not found with code: " + code));
    }

    /**
     * Get all active categories
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> getActiveCategories() {
        return categoryRepository.findByActiveTrue();
    }

    /**
     * Get active subcategories (leaf categories that have a parent)
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> getActiveSubcategories() {
        return categoryRepository.findByParentCategoryIsNotNullAndActiveTrue();
    }

    /**
     * Get all categories
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    /**
     * Get root categories
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> getRootCategories() {
        return categoryRepository.findRootCategories();
    }

    /**
     * Get child categories
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> getChildCategories(Long parentId) {
        return categoryRepository.findByParentCategoryIdAndActiveTrue(parentId);
    }

    /**
     * Update asset category
     */
    public AssetCategory updateCategory(Long id, AssetCategory categoryDetails) {
        log.info("Updating asset category with id: {}", id);

        AssetCategory category = getCategoryById(id);

        category.setName(categoryDetails.getName());
        category.setCode(categoryDetails.getCode());
        category.setDescription(categoryDetails.getDescription());
        category.setRequiresApproval(categoryDetails.getRequiresApproval());
        category.setActive(categoryDetails.getActive());
        category.setCustodianDeptCode(categoryDetails.getCustodianDeptCode());
        category.setCustodianUserId(categoryDetails.getCustodianUserId());

        AssetCategory updated = categoryRepository.save(category);
        log.info("Asset category updated with id: {}", id);
        return updated;
    }

    /**
     * Deactivate category
     */
    public AssetCategory deactivateCategory(Long id) {
        log.info("Deactivating asset category with id: {}", id);

        AssetCategory category = getCategoryById(id);
        category.setActive(false);

        return categoryRepository.save(category);
    }

    /**
     * Activate category
     */
    public AssetCategory activateCategory(Long id) {
        log.info("Activating asset category with id: {}", id);

        AssetCategory category = getCategoryById(id);
        category.setActive(true);

        return categoryRepository.save(category);
    }

    /**
     * Delete category (soft-delete via deactivation if it has children).
     */
    public void deleteCategory(Long id) {
        log.info("Deleting asset category with id: {}", id);

        AssetCategory category = categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Asset category not found with id: " + id));

        if (category.getChildCategories() != null && !category.getChildCategories().isEmpty()) {
            throw new IllegalStateException("Cannot delete category with children. Deactivate it instead.");
        }

        categoryRepository.deleteById(id);
        log.info("Asset category deleted with id: {}", id);
    }

    /**
     * Search categories
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> searchCategories(String searchTerm) {
        return categoryRepository.searchCategories(searchTerm);
    }

    /**
     * Get full category tree starting from root categories
     */
    @Transactional(readOnly = true)
    public List<AssetCategoryResponseDto> getCategoryTree() {
        List<AssetCategory> roots = categoryRepository.findByParentCategoryIsNull();
        return roots.stream().map(this::toTreeDto).toList();
    }

    private AssetCategoryResponseDto toTreeDto(AssetCategory cat) {
        AssetCategoryResponseDto dto = convertToResponseDto(cat);
        if (cat.getChildCategories() != null && !cat.getChildCategories().isEmpty()) {
            dto.setChildren(cat.getChildCategories().stream().map(this::toTreeDto).toList());
        }
        return dto;
    }

    private AssetCategoryResponseDto convertToResponseDto(AssetCategory category) {
        return AssetCategoryResponseDto.builder()
            .id(category.getId())
            .name(category.getName())
            .code(category.getCode())
            .description(category.getDescription())
            .parentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
            .custodianDeptCode(category.getCustodianDeptCode())
            .custodianUserId(category.getCustodianUserId())
            .requiresApproval(category.getRequiresApproval())
            .active(category.getActive())
            .createdAt(category.getCreatedAt())
            .updatedAt(category.getUpdatedAt())
            .build();
    }
}
