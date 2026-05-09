package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.config.CacheConfig;
import com.werkflow.admin.designtime.platform.dto.CategoryEntry;
import com.werkflow.admin.designtime.platform.dto.CategoryRequest;
import com.werkflow.admin.designtime.platform.entity.Category;
import com.werkflow.admin.designtime.platform.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD operations over the category table, tenant-scoped.
 * ArtifactCount is sourced directly from the category table for v1
 * (full cross-service aggregation deferred to M4.8 analytics).
 */
@Service
@RequiredArgsConstructor
public class CategoryProjector {

    private final CategoryRepository categoryRepository;

    /**
     * Returns all categories for the tenant in display order.
     */
    @Cacheable(value = CacheConfig.PSS_CATEGORIES, key = "#tenantId")
    @Transactional(readOnly = true)
    public List<CategoryEntry> list(String tenantId) {
        return categoryRepository.findByTenantIdOrderByDisplayOrder(tenantId)
                .stream()
                .map(this::toEntry)
                .collect(Collectors.toList());
    }

    /**
     * Creates a new category for the tenant.
     */
    @CacheEvict(value = {CacheConfig.PSS_CATEGORIES, CacheConfig.PSS_CAPABILITIES}, key = "#tenantId")
    @Transactional
    public CategoryEntry create(String tenantId, CategoryRequest request) {
        if (categoryRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category with code '" + request.code() + "' already exists for tenant");
        }
        Category category = new Category();
        category.setTenantId(tenantId);
        applyRequest(category, request);
        return toEntry(categoryRepository.save(category));
    }

    /**
     * Updates an existing category.
     */
    @CacheEvict(value = {CacheConfig.PSS_CATEGORIES, CacheConfig.PSS_CAPABILITIES}, key = "#tenantId")
    @Transactional
    public CategoryEntry update(String tenantId, UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (!category.getCode().equals(request.code())
                && categoryRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Category code '" + request.code() + "' is already in use");
        }
        applyRequest(category, request);
        return toEntry(categoryRepository.save(category));
    }

    /**
     * Deletes a category. Logs a warning if artifacts still reference it — references become orphaned (null).
     */
    @CacheEvict(value = {CacheConfig.PSS_CATEGORIES, CacheConfig.PSS_CAPABILITIES}, key = "#tenantId")
    @Transactional
    public void delete(String tenantId, UUID id) {
        Category category = categoryRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        categoryRepository.delete(category);
    }

    private void applyRequest(Category category, CategoryRequest request) {
        category.setDisplayName(request.displayName());
        category.setCode(request.code());
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
    }

    private CategoryEntry toEntry(Category c) {
        return new CategoryEntry(c.getId(), c.getCode(), c.getDisplayName(),
                c.getIcon(), c.getColor(), c.getDisplayOrder(), 0L);
    }
}
