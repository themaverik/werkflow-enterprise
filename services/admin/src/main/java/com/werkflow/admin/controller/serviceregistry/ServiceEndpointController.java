package com.werkflow.admin.controller.serviceregistry;

import com.werkflow.admin.dto.serviceregistry.ErrorResponse;
import com.werkflow.admin.dto.serviceregistry.ServiceEndpointRequest;
import com.werkflow.admin.dto.serviceregistry.ServiceEndpointResponse;
import com.werkflow.admin.entity.serviceregistry.ServiceEndpoint;
import com.werkflow.admin.entity.serviceregistry.ServiceRegistry;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing service endpoints
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Service Endpoints", description = "Service endpoint management APIs")
public class ServiceEndpointController {

    private final ServiceRegistryService serviceRegistryService;
    private final ServiceRegistryMapper serviceRegistryMapper;

    /**
     * Create a new endpoint for a service
     * @param serviceId Service ID
     * @param request Endpoint details
     * @return The created endpoint
     */
    @PostMapping("/services/{serviceId}/endpoints")
    @Operation(summary = "Create endpoint", description = "Create a new endpoint for a service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Endpoint created successfully",
                    content = @Content(schema = @Schema(implementation = ServiceEndpointResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Endpoint already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceEndpointResponse> createEndpoint(
            @PathVariable @Parameter(description = "Service ID") UUID serviceId,
            @Valid @RequestBody ServiceEndpointRequest request) {
        log.info("Received request to create endpoint for service ID: {}", serviceId);

        ServiceRegistry service = serviceRegistryService.getServiceById(serviceId);
        ServiceEndpoint entity = serviceRegistryMapper.toEndpointEntity(request, service);
        ServiceEndpoint savedEntity = serviceRegistryService.createEndpoint(serviceId, entity);
        ServiceEndpointResponse response = serviceRegistryMapper.toEndpointResponse(savedEntity);

        log.info("Successfully created endpoint: {} {}", response.getHttpMethod(), response.getEndpointPath());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all endpoints for a service
     * @param serviceId Service ID
     * @return List of endpoints
     */
    @GetMapping("/services/{serviceId}/endpoints")
    @Operation(summary = "List endpoints", description = "Get all endpoints for a service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endpoints retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ServiceEndpointResponse>> getServiceEndpoints(
            @PathVariable @Parameter(description = "Service ID") UUID serviceId) {
        log.info("Received request to list endpoints for service ID: {}", serviceId);

        List<ServiceEndpoint> entities = serviceRegistryService.getServiceEndpoints(serviceId);
        List<ServiceEndpointResponse> response = serviceRegistryMapper.toEndpointResponseList(entities);

        log.info("Returning {} endpoints", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing endpoint
     * @param endpointId Endpoint ID
     * @param request Updated endpoint details
     * @return The updated endpoint
     */
    @PutMapping("/endpoints/{endpointId}")
    @Operation(summary = "Update endpoint", description = "Update an existing service endpoint")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endpoint updated successfully",
                    content = @Content(schema = @Schema(implementation = ServiceEndpointResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Endpoint not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Endpoint with same path and method already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceEndpointResponse> updateEndpoint(
            @PathVariable @Parameter(description = "Endpoint ID") UUID endpointId,
            @Valid @RequestBody ServiceEndpointRequest request) {
        log.info("Received request to update endpoint ID: {}", endpointId);

        ServiceEndpoint existingEntity = serviceRegistryService.getEndpointById(endpointId);
        serviceRegistryMapper.updateEndpointFromRequest(existingEntity, request);
        ServiceEndpoint updatedEntity = serviceRegistryService.updateEndpoint(endpointId, existingEntity);
        ServiceEndpointResponse response = serviceRegistryMapper.toEndpointResponse(updatedEntity);

        log.info("Successfully updated endpoint: {} {}", response.getHttpMethod(), response.getEndpointPath());
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an endpoint
     * @param endpointId Endpoint ID
     * @return No content
     */
    @DeleteMapping("/endpoints/{endpointId}")
    @Operation(summary = "Delete endpoint", description = "Delete a service endpoint")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Endpoint deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteEndpoint(
            @PathVariable @Parameter(description = "Endpoint ID") UUID endpointId) {
        log.info("Received request to delete endpoint ID: {}", endpointId);

        serviceRegistryService.deleteEndpoint(endpointId);

        log.info("Successfully deleted endpoint ID: {}", endpointId);
        return ResponseEntity.noContent().build();
    }
}
