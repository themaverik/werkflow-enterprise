package com.werkflow.engine.controller;

import com.werkflow.engine.dto.*;
import com.werkflow.engine.service.TaskFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for task form operations.
 * Handles form retrieval and submission for Flowable tasks.
 */
@RestController
@RequestMapping({"/api/tasks", "/api/v1/tasks"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Task Forms", description = "Task form management and submission API")
public class TaskFormController {

    private final TaskFormService taskFormService;

    /**
     * Get form schema and variables for a task
     * @param taskId The task ID
     * @return Task form response with schema and variables
     */
    @GetMapping("/{taskId}/form")
    @Operation(summary = "Get task form", description = "Retrieve form schema and initial values for a task")
    public ResponseEntity<TaskFormResponse> getTaskForm(
            @Parameter(description = "Task ID")
            @PathVariable String taskId) {
        log.info("Getting form for task: {}", taskId);
        TaskFormResponse response = taskFormService.getTaskForm(taskId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get form variables only (without schema)
     * @param taskId The task ID
     * @return Map of variables
     */
    @GetMapping("/{taskId}/form/variables")
    @Operation(summary = "Get task form variables", description = "Retrieve form variable values for a task")
    public ResponseEntity<Map<String, Object>> getTaskFormVariables(
            @Parameter(description = "Task ID")
            @PathVariable String taskId) {
        log.info("Getting form variables for task: {}", taskId);
        Map<String, Object> variables = taskFormService.getTaskFormVariables(taskId);
        return ResponseEntity.ok(variables);
    }

    /**
     * Validate form data without submitting
     * @param taskId The task ID
     * @param formData The form data to validate
     * @return Validation result
     */
    @PostMapping("/{taskId}/form/validate")
    @Operation(summary = "Validate form data", description = "Validate form submission without completing task")
    public ResponseEntity<Map<String, Object>> validateFormData(
            @Parameter(description = "Task ID")
            @PathVariable String taskId,
            @RequestBody Map<String, Object> formData) {
        log.info("Validating form data for task: {}", taskId);

        taskFormService.validateTaskFormSubmission(taskId, formData);

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Form data is valid",
                "taskId", taskId
        ));
    }

    /**
     * Submit form and complete task
     * @param taskId The task ID
     * @param request Form submission request
     * @param authentication User authentication
     * @return Form submission response
     */
    @PostMapping("/{taskId}/form/submit")
    @Operation(summary = "Submit task form", description = "Submit form data and complete task")
    public ResponseEntity<FormSubmitResponse> submitTaskForm(
            @Parameter(description = "Task ID")
            @PathVariable String taskId,
            @RequestBody FormSubmitRequest request,
            Authentication authentication) {

        String submittedBy = extractUsername(authentication);
        log.info("Submitting form for task: {} by user: {}", taskId, submittedBy);

        FormSubmitResponse response = taskFormService.submitTaskForm(taskId, request, submittedBy);

        return ResponseEntity.ok(response);
    }

    /**
     * Extract username from authentication
     */
    private String extractUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            JwtUserContext userContext = new JwtUserContext(jwt);
            return userContext.getUserId();
        }
        return "system";
    }
}
