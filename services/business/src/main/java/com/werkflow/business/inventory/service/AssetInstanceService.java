package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.repository.AssetDefinitionRepository;
import com.werkflow.business.inventory.repository.AssetInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for AssetInstance operations
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssetInstanceService {

    private final AssetInstanceRepository instanceRepository;
    private final AssetDefinitionRepository definitionRepository;

    /**
     * Create a new asset instance
     */
    public AssetInstance createInstance(AssetInstance instance) {
        log.info("Creating new asset instance with tag: {}", instance.getAssetTag());

        if (instanceRepository.findByAssetTag(instance.getAssetTag()).isPresent()) {
            throw new IllegalArgumentException("Asset instance with tag already exists: " + instance.getAssetTag());
        }

        AssetInstance saved = instanceRepository.save(instance);
        log.info("Asset instance created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Get asset instance by ID
     */
    @Transactional(readOnly = true)
    public AssetInstance getInstanceById(Long id) {
        return instanceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found with id: " + id));
    }

    /**
     * Get asset instance by asset tag
     */
    @Transactional(readOnly = true)
    public AssetInstance getInstanceByAssetTag(String assetTag) {
        return instanceRepository.findByAssetTag(assetTag)
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found with tag: " + assetTag));
    }

    /**
     * Get asset instances by asset definition
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getInstancesByDefinition(Long definitionId) {
        AssetDefinition definition = definitionRepository.findById(definitionId)
            .orElseThrow(() -> new EntityNotFoundException("Asset definition not found with id: " + definitionId));

        return instanceRepository.findByAssetDefinition(definition);
    }

    /**
     * Get asset instances by status
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getInstancesByStatus(String status) {
        return instanceRepository.findByStatus(status);
    }

    /**
     * Get available assets
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getAvailableAssets() {
        return instanceRepository.findAvailableAssets();
    }

    /**
     * Get assets in use
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getAssetsInUse() {
        return instanceRepository.findAssetsInUse();
    }

    /**
     * Get assets requiring maintenance
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getAssetsRequiringMaintenance() {
        return instanceRepository.findAssetsRequiringMaintenance();
    }

    /**
     * Get assets with warranty expiring soon
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getAssetsWithExpiringWarranty(LocalDate expiryDate) {
        return instanceRepository.findAssetsWithExpiringWarranty(expiryDate);
    }

    /**
     * Get all asset instances
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getAllInstances() {
        return instanceRepository.findAll();
    }

    /**
     * Update asset instance
     */
    public AssetInstance updateInstance(Long id, AssetInstance instanceDetails) {
        log.info("Updating asset instance with id: {}", id);

        AssetInstance instance = getInstanceById(id);

        // Validate asset tag uniqueness if changed
        if (!instance.getAssetTag().equals(instanceDetails.getAssetTag())) {
            if (instanceRepository.findByAssetTag(instanceDetails.getAssetTag()).isPresent()) {
                throw new IllegalArgumentException("Asset instance with tag already exists: " + instanceDetails.getAssetTag());
            }
        }

        instance.setAssetTag(instanceDetails.getAssetTag());
        instance.setSerialNumber(instanceDetails.getSerialNumber());
        instance.setPurchaseDate(instanceDetails.getPurchaseDate());
        instance.setPurchaseCost(instanceDetails.getPurchaseCost());
        instance.setWarrantyExpiryDate(instanceDetails.getWarrantyExpiryDate());
        instance.setCondition(instanceDetails.getCondition());
        instance.setStatus(instanceDetails.getStatus());
        instance.setCurrentLocation(instanceDetails.getCurrentLocation());
        instance.setNotes(instanceDetails.getNotes());
        instance.setMetadata(instanceDetails.getMetadata());

        if (instanceDetails.getAssetDefinition() != null) {
            instance.setAssetDefinition(instanceDetails.getAssetDefinition());
        }

        AssetInstance updated = instanceRepository.save(instance);
        log.info("Asset instance updated with id: {}", id);
        return updated;
    }

    /**
     * Update asset status
     */
    public AssetInstance updateStatus(Long id, String status) {
        log.info("Updating asset instance status to: {}", status);

        AssetInstance instance = getInstanceById(id);
        instance.setStatus(AssetInstance.AssetStatus.valueOf(status));

        return instanceRepository.save(instance);
    }

    /**
     * Search asset instances
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> searchInstances(String searchTerm) {
        return instanceRepository.searchAssets(searchTerm);
    }

    /**
     * Get assets needing attention
     */
    @Transactional(readOnly = true)
    public List<AssetInstance> getAssetsNeedingAttention() {
        return instanceRepository.findAssetsNeedingAttention();
    }
}
