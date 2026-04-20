package com.werkflow.business.procurement.service;

import com.werkflow.business.procurement.dto.*;
import com.werkflow.business.procurement.entity.*;
import com.werkflow.business.procurement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptService {
    private final ReceiptRepository receiptRepository;
    private final PurchaseOrderRepository poRepository;
    private final ReceiptLineItemRepository lineItemRepository;

    @Transactional(readOnly = true)
    public List<ReceiptResponse> getAllReceipts() {
        return receiptRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptById(Long id) {
        return receiptRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Receipt not found: " + id));
    }

    @Transactional
    public ReceiptResponse createReceipt(ReceiptRequest request) {
        var po = poRepository.findById(request.getPurchaseOrderId())
            .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        Receipt receipt = Receipt.builder()
            .receiptNumber("GR-" + System.currentTimeMillis())
            .purchaseOrder(po)
            .receiptDate(request.getReceiptDate())
            .receivedByUserId(request.getReceivedByUserId())
            .status(Receipt.ReceiptStatus.DRAFT)
            .notes(request.getNotes())
            .discrepancyNotes(request.getDiscrepancyNotes())
            .build();

        return toResponse(receiptRepository.save(receipt));
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        List<ReceiptLineItemResponse> lineItems = lineItemRepository.findByReceiptId(receipt.getId()).stream()
            .map(this::lineItemToResponse)
            .collect(Collectors.toList());

        return ReceiptResponse.builder()
            .id(receipt.getId())
            .receiptNumber(receipt.getReceiptNumber())
            .purchaseOrderId(receipt.getPurchaseOrder().getId())
            .poNumber(receipt.getPurchaseOrder().getPoNumber())
            .receiptDate(receipt.getReceiptDate())
            .receivedByUserId(receipt.getReceivedByUserId())
            .status(receipt.getStatus())
            .notes(receipt.getNotes())
            .discrepancyNotes(receipt.getDiscrepancyNotes())
            .lineItems(lineItems)
            .createdAt(receipt.getCreatedAt())
            .updatedAt(receipt.getUpdatedAt())
            .build();
    }

    private ReceiptLineItemResponse lineItemToResponse(ReceiptLineItem item) {
        return ReceiptLineItemResponse.builder()
            .id(item.getId())
            .receiptId(item.getReceipt().getId())
            .poLineItemId(item.getPoLineItem().getId())
            .itemDescription(item.getPoLineItem().getDescription())
            .receivedQuantity(item.getReceivedQuantity())
            .acceptedQuantity(item.getAcceptedQuantity())
            .rejectedQuantity(item.getRejectedQuantity())
            .condition(item.getCondition())
            .notes(item.getNotes())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
