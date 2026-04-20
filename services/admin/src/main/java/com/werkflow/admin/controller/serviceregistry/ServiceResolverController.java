package com.werkflow.admin.controller.serviceregistry;

import com.werkflow.admin.dto.serviceregistry.ErrorResponse;
import com.werkflow.admin.dto.serviceregistry.ServiceResolverResponse;
import com.werkflow.admin.entity.serviceregistry.Environment;
import com.werkflow.admin.entity.serviceregistry.ServiceRegistry;
import com.werkflow.admin.service.ServiceRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for service URL resolution
 * Used by RestServiceDelegate to resolve service URLs dynamically
 */
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Service Resolver", description = "Service URL resolution APIs for workflow delegates")
public class ServiceResolverController {

    private final ServiceRegistryService serviceRegistryService;

    /**
     * Resolve service URL for a given service name and environment
     * This endpoint is used by RestServiceDelegate to get the full service URL
     *
     * @param serviceName The service name to resolve
     * @param environment The environment (local, development, staging, production)
     * @return The resolved service URL details
     */
    @GetMapping("/resolve/{serviceName}")
    @Operation(
            summary = "Resolve service URL",
            description = "Resolve the full service URL for a given service name and environment. " +
                    "This endpoint is primarily used by RestServiceDelegate in BPMN workflows to " +
                    "dynamically resolve service URLs at runtime."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service URL resolved successfully",
                    content = @Content(schema = @Schema(implementation = ServiceResolverResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Environment not configured for this service",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceResolverResponse> resolveServiceUrl(
            @PathVariable @Parameter(description = "Service name (e.g., hr-service, finance-service)")
            String serviceName,
            @RequestParam(defaultValue = "development")
            @Parameter(description = "Environment (local, development, staging, production)")
            Environment environment) {

        log.info("Received request to resolve URL for service: {} in environment: {}", serviceName, environment);

        // Get service details
        ServiceRegistry service = serviceRegistryService.getServiceByName(serviceName);

        // Resolve full URL (base URL + base path)
        String fullUrl = serviceRegistryService.resolveServiceUrl(serviceName, environment);

        // Build response
        ServiceResolverResponse response = ServiceResolverResponse.builder()
                .serviceName(serviceName)
                .environment(environment)
                .resolvedUrl(fullUrl)
                .basePath(service.getBasePath())
                .fullUrl(fullUrl)
                .build();

        log.info("Successfully resolved URL for {}: {}", serviceName, fullUrl);
        return ResponseEntity.ok(response);
    }
}
