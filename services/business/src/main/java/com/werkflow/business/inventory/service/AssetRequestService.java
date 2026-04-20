package com.werkflow.business.inventory.service;

import com.werkflow.business.inventory.dto.AssetRequestDto;
import com.werkflow.business.inventory.dto.AssetRequestResponse;
import com.werkflow.business.inventory.entity.AssetRequest;
import com.werkflow.business.inventory.entity.AssetRequestStatus;
import com.werkflow.business.inventory.entity.AssetDefinition;
import com.werkflow.business.inventory.entity.AssetCategory;
import com.werkflow.business.inventory.repository.AssetRequestRepository;
import com.werkflow.business.inventory.repository.AssetDefinitionRepository;
import com.werkflow.business.inventory.repository.AssetCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AssetRequestService {

    private final AssetRequestRepository assetRequestRepository;
    private final AssetDefinitionRepository assetDefinitionRepository;
    private final AssetCategoryRepository assetCategoryRepository;

    @Transactional
    public AssetRequestResponse createRequest(AssetRequestDto dto) {
        AssetRequest request = AssetRequest.builder()
            .requesterUserId(dto.getRequesterUserId())
            .requesterName(dto.getRequesterName())
            .requesterEmail(dto.getRequesterEmail())
            .requesterPhone(dto.getRequesterPhone())
            .departmentCode(dto.getDepartmentCode())
            .officeLocation(dto.getOfficeLocation())
            .assetDefinitionId(dto.getAssetDefinitionId())
            .assetCategoryId(dto.getAssetCategoryId())
            .quantity(dto.getQuantity())
            .procurementRequired(dto.getProcurementRequired() != null ? dto.getProcurementRequired() : false)
            .approxPrice(dto.getApproxPrice())
            .deliveryDate(dto.getDeliveryDate())
            .justification(dto.getJustification())
            .status(AssetRequestStatus.PENDING)
            .build();
        return toResponse(assetRequestRepository.save(request));
    }

    public AssetRequestResponse getRequestById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public List<AssetRequestResponse> getRequestsByUser(String userId) {
        return assetRequestRepository.findByRequesterUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssetRequestResponse updateProcessInstanceId(Long id, String processInstanceId) {
        AssetRequest req = findOrThrow(id);
        req.setProcessInstanceId(processInstanceId);
        return toResponse(assetRequestRepository.save(req));
    }

    @Transactional
    public AssetRequestResponse approveRequest(String processInstanceId, String approvedByUserId) {
        AssetRequest req = assetRequestRepository.findByProcessInstanceId(processInstanceId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found for process: " + processInstanceId));
        req.setStatus(AssetRequestStatus.APPROVED);
        req.setApprovedByUserId(approvedByUserId);
        return toResponse(assetRequestRepository.save(req));
    }

    @Transactional
    public AssetRequestResponse rejectRequest(String processInstanceId, String approvedByUserId, String reason) {
        AssetRequest req = assetRequestRepository.findByProcessInstanceId(processInstanceId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found for process: " + processInstanceId));
        req.setStatus(AssetRequestStatus.REJECTED);
        req.setApprovedByUserId(approvedByUserId);
        req.setRejectionReason(reason);
        return toResponse(assetRequestRepository.save(req));
    }

    @Transactional
    public AssetRequestResponse initiateProcurement(String processInstanceId) {
        AssetRequest req = assetRequestRepository.findByProcessInstanceId(processInstanceId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found for process: " + processInstanceId));
        req.setStatus(AssetRequestStatus.PROCUREMENT_INITIATED);
        return toResponse(assetRequestRepository.save(req));
    }

    public Map<String, Object> toProcessVariables(Long requestId) {
        AssetRequest req = findOrThrow(requestId);
        String custodianDeptCode = null;
        String custodianGroupName = null;
        if (req.getAssetCategoryId() != null) {
            AssetCategory category = assetCategoryRepository.findById(req.getAssetCategoryId()).orElse(null);
            if (category != null) {
                custodianDeptCode = category.getCustodianDeptCode();
                custodianGroupName = category.getResponsibleGroup();
            }
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("assetRequestId", req.getId());
        vars.put("requesterUserId", req.getRequesterUserId());
        vars.put("requesterName", req.getRequesterName());
        vars.put("requesterEmail", req.getRequesterEmail());
        vars.put("departmentCode", req.getDepartmentCode() != null ? req.getDepartmentCode() : "");
        vars.put("officeLocation", req.getOfficeLocation().name());
        vars.put("assetDefinitionId", req.getAssetDefinitionId() != null ? req.getAssetDefinitionId() : 0L);
        vars.put("assetCategoryId", req.getAssetCategoryId() != null ? req.getAssetCategoryId() : 0L);
        vars.put("custodianDeptCode", custodianDeptCode != null ? custodianDeptCode : "");
        vars.put("quantity", req.getQuantity());
        if (custodianGroupName != null) {
            vars.put("custodianGroupName", custodianGroupName);
        }
        vars.put("procurementGroupName", "Procurement");
        return vars;
    }

    private AssetRequest findOrThrow(Long id) {
        return assetRequestRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("AssetRequest not found: " + id));
    }

    private AssetRequestResponse toResponse(AssetRequest req) {
        String assetName = null;
        String categoryName = null;
        if (req.getAssetDefinitionId() != null) {
            assetName = assetDefinitionRepository.findById(req.getAssetDefinitionId())
                .map(AssetDefinition::getName).orElse(null);
        }
        if (req.getAssetCategoryId() != null) {
            categoryName = assetCategoryRepository.findById(req.getAssetCategoryId())
                .map(AssetCategory::getName).orElse(null);
        }
        return AssetRequestResponse.builder()
            .id(req.getId())
            .processInstanceId(req.getProcessInstanceId())
            .requesterUserId(req.getRequesterUserId())
            .requesterName(req.getRequesterName())
            .requesterEmail(req.getRequesterEmail())
            .requesterPhone(req.getRequesterPhone())
            .departmentCode(req.getDepartmentCode())
            .officeLocation(req.getOfficeLocation())
            .assetDefinitionId(req.getAssetDefinitionId())
            .assetName(assetName)
            .assetCategoryId(req.getAssetCategoryId())
            .assetCategoryName(categoryName)
            .quantity(req.getQuantity())
            .procurementRequired(req.getProcurementRequired())
            .approxPrice(req.getApproxPrice())
            .deliveryDate(req.getDeliveryDate())
            .justification(req.getJustification())
            .status(req.getStatus())
            .approvedByUserId(req.getApprovedByUserId())
            .rejectionReason(req.getRejectionReason())
            .createdAt(req.getCreatedAt())
            .updatedAt(req.getUpdatedAt())
            .build();
    }
}
