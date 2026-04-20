package com.werkflow.business.procurement.service;

import com.werkflow.business.procurement.dto.VendorRequest;
import com.werkflow.business.procurement.dto.VendorResponse;
import com.werkflow.business.procurement.entity.Vendor;
import com.werkflow.business.procurement.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorService {
    private final VendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public List<VendorResponse> getAllVendors() {
        return vendorRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VendorResponse getVendorById(Long id) {
        return vendorRepository.findById(id).map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Vendor not found: " + id));
    }

    @Transactional
    public VendorResponse createVendor(VendorRequest request) {
        Vendor vendor = Vendor.builder()
            .name(request.getName())
            .contactPerson(request.getContactPerson())
            .email(request.getEmail())
            .phone(request.getPhone())
            .address(request.getAddress())
            .taxId(request.getTaxId())
            .paymentTerms(request.getPaymentTerms())
            .status(request.getStatus() != null ? request.getStatus() : Vendor.VendorStatus.ACTIVE)
            .rating(request.getRating())
            .notes(request.getNotes())
            .metadata(request.getMetadata())
            .build();
        return toResponse(vendorRepository.save(vendor));
    }

    private VendorResponse toResponse(Vendor vendor) {
        return VendorResponse.builder()
            .id(vendor.getId())
            .name(vendor.getName())
            .contactPerson(vendor.getContactPerson())
            .email(vendor.getEmail())
            .phone(vendor.getPhone())
            .address(vendor.getAddress())
            .taxId(vendor.getTaxId())
            .paymentTerms(vendor.getPaymentTerms())
            .status(vendor.getStatus())
            .rating(vendor.getRating())
            .notes(vendor.getNotes())
            .metadata(vendor.getMetadata())
            .createdAt(vendor.getCreatedAt())
            .updatedAt(vendor.getUpdatedAt())
            .build();
    }
}
