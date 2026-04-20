package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.entity.AssetCategory;
import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.ItemType;
import com.werkflow.business.inventory.repository.AssetCategoryRepository;
import com.werkflow.business.inventory.repository.AssetDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service for AssetDefinition operations
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssetDefinitionService {

    private final AssetDefinitionRepository definitionRepository;
    private final AssetCategoryRepository categoryRepository;

    /**
     * Create a new asset definition
     */
    public AssetDefinition createDefinition(AssetDefinition definition) {
        log.info("Creating new asset definition: {}", definition.getName());

        if (definitionRepository.findBySku(definition.getSku()).isPresent()) {
            throw new IllegalArgumentException("Asset definition with SKU already exists: " + definition.getSku());
        }

        AssetDefinition saved = definitionRepository.save(definition);
        log.info("Asset definition created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Get asset definition by ID
     */
    @Transactional(readOnly = true)
    public AssetDefinition getDefinitionById(Long id) {
        return definitionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Asset definition not found with id: " + id));
    }

    /**
     * Get asset definition by SKU
     */
    @Transactional(readOnly = true)
    public AssetDefinition getDefinitionBySku(String sku) {
        return definitionRepository.findBySku(sku)
            .orElseThrow(() -> new EntityNotFoundException("Asset definition not found with SKU: " + sku));
    }

    /**
     * Get asset definitions by category
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> getDefinitionsByCategory(Long categoryId) {
        return definitionRepository.findByCategoryIdAndActiveTrue(categoryId);
    }

    /**
     * Get all active asset definitions
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> getActiveDefinitions() {
        return definitionRepository.findByActiveTrue();
    }

    /**
     * Get all asset definitions
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> getAllDefinitions() {
        return definitionRepository.findAll();
    }

    /**
     * Get asset definitions requiring maintenance
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> getDefinitionsRequiringMaintenance() {
        return definitionRepository.findByRequiresMaintenanceTrueAndActiveTrue();
    }

    /**
     * Get asset definitions by manufacturer
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> getDefinitionsByManufacturer(String manufacturer) {
        return definitionRepository.findByManufacturerAndActiveTrue(manufacturer);
    }

    /**
     * Get asset definitions by price range
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> getDefinitionsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return definitionRepository.findByPriceRange(minPrice, maxPrice);
    }

    /**
     * Update asset definition
     */
    public AssetDefinition updateDefinition(Long id, AssetDefinition definitionDetails) {
        log.info("Updating asset definition with id: {}", id);

        AssetDefinition definition = getDefinitionById(id);

        // Validate SKU uniqueness if changed
        if (!definition.getSku().equals(definitionDetails.getSku())) {
            if (definitionRepository.findBySku(definitionDetails.getSku()).isPresent()) {
                throw new IllegalArgumentException("Asset definition with SKU already exists: " + definitionDetails.getSku());
            }
        }

        definition.setSku(definitionDetails.getSku());
        definition.setName(definitionDetails.getName());
        definition.setManufacturer(definitionDetails.getManufacturer());
        definition.setModel(definitionDetails.getModel());
        if (definitionDetails.getItemType() != null) {
            definition.setItemType(definitionDetails.getItemType());
        }
        definition.setSpecifications(definitionDetails.getSpecifications());
        definition.setUnitCost(definitionDetails.getUnitCost());
        definition.setExpectedLifespanMonths(definitionDetails.getExpectedLifespanMonths());
        definition.setRequiresMaintenance(definitionDetails.getRequiresMaintenance());
        definition.setMaintenanceIntervalMonths(definitionDetails.getMaintenanceIntervalMonths());
        definition.setActive(definitionDetails.getActive());

        if (definitionDetails.getCategory() != null) {
            definition.setCategory(definitionDetails.getCategory());
        }

        AssetDefinition updated = definitionRepository.save(definition);
        log.info("Asset definition updated with id: {}", id);
        return updated;
    }

    /**
     * Deactivate asset definition
     */
    public AssetDefinition deactivateDefinition(Long id) {
        log.info("Deactivating asset definition with id: {}", id);

        AssetDefinition definition = getDefinitionById(id);
        definition.setActive(false);

        return definitionRepository.save(definition);
    }

    /**
     * Activate asset definition
     */
    public AssetDefinition activateDefinition(Long id) {
        log.info("Activating asset definition with id: {}", id);

        AssetDefinition definition = getDefinitionById(id);
        definition.setActive(true);

        return definitionRepository.save(definition);
    }

    /**
     * Search asset definitions
     */
    @Transactional(readOnly = true)
    public List<AssetDefinition> searchDefinitions(String searchTerm) {
        return definitionRepository.searchDefinitions(searchTerm);
    }
}
