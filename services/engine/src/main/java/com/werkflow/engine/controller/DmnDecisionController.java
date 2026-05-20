package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.dmn.DmnDecisionDto;
import com.werkflow.engine.dto.dmn.DmnExecutionDto;
import com.werkflow.engine.dto.dmn.DmnTestResultDto;
import com.werkflow.engine.service.DmnDecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing and evaluating DMN decision tables.
 */
@RestController
@RequestMapping("/api/v1/dmn/decisions")
@RequiredArgsConstructor
@Tag(name = "DMN Decisions", description = "Decision table management and evaluation")
@SecurityRequirement(name = "bearer-jwt")
public class DmnDecisionController {

    private final DmnDecisionService dmnDecisionService;

    public record DeployRequest(String name, String dmnXml) {}

    @GetMapping
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "List decision tables", description = "Returns the latest version of every deployed decision table")
    public ResponseEntity<List<DmnDecisionDto>> listDecisions(Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(dmnDecisionService.listDecisions(tenantId));
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Get decision table metadata")
    public ResponseEntity<DmnDecisionDto> getDecision(
            @Parameter(description = "Decision table key") @PathVariable String key,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(dmnDecisionService.getDecision(key, tenantId));
    }

    @GetMapping(value = "/{key}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Get raw DMN XML", description = "Returns the DMN XML for the latest deployed version")
    public ResponseEntity<String> getDecisionXml(
            @Parameter(description = "Decision table key") @PathVariable String key,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(dmnDecisionService.getDecisionXml(key, tenantId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'WORKFLOW:DEPLOY')")
    @Operation(summary = "Deploy a new decision table", description = "Deploys DMN XML. Flowable auto-increments the version if the decision key already exists.")
    public ResponseEntity<DmnDecisionDto> deployDecision(
            @RequestBody DeployRequest request,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        DmnDecisionDto result = dmnDecisionService.deployDecision(request.dmnXml(), request.name(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'WORKFLOW:DEPLOY')")
    @Operation(summary = "Redeploy an existing decision table", description = "Deploys an updated DMN XML as a new version of the given decision key")
    public ResponseEntity<DmnDecisionDto> redeployDecision(
            @Parameter(description = "Decision table key") @PathVariable String key,
            @RequestBody DeployRequest request,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        String name = (request.name() != null && !request.name().isBlank()) ? request.name() : key;
        DmnDecisionDto result = dmnDecisionService.deployDecision(request.dmnXml(), name, tenantId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/deployment/{deploymentId}")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Delete a decision deployment", description = "Removes the deployment and all decision table versions it contains")
    public ResponseEntity<Void> deleteDeployment(
            @Parameter(description = "Flowable deployment ID") @PathVariable String deploymentId,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        dmnDecisionService.deleteDeployment(deploymentId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{key}/test")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Test a decision table", description = "Evaluates the decision with ad-hoc inputs. Result is not persisted to history.")
    public ResponseEntity<DmnTestResultDto> testDecision(
            @Parameter(description = "Decision table key") @PathVariable String key,
            @RequestBody Map<String, Object> inputs,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(dmnDecisionService.testDecision(key, tenantId, inputs));
    }

    @GetMapping("/{key}/executions")
    @PreAuthorize("hasPermission(null, 'WORKFLOW:MANAGE')")
    @Operation(summary = "Get execution history", description = "Returns paginated decision evaluation history from Flowable ACT_HI_DEC_INSTANCE")
    public ResponseEntity<Page<DmnExecutionDto>> getExecutionHistory(
            @Parameter(description = "Decision table key") @PathVariable String key,
            @PageableDefault(size = 20, sort = "executedAt") Pageable pageable,
            Authentication authentication
    ) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(dmnDecisionService.getExecutionHistory(key, tenantId, pageable));
    }

    private String extractTenantId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            JwtUserContext ctx = new JwtUserContext(jwt);
            return ctx.getTenantCode() != null ? ctx.getTenantCode() : "default";
        }
        return "default";
    }
}
