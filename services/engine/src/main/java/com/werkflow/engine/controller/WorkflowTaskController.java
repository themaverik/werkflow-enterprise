package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.TaskListResponse;
import com.werkflow.engine.dto.TaskQueryParams;
import com.werkflow.engine.dto.TaskResponse;
import com.werkflow.engine.service.TaskService;
import com.werkflow.engine.service.WorkflowTaskService;
import com.werkflow.engine.util.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for workflow task management
 * Provides endpoints for retrieving user tasks and group candidate tasks
 */
@RestController
@RequestMapping("/workflows/tasks")
@RequiredArgsConstructor
@Tag(name = "Workflow Tasks", description = "User task management for workflows")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
public class WorkflowTaskController {

    private final WorkflowTaskService workflowTaskService;
    private final TaskService taskService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Get tasks assigned to the authenticated user
     * @param jwt JWT token from authentication
     * @param params Query parameters for filtering, sorting, and pagination
     * @return Paginated list of user's assigned tasks
     */
    @GetMapping("/my-tasks")
    @Operation(
            summary = "Get my tasks",
            description = "Retrieve all tasks assigned to the authenticated user with pagination, filtering, and sorting. " +
                    "Only tasks directly assigned to the user are returned."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tasks retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TaskListResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Invalid query parameters"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error"
            )
    })
    public ResponseEntity<TaskListResponse> getMyTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Page number (zero-based)") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field and direction (e.g., 'createTime,desc', 'priority,asc')") @RequestParam(defaultValue = "createTime,desc") String sort,
            @Parameter(description = "Search in task name or description") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by priority (0-100)") @RequestParam(required = false) Integer priority,
            @Parameter(description = "Filter by process definition key") @RequestParam(required = false) String processDefinitionKey,
            @Parameter(description = "Filter tasks due before this date (ISO 8601 format)") @RequestParam(required = false) String dueBefore,
            @Parameter(description = "Filter tasks due after this date (ISO 8601 format)") @RequestParam(required = false) String dueAfter) {

        log.info("GET /workflows/tasks/my-tasks - User: {}", jwt.getClaimAsString("preferred_username"));

        // Build query parameters
        TaskQueryParams params = TaskQueryParams.builder()
                .page(page)
                .size(size)
                .sort(sort)
                .search(search)
                .priority(priority)
                .processDefinitionKey(processDefinitionKey)
                .build();

        // Extract user context from JWT
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        // Fetch tasks
        TaskListResponse response = workflowTaskService.getMyTasks(userContext, params);

        log.info("Returning {} tasks (page {}/{}) for user: {}",
                response.getContent().size(),
                response.getPage().getNumber() + 1,
                response.getPage().getTotalPages(),
                userContext.getUserId());

        return ResponseEntity.ok(response);
    }

    /**
     * Get tasks available to the user's groups (candidate tasks)
     * @param jwt JWT token from authentication
     * @param params Query parameters for filtering, sorting, and pagination
     * @return Paginated list of group candidate tasks
     */
    @GetMapping("/group-tasks")
    @Operation(
            summary = "Get group tasks",
            description = "Retrieve all tasks available to the authenticated user's groups (candidate tasks). " +
                    "By default, only unassigned tasks are returned. Set includeAssigned=true to include assigned tasks."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tasks retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TaskListResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - User not in requested group"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Invalid query parameters"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error"
            )
    })
    public ResponseEntity<TaskListResponse> getGroupTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Page number (zero-based)") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field and direction (e.g., 'createTime,desc', 'priority,asc')") @RequestParam(defaultValue = "createTime,desc") String sort,
            @Parameter(description = "Search in task name or description") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by priority (0-100)") @RequestParam(required = false) Integer priority,
            @Parameter(description = "Filter by process definition key") @RequestParam(required = false) String processDefinitionKey,
            @Parameter(description = "Filter by specific group (must be user's group)") @RequestParam(required = false) String groupId,
            @Parameter(description = "Include already assigned tasks") @RequestParam(defaultValue = "false") Boolean includeAssigned,
            @Parameter(description = "Filter tasks due before this date (ISO 8601 format)") @RequestParam(required = false) String dueBefore,
            @Parameter(description = "Filter tasks due after this date (ISO 8601 format)") @RequestParam(required = false) String dueAfter) {

        log.info("GET /workflows/tasks/group-tasks - User: {}", jwt.getClaimAsString("preferred_username"));

        // Build query parameters
        TaskQueryParams params = TaskQueryParams.builder()
                .page(page)
                .size(size)
                .sort(sort)
                .search(search)
                .priority(priority)
                .processDefinitionKey(processDefinitionKey)
                .groupId(groupId)
                .includeAssigned(includeAssigned)
                .build();

        // Extract user context from JWT
        JwtUserContext userContext = jwtClaimsExtractor.extractUserContext(jwt);

        // Fetch group tasks
        TaskListResponse response = workflowTaskService.getGroupTasks(userContext, params);

        log.info("Returning {} group tasks (page {}/{}) for user: {}",
                response.getContent().size(),
                response.getPage().getNumber() + 1,
                response.getPage().getTotalPages(),
                userContext.getUserId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/process/{processInstanceId}")
    @Operation(summary = "Get tasks for a process instance")
    public ResponseEntity<List<TaskResponse>> getTasksByProcessInstance(
            @Parameter(description = "Process instance ID") @PathVariable String processInstanceId) {
        log.info("GET /workflows/tasks/process/{}", processInstanceId);
        List<TaskResponse> responses = taskService.getTasksByProcessInstanceId(processInstanceId);
        return ResponseEntity.ok(responses);
    }
}
