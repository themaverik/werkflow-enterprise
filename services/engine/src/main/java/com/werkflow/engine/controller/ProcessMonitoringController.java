package com.werkflow.engine.controller;

import com.werkflow.engine.dto.*;
import com.werkflow.engine.service.ProcessMonitoringService;
import com.werkflow.engine.util.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for process monitoring and tracking
 * Provides endpoints for retrieving process instance details, tasks, and historical events
 */
@RestController
@RequestMapping("/workflows/processes")
@RequiredArgsConstructor
@Tag(name = "Process Monitoring", description = "APIs for monitoring and tracking workflow process instances")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
public class ProcessMonitoringController {

    private final ProcessMonitoringService processMonitoringService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Get detailed information about a process instance
     * @param jwt JWT token from authentication
     * @param processInstanceId Process instance ID
     * @param includeVariables Whether to include process variables
     * @return Process instance details
     */
    @GetMapping("/{processInstanceId}")
    @Operation(
            summary = "Get process instance details",
            description = "Retrieve full details of a specific process instance including current status, tasks, and optionally variables. " +
                    "User can only access processes they initiated or are involved in."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Process instance details retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProcessInstanceDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User not authorized to view this process"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - Process instance not found"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error"
            )
    })
    public ResponseEntity<ProcessInstanceDTO> getProcessInstanceDetails(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId,
            @Parameter(description = "Include process variables in response")
            @RequestParam(defaultValue = "false") Boolean includeVariables) {

        log.info("GET /workflows/processes/{} - User: {}, includeVariables: {}",
                processInstanceId, jwt.getClaimAsString("preferred_username"), includeVariables);

        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        ProcessInstanceDTO processInstance = processMonitoringService.getProcessInstanceDetails(
                processInstanceId, includeVariables, userContext);

        log.info("Returning process instance details: {} (status: {})",
                processInstance.getId(), processInstance.getStatus());

        return ResponseEntity.ok(processInstance);
    }

    /**
     * Get all tasks for a process instance
     * @param jwt JWT token from authentication
     * @param processInstanceId Process instance ID
     * @param includeHistory Whether to include completed tasks
     * @param status Filter by status (ACTIVE, COMPLETED, or null for all)
     * @return List of tasks
     */
    @GetMapping("/{processInstanceId}/tasks")
    @Operation(
            summary = "Get process instance tasks",
            description = "Retrieve all tasks (active and/or completed) for a specific process instance. " +
                    "Supports filtering by task status. User must be authorized to view the process."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tasks retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProcessTaskHistoryDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User not authorized to view this process"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - Process instance not found"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error"
            )
    })
    public ResponseEntity<List<ProcessTaskHistoryDTO>> getProcessInstanceTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId,
            @Parameter(description = "Include completed tasks in response")
            @RequestParam(defaultValue = "true") Boolean includeHistory,
            @Parameter(description = "Filter by task status (ACTIVE, COMPLETED, or null for all)")
            @RequestParam(required = false) String status) {

        log.info("GET /workflows/processes/{}/tasks - User: {}, includeHistory: {}, status: {}",
                processInstanceId, jwt.getClaimAsString("preferred_username"), includeHistory, status);

        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        List<ProcessTaskHistoryDTO> tasks = processMonitoringService.getProcessInstanceTasks(
                processInstanceId, includeHistory, status, userContext);

        log.info("Returning {} tasks for process instance: {}", tasks.size(), processInstanceId);

        return ResponseEntity.ok(tasks);
    }

    /**
     * Get historical timeline of events for a process instance
     * @param jwt JWT token from authentication
     * @param processInstanceId Process instance ID
     * @param page Page number (zero-based)
     * @param size Page size
     * @return Process history with pagination
     */
    @GetMapping("/{processInstanceId}/history")
    @Operation(
            summary = "Get process instance history",
            description = "Retrieve chronological timeline of all events for a process instance including " +
                    "task assignments, completions, and other activities. Supports pagination."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "History retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProcessHistoryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User not authorized to view this process"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - Process instance not found"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error"
            )
    })
    public ResponseEntity<ProcessHistoryResponse> getProcessInstanceHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Process instance ID", required = true)
            @PathVariable String processInstanceId,
            @Parameter(description = "Page number (zero-based)")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") Integer size) {

        log.info("GET /workflows/processes/{}/history - User: {}, page: {}, size: {}",
                processInstanceId, jwt.getClaimAsString("preferred_username"), page, size);

        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        ProcessHistoryResponse history = processMonitoringService.getProcessInstanceHistory(
                processInstanceId, page, size, userContext);

        log.info("Returning {} events (page {}/{}) for process instance: {}",
                history.getEvents().size(), page + 1, history.getTotalPages(), processInstanceId);

        return ResponseEntity.ok(history);
    }

    /**
     * Find process instance by business key
     * @param jwt JWT token from authentication
     * @param businessKey Business key (e.g., "PR-2025-00042", "CAPEX-2025-001")
     * @return Process instance details
     */
    @GetMapping("/by-key/{businessKey}")
    @Operation(
            summary = "Get process instance by business key",
            description = "Find and retrieve process instance details using a business key. " +
                    "Business keys are typically request IDs, requisition numbers, or other unique identifiers. " +
                    "User must be authorized to view the process."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Process instance found and retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProcessInstanceDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User not authorized to view this process"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - No process found with this business key"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error"
            )
    })
    public ResponseEntity<ProcessInstanceDTO> getProcessByBusinessKey(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Business key (e.g., PR-2025-00042, CAPEX-2025-001)", required = true)
            @PathVariable String businessKey) {

        log.info("GET /workflows/processes/by-key/{} - User: {}",
                businessKey, jwt.getClaimAsString("preferred_username"));

        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        ProcessInstanceDTO processInstance = processMonitoringService.getProcessByBusinessKey(
                businessKey, userContext);

        log.info("Found process instance: {} for business key: {}",
                processInstance.getId(), businessKey);

        return ResponseEntity.ok(processInstance);
    }
}
