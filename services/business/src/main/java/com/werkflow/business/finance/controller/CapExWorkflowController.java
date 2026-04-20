package com.werkflow.business.finance.controller;

import com.werkflow.business.finance.service.CapExWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for CapEx workflow integration.
 * Called by the Engine service via RestServiceDelegate during BPMN execution.
 */
@Slf4j
@RestController
@RequestMapping("/workflow/capex")
@RequiredArgsConstructor
@Tag(name = "CapEx Workflow", description = "Workflow integration APIs for CapEx approval process")
public class CapExWorkflowController {

    private final CapExWorkflowService capExWorkflowService;

    @PostMapping("/create-request")
    @Operation(summary = "Create CapEx request from workflow")
    public ResponseEntity<Map<String, Object>> createRequest(@RequestBody Map<String, Object> request) {
        log.info("Workflow: Creating CapEx request");
        Map<String, Object> response = capExWorkflowService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/check-budget")
    @Operation(summary = "Check budget availability for CapEx request")
    public ResponseEntity<Map<String, Object>> checkBudget(@RequestBody Map<String, Object> request) {
        log.info("Workflow: Checking budget availability");
        return ResponseEntity.ok(capExWorkflowService.checkBudget(request));
    }

    @PutMapping("/update-status")
    @Operation(summary = "Update CapEx request status from workflow")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> request) {
        log.info("Workflow: Updating CapEx status");
        return ResponseEntity.ok(capExWorkflowService.updateStatus(request));
    }

    @PostMapping("/allocate")
    @Operation(summary = "Allocate budget for approved CapEx request")
    public ResponseEntity<Map<String, Object>> allocateBudget(@RequestBody Map<String, Object> request) {
        log.info("Workflow: Allocating budget");
        return ResponseEntity.ok(capExWorkflowService.allocateBudget(request));
    }
}
