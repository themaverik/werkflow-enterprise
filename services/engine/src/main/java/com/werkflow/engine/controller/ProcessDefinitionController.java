package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.dto.TaskFormResponse;
import com.werkflow.engine.service.ProcessCustodyService;
import com.werkflow.engine.service.ProcessDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST controller for managing BPMN process definitions
 * Supports both /process-definitions and /api/process-definitions paths
 */
@RestController
@RequestMapping({"/process-definitions", "/api/process-definitions"})
@RequiredArgsConstructor
@Tag(name = "Process Definitions", description = "BPMN process definition management")
@SecurityRequirement(name = "bearer-jwt")
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;
    private final ProcessCustodyService processCustodyService;

    public record DeployRequest(String name, String bpmnXml, String owningDepartment, String parentDeploymentId) {}

    @PostMapping(value = "/deploy", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'WORKFLOW:DEPLOY')")
    @Operation(summary = "Deploy a new process definition", description = "Deploy a BPMN 2.0 XML string. " +
        "Set parentDeploymentId to link this deployment to a bundle (ADR-009).")
    public ResponseEntity<ProcessDefinitionResponse> deployProcessDefinition(
        @RequestBody DeployRequest deployRequest,
        Authentication authentication
    ) {
        String resourceName = deployRequest.name().toLowerCase().replaceAll("\\s+", "-") + ".bpmn20.xml";
        ProcessDefinitionResponse response = processDefinitionService.deployProcessDefinition(
                deployRequest.bpmnXml(), resourceName, deployRequest.parentDeploymentId());

        if (deployRequest.owningDepartment() != null && !deployRequest.owningDepartment().isBlank()) {
            JwtUserContext user = extractUserContext(authentication);
            processCustodyService.recordCustody(response.getKey(), deployRequest.owningDepartment(),
                    user.getUserId(), user.getDepartment());
            response.setOwningDepartment(deployRequest.owningDepartment());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all process definitions", description = "Retrieve all process definitions (latest versions)")
    public ResponseEntity<List<ProcessDefinitionResponse>> getAllProcessDefinitions() {
        List<ProcessDefinitionResponse> responses = processDefinitionService.getAllProcessDefinitions();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get process definition by ID")
    public ResponseEntity<ProcessDefinitionResponse> getProcessDefinitionById(
        @Parameter(description = "Process definition ID") @PathVariable String id
    ) {
        ProcessDefinitionResponse response = processDefinitionService.getProcessDefinitionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/key/{key}")
    @Operation(summary = "Get process definition by key", description = "Get latest version of process definition by key")
    public ResponseEntity<ProcessDefinitionResponse> getProcessDefinitionByKey(
        @Parameter(description = "Process definition key") @PathVariable String key
    ) {
        ProcessDefinitionResponse response = processDefinitionService.getProcessDefinitionByKey(key);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/key/{key}/versions")
    @Operation(summary = "Get all versions of a process definition")
    public ResponseEntity<List<ProcessDefinitionResponse>> getProcessDefinitionVersions(
        @Parameter(description = "Process definition key") @PathVariable String key
    ) {
        List<ProcessDefinitionResponse> responses = processDefinitionService.getProcessDefinitionVersions(key);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/deployment/{deploymentId}")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Delete process definition deployment")
    public ResponseEntity<Void> deleteProcessDefinition(
        @Parameter(description = "Deployment ID") @PathVariable String deploymentId,
        @Parameter(description = "Cascade delete (delete running instances)") @RequestParam(defaultValue = "false") boolean cascade,
        @Parameter(description = "Process definition key for custody check") @RequestParam(required = false) String processKey,
        Authentication authentication
    ) {
        if (processKey != null && !processKey.isBlank()) {
            JwtUserContext user = extractUserContext(authentication);
            processCustodyService.assertCustody(processKey, user);
        }
        processDefinitionService.deleteProcessDefinition(deploymentId, cascade);
        return ResponseEntity.noContent().build();
    }

    private JwtUserContext extractUserContext(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return new JwtUserContext(jwt);
        }
        return JwtUserContext.builder().userId("system").build();
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Suspend process definition")
    public ResponseEntity<Void> suspendProcessDefinition(
        @Parameter(description = "Process definition ID") @PathVariable String id
    ) {
        processDefinitionService.suspendProcessDefinition(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Activate process definition")
    public ResponseEntity<Void> activateProcessDefinition(
        @Parameter(description = "Process definition ID") @PathVariable String id
    ) {
        processDefinitionService.activateProcessDefinition(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/start-form")
    @Operation(summary = "Get start form for process definition", description = "Returns the form schema linked to the start event")
    public ResponseEntity<TaskFormResponse> getStartForm(
        @Parameter(description = "Process definition ID") @PathVariable String id
    ) {
        TaskFormResponse response = processDefinitionService.getStartForm(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/xml")
    @Operation(summary = "Get process definition BPMN XML", description = "Retrieve the BPMN XML representation of a process definition")
    public ResponseEntity<String> getProcessDefinitionXml(
        @Parameter(description = "Process definition ID") @PathVariable String id
    ) {
        String xml = processDefinitionService.getProcessDefinitionXml(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }

    @GetMapping("/key/{key}/xml")
    @Operation(summary = "Get BPMN XML by process key", description = "Retrieve the BPMN XML for the latest deployed version of a process by its key")
    public ResponseEntity<String> getProcessDefinitionXmlByKey(
        @Parameter(description = "Process definition key") @PathVariable String key
    ) {
        String xml = processDefinitionService.getProcessDefinitionXmlByKey(key);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
    }
}
