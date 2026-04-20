package com.werkflow.engine.controller;

import com.werkflow.engine.dto.ProcessInstanceResponse;
import com.werkflow.engine.dto.StartProcessRequest;
import com.werkflow.engine.service.ProcessInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing process instances
 */
@RestController
@RequestMapping("/api/process-instances")
@RequiredArgsConstructor
@Tag(name = "Process Instances", description = "Process instance execution and management")
@SecurityRequirement(name = "bearer-jwt")
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    @PostMapping
    @Operation(summary = "Start a new process instance")
    public ResponseEntity<ProcessInstanceResponse> startProcessInstance(
        @Valid @RequestBody StartProcessRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getClaimAsString("preferred_username");
        ProcessInstanceResponse response = processInstanceService.startProcessInstance(
            request, userId, jwt.getTokenValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all active process instances")
    public ResponseEntity<List<ProcessInstanceResponse>> getAllProcessInstances() {
        List<ProcessInstanceResponse> responses = processInstanceService.getAllProcessInstances();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get process instance by ID")
    public ResponseEntity<ProcessInstanceResponse> getProcessInstanceById(
        @Parameter(description = "Process instance ID") @PathVariable String id
    ) {
        ProcessInstanceResponse response = processInstanceService.getProcessInstanceById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/definition-key/{key}")
    @Operation(summary = "Get process instances by process definition key")
    public ResponseEntity<List<ProcessInstanceResponse>> getProcessInstancesByDefinitionKey(
        @Parameter(description = "Process definition key") @PathVariable String key
    ) {
        List<ProcessInstanceResponse> responses = processInstanceService.getProcessInstancesByDefinitionKey(key);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (terminate) a process instance")
    @PreAuthorize("hasPermission(null, 'PROCESS:MANAGE')")
    public ResponseEntity<Void> deleteProcessInstance(
        @Parameter(description = "Process instance ID") @PathVariable String id,
        @Parameter(description = "Deletion reason") @RequestParam(required = false) String reason
    ) {
        processInstanceService.deleteProcessInstance(id, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a process instance")
    @PreAuthorize("hasPermission(null, 'PROCESS:MANAGE')")
    public ResponseEntity<Void> suspendProcessInstance(
        @Parameter(description = "Process instance ID") @PathVariable String id
    ) {
        processInstanceService.suspendProcessInstance(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a suspended process instance")
    @PreAuthorize("hasPermission(null, 'PROCESS:MANAGE')")
    public ResponseEntity<Void> activateProcessInstance(
        @Parameter(description = "Process instance ID") @PathVariable String id
    ) {
        processInstanceService.activateProcessInstance(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/variables")
    @Operation(summary = "Get process variables")
    public ResponseEntity<Map<String, Object>> getProcessVariables(
        @Parameter(description = "Process instance ID") @PathVariable String id
    ) {
        Map<String, Object> variables = processInstanceService.getProcessVariables(id);
        return ResponseEntity.ok(variables);
    }

    @PutMapping("/{id}/variables")
    @Operation(summary = "Set process variables")
    @PreAuthorize("hasPermission(null, 'PROCESS:MANAGE')")
    public ResponseEntity<Void> setProcessVariables(
        @Parameter(description = "Process instance ID") @PathVariable String id,
        @RequestBody Map<String, Object> variables
    ) {
        processInstanceService.setProcessVariables(id, variables);
        return ResponseEntity.noContent().build();
    }
}
