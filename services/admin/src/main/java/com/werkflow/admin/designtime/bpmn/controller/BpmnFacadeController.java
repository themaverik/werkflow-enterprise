package com.werkflow.admin.designtime.bpmn.controller;

import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse;
import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService;
import com.werkflow.admin.security.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * BPMN Facade — provides process-level context to the BPMN designer.
 *
 * <p>Exposes accumulated process variable information so the designer can offer
 * context-sensitive variable pickers when configuring a task's input expressions.</p>
 */
@RestController
@RequestMapping("/api/v1/design/bpmn")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
@Tag(name = "DTDS — BPMN Facade", description = "Design-time BPMN introspection: process variables, activity context")
public class BpmnFacadeController {

    private final ProcessVariableScopeService variableScopeService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Returns the accumulated process variables reachable at the given activity,
     * with provenance indicating which prior task sets each variable.
     *
     * <p>This powers the variable picker in the BPMN designer so that the designer
     * can select process variables for input mappings without knowing BPMN internals.</p>
     */
    @GetMapping("/processes/{processDefId}/variables-at/{activityId}")
    @Operation(summary = "Variables at activity",
               description = "Traverses the deployed BPMN XML and returns the accumulated process variables at the given activity, with provenance (which task sets each variable).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Variable list returned"),
        @ApiResponse(responseCode = "404", description = "Process definition not found"),
        @ApiResponse(responseCode = "503", description = "Engine service unavailable")
    })
    public ResponseEntity<VariableAtActivityResponse> variablesAt(
            @Parameter(description = "Flowable process definition ID including version suffix")
            @PathVariable String processDefId,
            @Parameter(description = "BPMN element ID of the target activity")
            @PathVariable String activityId,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        return ResponseEntity.ok(variableScopeService.variablesAt(tenantId, processDefId, activityId, jwt.getTokenValue()));
    }
}
