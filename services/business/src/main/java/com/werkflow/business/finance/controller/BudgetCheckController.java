package com.werkflow.business.finance.controller;

import com.werkflow.business.finance.dto.BudgetCheckRequest;
import com.werkflow.business.finance.dto.BudgetCheckResponse;
import com.werkflow.business.finance.service.BudgetCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller for budget availability checks
 * This endpoint is called by other services via RestServiceDelegate
 */
@Slf4j
@RestController
@RequestMapping("/budget")
@RequiredArgsConstructor
@Tag(name = "Budget Check", description = "Budget availability check APIs for cross-service calls")
public class BudgetCheckController {

    private final BudgetCheckService budgetCheckService;

    @PostMapping("/check")
    @Operation(
        summary = "Check budget availability",
        description = "Verify if sufficient budget is available for a requested amount. " +
                     "Called by other services via RestServiceDelegate in workflows."
    )
    public ResponseEntity<BudgetCheckResponse> checkBudgetAvailability(
            @Valid @RequestBody BudgetCheckRequest request) {

        log.info("Received budget check request - Department: {}, Amount: {}, Cost Center: {}",
                request.getDepartmentId(), request.getAmount(), request.getCostCenter());

        BudgetCheckResponse response = budgetCheckService.checkBudgetAvailability(request);

        log.info("Budget check result - Available: {}, Reason: {}",
                response.isAvailable(), response.getReason());

        return ResponseEntity.ok(response);
    }
}
