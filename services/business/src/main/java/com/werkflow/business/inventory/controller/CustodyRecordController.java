package com.werkflow.business.inventory.controller;

import com.werkflow.business.inventory.dto.CustodyRecordRequestDto;
import com.werkflow.business.inventory.dto.CustodyRecordResponseDto;
import com.werkflow.business.inventory.entity.AssetInstance;
import com.werkflow.business.inventory.entity.CustodyRecord;
import com.werkflow.business.inventory.service.AssetInstanceService;
import com.werkflow.business.inventory.service.CustodyRecordService;
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
 * REST Controller for CustodyRecord operations
 */
@RestController
@RequestMapping("/custody-records")
@RequiredArgsConstructor
@Tag(name = "Custody Records", description = "Inter-department asset custody management APIs")
public class CustodyRecordController {

    private final CustodyRecordService custodyService;
    private final AssetInstanceService assetService;

    @PostMapping
    @Operation(summary = "Create custody record", description = "Create a new custody record for asset assignment")
    public ResponseEntity<CustodyRecordResponseDto> createCustodyRecord(@Valid @RequestBody CustodyRecordRequestDto requestDto) {
        AssetInstance asset = assetService.getInstanceById(requestDto.getAssetInstanceId());

        CustodyRecord record = CustodyRecord.builder()
            .assetInstance(asset)
            .custodianDeptId(requestDto.getCustodianDeptId())
            .custodianUserId(requestDto.getCustodianUserId())
            .physicalLocation(requestDto.getPhysicalLocation())
            .custodyType(CustodyRecord.CustodyType.valueOf(requestDto.getCustodyType()))
            .startDate(requestDto.getStartDate())
            .endDate(requestDto.getEndDate())
            .assignedByUserId(requestDto.getAssignedByUserId())
            .returnCondition(requestDto.getReturnCondition() != null ?
                AssetInstance.AssetCondition.valueOf(requestDto.getReturnCondition()) : null)
            .notes(requestDto.getNotes())
            .build();

        CustodyRecord created = custodyService.createCustodyRecord(record);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get custody record by ID", description = "Retrieve a custody record by its ID")
    public ResponseEntity<CustodyRecordResponseDto> getCustodyRecordById(@PathVariable Long id) {
        CustodyRecord record = custodyService.getCustodyRecordById(id);
        return ResponseEntity.ok(mapToResponse(record));
    }

    @GetMapping("/current/asset/{assetId}")
    @Operation(summary = "Get current custody", description = "Get the current active custody record for an asset")
    public ResponseEntity<CustodyRecordResponseDto> getCurrentCustody(@PathVariable Long assetId) {
        CustodyRecord record = custodyService.getCurrentCustody(assetId);
        return ResponseEntity.ok(mapToResponse(record));
    }

    @GetMapping("/history/asset/{assetId}")
    @Operation(summary = "Get custody history", description = "Get custody history for an asset")
    public ResponseEntity<List<CustodyRecordResponseDto>> getCustodyHistory(@PathVariable Long assetId) {
        List<CustodyRecord> records = custodyService.getCustodyHistory(assetId);
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping
    @Operation(summary = "Get all custody records", description = "Retrieve all custody records")
    public ResponseEntity<List<CustodyRecordResponseDto>> getAllCustodyRecords() {
        List<CustodyRecord> records = custodyService.getAllCustodyRecords();
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active custody records", description = "Retrieve all active (current) custody records")
    public ResponseEntity<List<CustodyRecordResponseDto>> getActiveCustodyRecords() {
        List<CustodyRecord> records = custodyService.getActiveCustodyRecords();
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/department/{deptId}")
    @Operation(summary = "Get custody by department", description = "Get assets under custody of a department")
    public ResponseEntity<List<CustodyRecordResponseDto>> getCustodyByDepartment(@PathVariable Long deptId) {
        List<CustodyRecord> records = custodyService.getCustodyByDepartment(deptId);
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get custody by user", description = "Get assets under custody of a user")
    public ResponseEntity<List<CustodyRecordResponseDto>> getCustodyByUser(@PathVariable Long userId) {
        List<CustodyRecord> records = custodyService.getCustodyByUser(userId);
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/type/{custodyType}")
    @Operation(summary = "Get custody by type", description = "Get custody records by custody type")
    public ResponseEntity<List<CustodyRecordResponseDto>> getCustodyByType(@PathVariable String custodyType) {
        List<CustodyRecord> records = custodyService.getCustodyByType(custodyType);
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/overdue-temporary")
    @Operation(summary = "Get overdue temporary custody", description = "Get overdue temporary custody records")
    public ResponseEntity<List<CustodyRecordResponseDto>> getOverdueTemporaryCustody() {
        List<CustodyRecord> records = custodyService.getOverdueTemporaryCustody();
        return ResponseEntity.ok(records.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @PutMapping("/{id}/end")
    @Operation(summary = "End custody", description = "End a custody record (asset return)")
    public ResponseEntity<CustodyRecordResponseDto> endCustody(
            @PathVariable Long id,
            @RequestParam String returnCondition) {
        CustodyRecord updated = custodyService.endCustody(id, returnCondition);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update custody record", description = "Update a custody record")
    public ResponseEntity<CustodyRecordResponseDto> updateCustodyRecord(
            @PathVariable Long id,
            @Valid @RequestBody CustodyRecordRequestDto requestDto) {
        CustodyRecord recordDetails = CustodyRecord.builder()
            .physicalLocation(requestDto.getPhysicalLocation())
            .custodyType(CustodyRecord.CustodyType.valueOf(requestDto.getCustodyType()))
            .notes(requestDto.getNotes())
            .build();

        CustodyRecord updated = custodyService.updateCustodyRecord(id, recordDetails);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    private CustodyRecordResponseDto mapToResponse(CustodyRecord record) {
        return CustodyRecordResponseDto.builder()
            .id(record.getId())
            .assetInstanceId(record.getAssetInstance().getId())
            .assetTag(record.getAssetInstance().getAssetTag())
            .custodianDeptId(record.getCustodianDeptId())
            .custodianUserId(record.getCustodianUserId())
            .physicalLocation(record.getPhysicalLocation())
            .custodyType(record.getCustodyType().toString())
            .startDate(record.getStartDate())
            .endDate(record.getEndDate())
            .assignedByUserId(record.getAssignedByUserId())
            .returnCondition(record.getReturnCondition() != null ? record.getReturnCondition().toString() : null)
            .notes(record.getNotes())
            .createdAt(record.getCreatedAt())
            .isActive(record.getEndDate() == null)
            .build();
    }
}
