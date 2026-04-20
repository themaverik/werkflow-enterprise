package com.werkflow.admin.controller.serviceregistry;

import com.werkflow.admin.dto.serviceregistry.ErrorResponse;
import com.werkflow.admin.dto.serviceregistry.ServiceEnvironmentUrlRequest;
import com.werkflow.admin.dto.serviceregistry.ServiceEnvironmentUrlResponse;
import com.werkflow.admin.entity.serviceregistry.ServiceEnvironmentUrl;
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
 * REST controller for managing service environment URLs
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Service Environment URLs", description = "Service environment URL management APIs")
public class ServiceEnvironmentUrlController {

    private final ServiceRegistryService serviceRegistryService;
    private final ServiceRegistryMapper serviceRegistryMapper;

    /**
     * Create a new environment URL for a service
     * @param serviceId Service ID
     * @param request Environment URL details
     * @return The created environment URL
     */
    @PostMapping("/services/{serviceId}/urls")
    @Operation(summary = "Create environment URL", description = "Create a new environment URL for a service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Environment URL created successfully",
                    content = @Content(schema = @Schema(implementation = ServiceEnvironmentUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceEnvironmentUrlResponse> createEnvironmentUrl(
            @PathVariable @Parameter(description = "Service ID") UUID serviceId,
            @Valid @RequestBody ServiceEnvironmentUrlRequest request) {
        log.info("Received request to create environment URL for service ID: {} in environment: {}",
                serviceId, request.getEnvironment());

        ServiceRegistry service = serviceRegistryService.getServiceById(serviceId);
        ServiceEnvironmentUrl entity = serviceRegistryMapper.toEnvironmentUrlEntity(request, service);
        ServiceEnvironmentUrl savedEntity = serviceRegistryService.createEnvironmentUrl(serviceId, entity);
        ServiceEnvironmentUrlResponse response = serviceRegistryMapper.toEnvironmentUrlResponse(savedEntity);

        log.info("Successfully created environment URL for {} environment", response.getEnvironment());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all environment URLs for a service
     * @param serviceId Service ID
     * @return List of environment URLs
     */
    @GetMapping("/services/{serviceId}/urls")
    @Operation(summary = "List environment URLs", description = "Get all environment URLs for a service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Environment URLs retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Service not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ServiceEnvironmentUrlResponse>> getEnvironmentUrls(
            @PathVariable @Parameter(description = "Service ID") UUID serviceId) {
        log.info("Received request to list environment URLs for service ID: {}", serviceId);

        List<ServiceEnvironmentUrl> entities = serviceRegistryService.getEnvironmentUrls(serviceId);
        List<ServiceEnvironmentUrlResponse> response = serviceRegistryMapper.toEnvironmentUrlResponseList(entities);

        log.info("Returning {} environment URLs", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing environment URL
     * @param urlId Environment URL ID
     * @param request Updated environment URL details
     * @return The updated environment URL
     */
    @PutMapping("/service-urls/{urlId}")
    @Operation(summary = "Update environment URL", description = "Update an existing service environment URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Environment URL updated successfully",
                    content = @Content(schema = @Schema(implementation = ServiceEnvironmentUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Environment URL not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ServiceEnvironmentUrlResponse> updateEnvironmentUrl(
            @PathVariable @Parameter(description = "Environment URL ID") UUID urlId,
            @Valid @RequestBody ServiceEnvironmentUrlRequest request) {
        log.info("Received request to update environment URL ID: {}", urlId);

        ServiceEnvironmentUrl existingEntity = serviceRegistryService.getEnvironmentUrlById(urlId);
        serviceRegistryMapper.updateEnvironmentUrlFromRequest(existingEntity, request);
        ServiceEnvironmentUrl updatedEntity = serviceRegistryService.updateEnvironmentUrl(urlId, existingEntity);
        ServiceEnvironmentUrlResponse response = serviceRegistryMapper.toEnvironmentUrlResponse(updatedEntity);

        log.info("Successfully updated environment URL for {} environment", response.getEnvironment());
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an environment URL
     * @param urlId Environment URL ID
     * @return No content
     */
    @DeleteMapping("/service-urls/{urlId}")
    @Operation(summary = "Delete environment URL", description = "Delete a service environment URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Environment URL deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Environment URL not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteEnvironmentUrl(
            @PathVariable @Parameter(description = "Environment URL ID") UUID urlId) {
        log.info("Received request to delete environment URL ID: {}", urlId);

        serviceRegistryService.deleteEnvironmentUrl(urlId);

        log.info("Successfully deleted environment URL ID: {}", urlId);
        return ResponseEntity.noContent().build();
    }
}
