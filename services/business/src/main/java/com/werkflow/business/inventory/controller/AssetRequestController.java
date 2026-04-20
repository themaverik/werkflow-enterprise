package com.werkflow.business.inventory.controller;

import com.werkflow.business.inventory.dto.AssetRequestDto;
import com.werkflow.business.inventory.dto.AssetRequestResponse;
import com.werkflow.business.inventory.service.AssetRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory/asset-requests")
@RequiredArgsConstructor
public class AssetRequestController {

    private final AssetRequestService assetRequestService;

    @PostMapping
    public ResponseEntity<AssetRequestResponse> createRequest(@Valid @RequestBody AssetRequestDto dto) {
        return ResponseEntity.ok(assetRequestService.createRequest(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetRequestResponse> getRequest(@PathVariable Long id) {
        return ResponseEntity.ok(assetRequestService.getRequestById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AssetRequestResponse>> getByUser(@PathVariable String userId) {
        return ResponseEntity.ok(assetRequestService.getRequestsByUser(userId));
    }

    @GetMapping("/{id}/process-variables")
    public ResponseEntity<Map<String, Object>> getProcessVariables(@PathVariable Long id) {
        return ResponseEntity.ok(assetRequestService.toProcessVariables(id));
    }

    @PostMapping("/{id}/process-instance")
    public ResponseEntity<AssetRequestResponse> setProcessInstance(
            @PathVariable Long id, @RequestParam String processInstanceId) {
        return ResponseEntity.ok(assetRequestService.updateProcessInstanceId(id, processInstanceId));
    }

    @PostMapping("/callback/approve")
    public ResponseEntity<AssetRequestResponse> approve(
            @RequestParam String processInstanceId, @RequestParam String approvedByUserId) {
        return ResponseEntity.ok(assetRequestService.approveRequest(processInstanceId, approvedByUserId));
    }

    @PostMapping("/callback/reject")
    public ResponseEntity<AssetRequestResponse> reject(
            @RequestParam String processInstanceId,
            @RequestParam String approvedByUserId,
            @RequestParam String reason) {
        return ResponseEntity.ok(assetRequestService.rejectRequest(processInstanceId, approvedByUserId, reason));
    }

    @PostMapping("/callback/procurement")
    public ResponseEntity<AssetRequestResponse> initiateProcurement(
            @RequestParam String processInstanceId) {
        return ResponseEntity.ok(assetRequestService.initiateProcurement(processInstanceId));
    }
}
