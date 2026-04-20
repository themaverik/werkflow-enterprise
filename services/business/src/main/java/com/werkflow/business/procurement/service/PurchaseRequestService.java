package com.werkflow.business.procurement.service;

import com.werkflow.business.procurement.dto.*;
import com.werkflow.business.procurement.entity.*;
import com.werkflow.business.procurement.repository.PurchaseRequestRepository;
import com.werkflow.business.procurement.repository.PrLineItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {
    private final PurchaseRequestRepository prRepository;
    private final PrLineItemRepository lineItemRepository;

    @Transactional(readOnly = true)
    public List<PurchaseRequestResponse> getAllPurchaseRequests() {
        return prRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseRequestResponse getPurchaseRequestById(Long id) {
        return prRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Purchase request not found: " + id));
    }

    @Transactional
    public PurchaseRequestResponse createPurchaseRequest(PurchaseRequestRequest request) {
        PurchaseRequest pr = PurchaseRequest.builder()
            .prNumber("PR-" + System.currentTimeMillis())
            .requestingDeptId(request.getRequestingDeptId())
            .requesterUserId(request.getRequesterUserId())
            .requestDate(request.getRequestDate())
            .requiredByDate(request.getRequiredByDate())
            .priority(request.getPriority() != null ? request.getPriority() : PurchaseRequest.Priority.MEDIUM)
            .totalAmount(BigDecimal.ZERO)
            .status(PurchaseRequest.PrStatus.DRAFT)
            .justification(request.getJustification())
            .notes(request.getNotes())
            .build();

        PurchaseRequest saved = prRepository.save(pr);
        return toResponse(saved);
    }

    private PurchaseRequestResponse toResponse(PurchaseRequest pr) {
        List<PrLineItemResponse> lineItems = lineItemRepository.findByPurchaseRequestId(pr.getId()).stream()
            .map(this::lineItemToResponse)
            .collect(Collectors.toList());

        return PurchaseRequestResponse.builder()
            .id(pr.getId())
            .prNumber(pr.getPrNumber())
            .requestingDeptId(pr.getRequestingDeptId())
            .requesterUserId(pr.getRequesterUserId())
            .requestDate(pr.getRequestDate())
            .requiredByDate(pr.getRequiredByDate())
            .priority(pr.getPriority())
            .totalAmount(pr.getTotalAmount())
            .status(pr.getStatus())
            .approvedByUserId(pr.getApprovedByUserId())
            .approvedDate(pr.getApprovedDate())
            .processInstanceId(pr.getProcessInstanceId())
            .rejectionReason(pr.getRejectionReason())
            .justification(pr.getJustification())
            .notes(pr.getNotes())
            .lineItems(lineItems)
            .createdAt(pr.getCreatedAt())
            .updatedAt(pr.getUpdatedAt())
            .build();
    }

    private PrLineItemResponse lineItemToResponse(PrLineItem item) {
        return PrLineItemResponse.builder()
            .id(item.getId())
            .purchaseRequestId(item.getPurchaseRequest().getId())
            .lineNumber(item.getLineNumber())
            .description(item.getDescription())
            .quantity(item.getQuantity())
            .unitOfMeasure(item.getUnitOfMeasure())
            .estimatedUnitPrice(item.getEstimatedUnitPrice())
            .totalPrice(item.getTotalPrice())
            .budgetCategoryId(item.getBudgetCategoryId())
            .specifications(item.getSpecifications())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
