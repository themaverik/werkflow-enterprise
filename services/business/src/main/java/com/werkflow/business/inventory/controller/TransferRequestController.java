package com.werkflow.business.inventory.controller;

import com.werkflow.business.inventory.dto.TransferRequestRequestDto;
import com.werkflow.business.inventory.dto.TransferRequestResponseDto;
import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.TransferRequest;
import com.werkflow.business.inventory.service.AssetInstanceService;
import com.werkflow.business.inventory.service.TransferRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for TransferRequest operations
 */
@RestController
@RequestMapping("/transfer-requests")
@RequiredArgsConstructor
@Tag(name = "Transfer Requests", description = "Asset transfer and order batching APIs")
public class TransferRequestController {

    private final TransferRequestService transferService;
    private final AssetInstanceService assetService;

    @PostMapping
    @Operation(summary = "Create transfer request", description = "Create a new asset transfer request")
    public ResponseEntity<TransferRequestResponseDto> createTransferRequest(@Valid @RequestBody TransferRequestRequestDto requestDto) {
        AssetInstance asset = assetService.getInstanceById(requestDto.getAssetInstanceId());

        TransferRequest request = TransferRequest.builder()
            .assetInstance(asset)
            .fromDeptId(requestDto.getFromDeptId())
            .fromUserId(requestDto.getFromUserId())
            .toDeptId(requestDto.getToDeptId())
            .toUserId(requestDto.getToUserId())
            .transferType(TransferRequest.TransferType.valueOf(requestDto.getTransferType()))
            .transferReason(requestDto.getTransferReason())
            .expectedReturnDate(requestDto.getExpectedReturnDate())
            .initiatedByUserId(requestDto.getInitiatedByUserId())
            .initiatedDate(LocalDateTime.now())
            .status(TransferRequest.TransferStatus.PENDING)
            .build();

        TransferRequest created = transferService.createTransferRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transfer request by ID", description = "Retrieve a transfer request by its ID")
    public ResponseEntity<TransferRequestResponseDto> getTransferRequestById(@PathVariable Long id) {
        TransferRequest request = transferService.getTransferRequestById(id);
        return ResponseEntity.ok(mapToResponse(request));
    }

    @GetMapping
    @Operation(summary = "Get all transfer requests", description = "Retrieve all transfer requests")
    public ResponseEntity<List<TransferRequestResponseDto>> getAllTransfers() {
        List<TransferRequest> requests = transferService.getAllTransfers();
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/asset/{assetId}")
    @Operation(summary = "Get transfers by asset", description = "Get all transfer requests for an asset")
    public ResponseEntity<List<TransferRequestResponseDto>> getTransfersByAsset(@PathVariable Long assetId) {
        List<TransferRequest> requests = transferService.getTransfersByAsset(assetId);
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get transfers by status", description = "Get transfer requests by status")
    public ResponseEntity<List<TransferRequestResponseDto>> getTransfersByStatus(@PathVariable String status) {
        List<TransferRequest> requests = transferService.getTransfersByStatus(status);
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending transfers", description = "Retrieve all pending transfer requests")
    public ResponseEntity<List<TransferRequestResponseDto>> getPendingTransfers() {
        List<TransferRequest> requests = transferService.getPendingTransfers();
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/from-dept/{deptId}")
    @Operation(summary = "Get transfers from department", description = "Get transfer requests from a department")
    public ResponseEntity<List<TransferRequestResponseDto>> getTransfersFromDepartment(@PathVariable Long deptId) {
        List<TransferRequest> requests = transferService.getTransfersFromDepartment(deptId);
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/to-dept/{deptId}")
    @Operation(summary = "Get transfers to department", description = "Get transfer requests to a department")
    public ResponseEntity<List<TransferRequestResponseDto>> getTransfersToDepartment(@PathVariable Long deptId) {
        List<TransferRequest> requests = transferService.getTransfersToDepartment(deptId);
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/inter-department/active")
    @Operation(summary = "Get active inter-department transfers", description = "Retrieve active inter-department transfer requests")
    public ResponseEntity<List<TransferRequestResponseDto>> getActiveInterDepartmentTransfers() {
        List<TransferRequest> requests = transferService.getActiveInterDepartmentTransfers();
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/loans/active")
    @Operation(summary = "Get active loans", description = "Retrieve all active loan requests")
    public ResponseEntity<List<TransferRequestResponseDto>> getActiveLoanRequests() {
        List<TransferRequest> requests = transferService.getActiveLoanRequests();
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/loans/overdue")
    @Operation(summary = "Get overdue loans", description = "Retrieve overdue loan requests")
    public ResponseEntity<List<TransferRequestResponseDto>> getOverdueLoans() {
        List<TransferRequest> requests = transferService.getOverdueLoans();
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    @Operation(summary = "Search transfer requests", description = "Search transfer requests by reason or asset tag")
    public ResponseEntity<List<TransferRequestResponseDto>> searchTransfers(@RequestParam String searchTerm) {
        List<TransferRequest> requests = transferService.searchTransfers(searchTerm);
        return ResponseEntity.ok(requests.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve transfer request", description = "Approve a transfer request")
    public ResponseEntity<TransferRequestResponseDto> approveTransfer(
            @PathVariable Long id,
            @RequestParam Long approverUserId) {
        TransferRequest updated = transferService.approveTransfer(id, approverUserId);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject transfer request", description = "Reject a transfer request")
    public ResponseEntity<TransferRequestResponseDto> rejectTransfer(
            @PathVariable Long id,
            @RequestParam String rejectionReason) {
        TransferRequest updated = transferService.rejectTransfer(id, rejectionReason);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Complete transfer request", description = "Mark a transfer request as completed")
    public ResponseEntity<TransferRequestResponseDto> completeTransfer(@PathVariable Long id) {
        TransferRequest updated = transferService.completeTransfer(id);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update transfer request", description = "Update a pending transfer request")
    public ResponseEntity<TransferRequestResponseDto> updateTransferRequest(
            @PathVariable Long id,
            @Valid @RequestBody TransferRequestRequestDto requestDto) {
        TransferRequest requestDetails = TransferRequest.builder()
            .transferReason(requestDto.getTransferReason())
            .expectedReturnDate(requestDto.getExpectedReturnDate())
            .build();

        TransferRequest updated = transferService.updateTransferRequest(id, requestDetails);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    private TransferRequestResponseDto mapToResponse(TransferRequest request) {
        return TransferRequestResponseDto.builder()
            .id(request.getId())
            .assetInstanceId(request.getAssetInstance().getId())
            .assetTag(request.getAssetInstance().getAssetTag())
            .fromDeptId(request.getFromDeptId())
            .fromUserId(request.getFromUserId())
            .toDeptId(request.getToDeptId())
            .toUserId(request.getToUserId())
            .transferType(request.getTransferType().toString())
            .transferReason(request.getTransferReason())
            .expectedReturnDate(request.getExpectedReturnDate())
            .initiatedByUserId(request.getInitiatedByUserId())
            .initiatedDate(request.getInitiatedDate())
            .approvedByUserId(request.getApprovedByUserId())
            .approvedDate(request.getApprovedDate())
            .completedDate(request.getCompletedDate())
            .status(request.getStatus().toString())
            .processInstanceId(request.getProcessInstanceId())
            .rejectionReason(request.getRejectionReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }
}
