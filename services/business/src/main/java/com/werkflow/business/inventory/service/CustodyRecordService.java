package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.CustodyRecord;
import com.werkflow.business.inventory.repository.AssetInstanceRepository;
import com.werkflow.business.inventory.repository.CustodyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for CustodyRecord operations (Inter-department asset assignments)
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustodyRecordService {

    private final CustodyRecordRepository custodyRepository;
    private final AssetInstanceRepository assetRepository;

    /**
     * Create a new custody record
     */
    public CustodyRecord createCustodyRecord(CustodyRecord record) {
        log.info("Creating new custody record for asset: {}", record.getAssetInstance().getAssetTag());

        // Ensure asset exists
        AssetInstance asset = assetRepository.findById(record.getAssetInstance().getId())
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found"));

        CustodyRecord saved = custodyRepository.save(record);
        log.info("Custody record created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Get custody record by ID
     */
    @Transactional(readOnly = true)
    public CustodyRecord getCustodyRecordById(Long id) {
        return custodyRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Custody record not found with id: " + id));
    }

    /**
     * Get current custody for an asset
     */
    @Transactional(readOnly = true)
    public CustodyRecord getCurrentCustody(Long assetId) {
        AssetInstance asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found with id: " + assetId));

        return custodyRepository.findCurrentCustody(asset)
            .orElseThrow(() -> new EntityNotFoundException("No active custody record found for asset: " + assetId));
    }

    /**
     * Get custody history for an asset
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getCustodyHistory(Long assetId) {
        AssetInstance asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found with id: " + assetId));

        return custodyRepository.findByAssetInstanceOrderByStartDateDesc(asset);
    }

    /**
     * Get custody records by department
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getCustodyByDepartment(Long deptId) {
        return custodyRepository.findActiveCustodyByDepartment(deptId);
    }

    /**
     * Get custody records by user
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getCustodyByUser(Long userId) {
        return custodyRepository.findByCustodianUserId(userId);
    }

    /**
     * Get custody records by custody type
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getCustodyByType(String custodyType) {
        return custodyRepository.findByCustodyType(custodyType);
    }

    /**
     * Get all active custody records
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getActiveCustodyRecords() {
        return custodyRepository.findActiveCustodyRecords();
    }

    /**
     * Get overdue temporary custody records
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getOverdueTemporaryCustody() {
        return custodyRepository.findOverdueTemporaryCustody(LocalDateTime.now());
    }

    /**
     * End custody (return asset)
     */
    public CustodyRecord endCustody(Long id, String returnCondition) {
        log.info("Ending custody record with id: {}", id);

        CustodyRecord record = getCustodyRecordById(id);
        record.setEndDate(LocalDateTime.now());
        record.setReturnCondition(AssetInstance.AssetCondition.valueOf(returnCondition));

        return custodyRepository.save(record);
    }

    /**
     * Update custody record
     */
    public CustodyRecord updateCustodyRecord(Long id, CustodyRecord recordDetails) {
        log.info("Updating custody record with id: {}", id);

        CustodyRecord record = getCustodyRecordById(id);

        record.setPhysicalLocation(recordDetails.getPhysicalLocation());
        record.setCustodyType(recordDetails.getCustodyType());
        record.setNotes(recordDetails.getNotes());

        return custodyRepository.save(record);
    }

    /**
     * Get all custody records
     */
    @Transactional(readOnly = true)
    public List<CustodyRecord> getAllCustodyRecords() {
        return custodyRepository.findAll();
    }
}
