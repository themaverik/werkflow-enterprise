package com.werkflow.business.procurement.service;

import com.werkflow.business.procurement.dto.*;
import com.werkflow.business.procurement.entity.*;
import com.werkflow.business.procurement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
    private final PurchaseOrderRepository poRepository;
    private final VendorRepository vendorRepository;
    private final PoLineItemRepository lineItemRepository;

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getAllPurchaseOrders() {
        return poRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrderById(Long id) {
        return poRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Purchase order not found: " + id));
    }

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        var vendor = vendorRepository.findById(request.getVendorId())
            .orElseThrow(() -> new RuntimeException("Vendor not found"));

        PurchaseOrder po = PurchaseOrder.builder()
            .poNumber("PO-" + System.currentTimeMillis())
            .vendor(vendor)
            .orderDate(request.getOrderDate())
            .expectedDeliveryDate(request.getExpectedDeliveryDate())
            .totalAmount(BigDecimal.ZERO)
            .status(PurchaseOrder.PoStatus.DRAFT)
            .createdByUserId(request.getCreatedByUserId())
            .deliveryAddress(request.getDeliveryAddress())
            .paymentTerms(request.getPaymentTerms())
            .notes(request.getNotes())
            .build();

        return toResponse(poRepository.save(po));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        List<PoLineItemResponse> lineItems = lineItemRepository.findByPurchaseOrderId(po.getId()).stream()
            .map(this::lineItemToResponse)
            .collect(Collectors.toList());

        return PurchaseOrderResponse.builder()
            .id(po.getId())
            .poNumber(po.getPoNumber())
            .purchaseRequestId(po.getPurchaseRequest() != null ? po.getPurchaseRequest().getId() : null)
            .vendorId(po.getVendor().getId())
            .vendorName(po.getVendor().getName())
            .orderDate(po.getOrderDate())
            .expectedDeliveryDate(po.getExpectedDeliveryDate())
            .totalAmount(po.getTotalAmount())
            .status(po.getStatus())
            .createdByUserId(po.getCreatedByUserId())
            .deliveryAddress(po.getDeliveryAddress())
            .paymentTerms(po.getPaymentTerms())
            .notes(po.getNotes())
            .lineItems(lineItems)
            .createdAt(po.getCreatedAt())
            .updatedAt(po.getUpdatedAt())
            .build();
    }

    private PoLineItemResponse lineItemToResponse(PoLineItem item) {
        return PoLineItemResponse.builder()
            .id(item.getId())
            .purchaseOrderId(item.getPurchaseOrder().getId())
            .prLineItemId(item.getPrLineItem() != null ? item.getPrLineItem().getId() : null)
            .lineNumber(item.getLineNumber())
            .description(item.getDescription())
            .orderedQuantity(item.getOrderedQuantity())
            .receivedQuantity(item.getReceivedQuantity())
            .unitOfMeasure(item.getUnitOfMeasure())
            .unitPrice(item.getUnitPrice())
            .totalPrice(item.getTotalPrice())
            .specifications(item.getSpecifications())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
