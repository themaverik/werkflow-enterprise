package com.werkflow.admin.controller.serviceregistry;

import com.werkflow.admin.dto.serviceregistry.*;
import com.werkflow.admin.entity.serviceregistry.*;
import com.werkflow.admin.mapper.ServiceRegistryMapper;
import com.werkflow.admin.service.ServiceRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for service registry management
 * Provides endpoints for CRUD operations on service registrations
 */
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Service Registry", description = "Service registry management APIs")
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;
    private final ServiceRegistryMapper serviceRegistryMapper;

    /**
     * Create a new service registration
     * @param request Service registration details
     * @return The created service
     */
    @PostMapping
    @Operation(summary = "Create service", description = "Register a new service in the service registry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Service created successfully",
                    content = @Content(schema = @Schema(implementation = ServiceRegistryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Service with same name already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceRegistryResponse> createService(
            @Valid @RequestBody ServiceRegistryRequest request) {
        log.info("Received request to create service: {}", request.getServiceName());

        ServiceRegistry entity = serviceRegistryMapper.toEntity(request);
        ServiceRegistry savedEntity = serviceRegistryService.registerService(entity);
        ServiceRegistryResponse response = serviceRegistryMapper.toResponse(savedEntity);

        log.info("Successfully created service with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all services with pagination
     * @param pageable Pagination parameters
     * @return Page of services
     */
    @GetMapping
    @Operation(summary = "List services", description = "Get all registered services with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Services retrieved successfully")
    })
    public ResponseEntity<Page<ServiceRegistryResponse>> getAllServices(
            @PageableDefault(size = 20, sort = "serviceName", direction = Sort.Direction.ASC)
            @Parameter(description = "Pagination parameters (page, size, sort)")
            Pageable pageable) {
        log.info("Received request to list all services - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<ServiceRegistry> entities = serviceRegistryService.getAllServices(pageable);
        Page<ServiceRegistryResponse> response = entities.map(serviceRegistryMapper::toResponse);

        log.info("Returning {} services out of {} total", response.getNumberOfElements(), response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * Get service by ID
     * @param id Service ID
     * @return The service
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID", description = "Retrieve service details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ServiceRegistryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceRegistryResponse> getServiceById(
            @PathVariable @Parameter(description = "Service ID") UUID id) {
        log.info("Received request to get service by ID: {}", id);

        ServiceRegistry entity = serviceRegistryService.getServiceById(id);
        ServiceRegistryResponse response = serviceRegistryMapper.toResponse(entity);

        log.info("Returning service: {}", response.getServiceName());
        return ResponseEntity.ok(response);
    }

    /**
     * Get service by name
     * @param serviceName Service name
     * @return The service
     */
    @GetMapping("/by-name/{serviceName}")
    @Operation(summary = "Get service by name", description = "Retrieve service details by service name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ServiceRegistryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceRegistryResponse> getServiceByName(
            @PathVariable @Parameter(description = "Service name") String serviceName) {
        log.info("Received request to get service by name: {}", serviceName);

        ServiceRegistry entity = serviceRegistryService.getServiceByName(serviceName);
        ServiceRegistryResponse response = serviceRegistryMapper.toResponse(entity);

        log.info("Returning service with ID: {}", response.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing service
     * @param id Service ID
     * @param request Updated service details
     * @return The updated service
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update service", description = "Update an existing service registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service updated successfully",
                    content = @Content(schema = @Schema(implementation = ServiceRegistryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Service with same name already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceRegistryResponse> updateService(
            @PathVariable @Parameter(description = "Service ID") UUID id,
            @Valid @RequestBody ServiceRegistryRequest request) {
        log.info("Received request to update service ID: {}", id);

        ServiceRegistry existingEntity = serviceRegistryService.getServiceById(id);
        serviceRegistryMapper.updateEntityFromRequest(existingEntity, request);
        ServiceRegistry updatedEntity = serviceRegistryService.updateService(id, existingEntity);
        ServiceRegistryResponse response = serviceRegistryMapper.toResponse(updatedEntity);

        log.info("Successfully updated service: {}", response.getServiceName());
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a service
     * @param id Service ID
     * @return No content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete service", description = "Delete a service from the registry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Service deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteService(
            @PathVariable @Parameter(description = "Service ID") UUID id) {
        log.info("Received request to delete service ID: {}", id);

        serviceRegistryService.deleteService(id);

        log.info("Successfully deleted service ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trigger health check for a service in a specific environment
     * @param id Service ID
     * @param environment Environment to check
     * @return Health check result
     */
    @PostMapping("/{id}/health-check")
    @Operation(summary = "Trigger health check", description = "Perform a health check on a service in a specific environment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health check completed",
                    content = @Content(schema = @Schema(implementation = HealthCheckResultResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Environment not configured",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<HealthCheckResultResponse> performHealthCheck(
            @PathVariable @Parameter(description = "Service ID") UUID id,
            @RequestParam @Parameter(description = "Environment (local, development, staging, production)")
            Environment environment) {
        log.info("Received request to perform health check for service ID: {} in environment: {}", id, environment);

        ServiceHealthCheck healthCheck = serviceRegistryService.performHealthCheck(id, environment);
        HealthCheckResultResponse response = serviceRegistryMapper.toHealthCheckResponse(healthCheck);

        log.info("Health check completed with status: {}", response.getStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * Get health check history for a service
     * @param id Service ID
     * @param pageable Pagination parameters
     * @return Page of health check results
     */
    @GetMapping("/{id}/health-history")
    @Operation(summary = "Get health check history", description = "Retrieve health check history for a service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health check history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<HealthCheckResultResponse>> getHealthCheckHistory(
            @PathVariable @Parameter(description = "Service ID") UUID id,
            @PageableDefault(size = 20, sort = "checkedAt", direction = Sort.Direction.DESC)
            @Parameter(description = "Pagination parameters (page, size, sort)")
            Pageable pageable) {
        log.info("Received request to get health check history for service ID: {}", id);

        Page<ServiceHealthCheck> entities = serviceRegistryService.getHealthCheckHistory(id, pageable);
        Page<HealthCheckResultResponse> response = entities.map(serviceRegistryMapper::toHealthCheckResponse);

        log.info("Returning {} health check records out of {} total",
                response.getNumberOfElements(), response.getTotalElements());
        return ResponseEntity.ok(response);
    }
}
