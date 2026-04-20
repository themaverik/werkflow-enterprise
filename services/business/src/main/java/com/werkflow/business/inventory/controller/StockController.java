package com.werkflow.business.inventory.controller;

import com.werkflow.business.hr.entity.OfficeLocation;
import com.werkflow.business.inventory.dto.StockAvailabilityResponse;
import com.werkflow.business.inventory.service.StockService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory/stock")
@RequiredArgsConstructor
@Validated
public class StockController {

    private final StockService stockService;

    @GetMapping("/availability")
    public ResponseEntity<StockAvailabilityResponse> checkAvailability(
            @RequestParam Long assetDefinitionId,
            @RequestParam(defaultValue = "1") Integer quantity,
            @RequestParam(required = false) OfficeLocation officeLocation) {
        return ResponseEntity.ok(stockService.checkAvailability(assetDefinitionId, quantity, officeLocation));
    }
}
