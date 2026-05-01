package com.werkflow.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Tenant internal endpoints. cross-dept-threshold removed (ADR-002) —
 * DOA thresholds are now stored as configuration_variables per tenant.
 */
@RestController
@RequestMapping("/api/internal/tenants")
@RequiredArgsConstructor
public class TenantController {
    // Retained as placeholder; individual tenant operations delegated to service-specific controllers.
}
