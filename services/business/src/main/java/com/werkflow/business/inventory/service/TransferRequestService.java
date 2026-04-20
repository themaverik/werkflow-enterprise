package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.TransferRequest;
import com.werkflow.business.inventory.repository.AssetInstanceRepository;
import com.werkflow.business.inventory.repository.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for TransferRequest operations (Order batching and inter-department transfers)
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransferRequestService {

    private final TransferRequestRepository transferRepository;
    private final AssetInstanceRepository assetRepository;

    /**
     * Create a new transfer request
     */
    public TransferRequest createTransferRequest(TransferRequest request) {
        log.info("Creating new transfer request for asset: {}", request.getAssetInstance().getAssetTag());

        // Ensure asset exists
        AssetInstance asset = assetRepository.findById(request.getAssetInstance().getId())
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found"));

        // Validate transfer details
        if (request.getFromDeptId() == null || request.getToDeptId() == null) {
            throw new IllegalArgumentException("From and To department IDs are required");
        }

        request.setInitiatedDate(LocalDateTime.now());

        TransferRequest saved = transferRepository.save(request);
        log.info("Transfer request created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Get transfer request by ID
     */
    @Transactional(readOnly = true)
    public TransferRequest getTransferRequestById(Long id) {
        return transferRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transfer request not found with id: " + id));
    }

    /**
     * Get transfer requests by asset
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getTransfersByAsset(Long assetId) {
        AssetInstance asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new EntityNotFoundException("Asset instance not found with id: " + assetId));

        return transferRepository.findByAssetInstance(asset);
    }

    /**
     * Get transfer requests by status
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getTransfersByStatus(String status) {
        return transferRepository.findByStatus(status);
    }

    /**
     * Get pending transfer requests
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getPendingTransfers() {
        return transferRepository.findPendingRequests();
    }

    /**
     * Get transfers from department
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getTransfersFromDepartment(Long deptId) {
        return transferRepository.findByFromDeptId(deptId);
    }

    /**
     * Get transfers to department
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getTransfersToDepartment(Long deptId) {
        return transferRepository.findByToDeptId(deptId);
    }

    /**
     * Get active inter-department transfers
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getActiveInterDepartmentTransfers() {
        return transferRepository.findActiveInterDepartmentTransfers();
    }

    /**
     * Get active loan requests
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getActiveLoanRequests() {
        return transferRepository.findActiveLoanRequests();
    }

    /**
     * Get overdue loans
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getOverdueLoans() {
        return transferRepository.findOverdueLoans(LocalDateTime.now());
    }

    /**
     * Approve transfer request
     */
    public TransferRequest approveTransfer(Long id, Long approverUserId) {
        log.info("Approving transfer request with id: {}", id);

        TransferRequest request = getTransferRequestById(id);

        if (!request.getStatus().equals(TransferRequest.TransferStatus.PENDING)) {
            throw new IllegalStateException("Transfer request cannot be approved in status: " + request.getStatus());
        }

        request.setStatus(TransferRequest.TransferStatus.APPROVED);
        request.setApprovedByUserId(approverUserId);
        request.setApprovedDate(LocalDateTime.now());

        return transferRepository.save(request);
    }

    /**
     * Reject transfer request
     */
    public TransferRequest rejectTransfer(Long id, String rejectionReason) {
        log.info("Rejecting transfer request with id: {}", id);

        TransferRequest request = getTransferRequestById(id);

        if (!request.getStatus().equals(TransferRequest.TransferStatus.PENDING)) {
            throw new IllegalStateException("Transfer request cannot be rejected in status: " + request.getStatus());
        }

        request.setStatus(TransferRequest.TransferStatus.REJECTED);
        request.setRejectionReason(rejectionReason);

        return transferRepository.save(request);
    }

    /**
     * Complete transfer request
     */
    public TransferRequest completeTransfer(Long id) {
        log.info("Completing transfer request with id: {}", id);

        TransferRequest request = getTransferRequestById(id);

        if (!request.getStatus().equals(TransferRequest.TransferStatus.APPROVED)) {
            throw new IllegalStateException("Only approved transfers can be completed");
        }

        request.setStatus(TransferRequest.TransferStatus.COMPLETED);
        request.setCompletedDate(LocalDateTime.now());

        return transferRepository.save(request);
    }

    /**
     * Update transfer request
     */
    public TransferRequest updateTransferRequest(Long id, TransferRequest requestDetails) {
        log.info("Updating transfer request with id: {}", id);

        TransferRequest request = getTransferRequestById(id);

        if (!request.getStatus().equals(TransferRequest.TransferStatus.PENDING)) {
            throw new IllegalStateException("Only pending transfers can be updated");
        }

        request.setTransferReason(requestDetails.getTransferReason());
        request.setExpectedReturnDate(requestDetails.getExpectedReturnDate());
        if (requestDetails.getRejectionReason() != null) {
            request.setRejectionReason(requestDetails.getRejectionReason());
        }

        return transferRepository.save(request);
    }

    /**
     * Get all transfer requests
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> getAllTransfers() {
        return transferRepository.findAll();
    }

    /**
     * Search transfer requests
     */
    @Transactional(readOnly = true)
    public List<TransferRequest> searchTransfers(String searchTerm) {
        return transferRepository.searchTransfers(searchTerm);
    }
}
