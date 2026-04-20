package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.MaintenanceRecord;
import com.werkflow.business.inventory.repository.AssetInstanceRepository;
import com.werkflow.business.inventory.repository.MaintenanceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for MaintenanceRecord operations
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MaintenanceRecordService {

    private final MaintenanceRecordRepository maintenanceRepository;
    private final AssetInstanceRepository assetRepository;

    /**
     * Create a new maintenance record
     */
    public MaintenanceRecord createMaintenanceRecord(MaintenanceRecord record) {
        log.info("Creating new maintenance record for asset: {}", record.getAssetInstance().getAssetTag());

        // Ensure asset exists
        AssetInstance asset = assetRepository.findById(record.getAssetInstance().getId())
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found"));

        MaintenanceRecord saved = maintenanceRepository.save(record);
        log.info("Maintenance record created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Get maintenance record by ID
     */
    @Transactional(readOnly = true)
    public MaintenanceRecord getMaintenanceRecordById(Long id) {
        return maintenanceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found with id: " + id));
    }

    /**
     * Get maintenance history for an asset
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getMaintenanceHistory(Long assetId) {
        AssetInstance asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found with id: " + assetId));

        return maintenanceRepository.findByAssetInstanceOrderByScheduledDateDesc(asset);
    }

    /**
     * Get maintenance records by type
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getMaintenanceByType(String maintenanceType) {
        return maintenanceRepository.findByMaintenanceType(maintenanceType);
    }

    /**
     * Get incomplete maintenance records
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getIncompleteMaintenanceRecords() {
        return maintenanceRepository.findIncompleteMaintenanceRecords();
    }

    /**
     * Get overdue maintenance records
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getOverdueMaintenanceRecords() {
        return maintenanceRepository.findOverdueMaintenanceRecords(LocalDate.now());
    }

    /**
     * Get completed maintenance records
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getCompletedMaintenanceRecords() {
        return maintenanceRepository.findCompletedMaintenanceRecords();
    }

    /**
     * Get scheduled maintenance coming due
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getScheduledMaintenanceDue(LocalDate dueDate) {
        return maintenanceRepository.findScheduledMaintenanceDue(dueDate);
    }

    /**
     * Get expensive maintenance records
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getExpensiveMaintenanceRecords(BigDecimal minCost) {
        return maintenanceRepository.findExpensiveMaintenanceRecords(minCost);
    }

    /**
     * Update maintenance record
     */
    public MaintenanceRecord updateMaintenanceRecord(Long id, MaintenanceRecord recordDetails) {
        log.info("Updating maintenance record with id: {}", id);

        MaintenanceRecord record = getMaintenanceRecordById(id);

        record.setScheduledDate(recordDetails.getScheduledDate());
        record.setCompletedDate(recordDetails.getCompletedDate());
        record.setPerformedBy(recordDetails.getPerformedBy());
        record.setCost(recordDetails.getCost());
        record.setDescription(recordDetails.getDescription());
        record.setNextMaintenanceDate(recordDetails.getNextMaintenanceDate());

        return maintenanceRepository.save(record);
    }

    /**
     * Complete maintenance record
     */
    public MaintenanceRecord completeMaintenanceRecord(Long id, LocalDate completedDate, LocalDate nextMaintenanceDate) {
        log.info("Completing maintenance record with id: {}", id);

        MaintenanceRecord record = getMaintenanceRecordById(id);
        record.setCompletedDate(completedDate);
        record.setNextMaintenanceDate(nextMaintenanceDate);

        return maintenanceRepository.save(record);
    }

    /**
     * Get all maintenance records
     */
    @Transactional(readOnly = true)
    public List<MaintenanceRecord> getAllMaintenanceRecords() {
        return maintenanceRepository.findAll();
    }
}
