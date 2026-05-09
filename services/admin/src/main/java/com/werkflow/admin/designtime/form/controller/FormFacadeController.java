package com.werkflow.admin.designtime.form.controller;

import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse;
import com.werkflow.admin.designtime.bpmn.service.ProcessVariableScopeService;
import com.werkflow.admin.designtime.connector.dto.FlatField;
import com.werkflow.admin.designtime.connector.service.ConnectorCatalogService;
import com.werkflow.admin.security.JwtClaimsExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * DTDS Form Facade — endpoints consumed by the form-js editor to make
 * data bindings aware of connector operations and process variable scope.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/design/form")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
@Tag(name = "DTDS — Form Facade", description = "Design-time data service for the form-js editor")
public class FormFacadeController {

    private final ProcessVariableScopeService variableScopeService;
    private final ConnectorCatalogService catalogService;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /**
     * Returns the process variables in scope at the given activity.
     * The form-js editor uses this to suggest valid data bindings for form fields.
     *
     * @param processDefId deployed process definition ID
     * @param taskId       BPMN activity ID of the user task that renders this form
     */
    @GetMapping("/binding-targets")
    @Operation(summary = "Get form binding targets",
               description = "Returns process variables reachable at the given task — " +
                             "used by the form editor to suggest valid data bindings.")
    public ResponseEntity<VariableAtActivityResponse> getBindingTargets(
            @RequestParam String processDefId,
            @RequestParam String taskId,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        VariableAtActivityResponse response = variableScopeService.variablesAt(
                tenantId, processDefId, taskId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns enum-typed fields from a connector operation's output schema.
     * The form-js editor uses this to populate select/radio/checkbox fields
     * from live connector data at runtime.
     *
     * @param connectorKey connector key (kebab-case)
     * @param operationId  operation ID within the connector
     */
    @GetMapping("/connector-options/{connectorKey}/{operationId}")
    @Operation(summary = "Get connector enum options",
               description = "Returns fields from the operation output schema that have a fixed set of values " +
                             "(enum or boolean). The form editor uses these for select-field data sources.")
    public ResponseEntity<List<Map<String, Object>>> getConnectorOptions(
            @PathVariable String connectorKey,
            @PathVariable String operationId,
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwtClaimsExtractor.getTenantId(jwt);
        List<FlatField> outputFields = catalogService.getFlatFields(tenantId, connectorKey, operationId, "output");

        // Return all scalar output fields as potential select-field options.
        // The form editor filters for enum/boolean types on the client side.
        List<Map<String, Object>> enumFields = outputFields.stream()
                .filter(f -> !f.isArrayItem() && f.depth() <= 2)
                .map(f -> {
                    String fieldName = f.path().contains(".")
                            ? f.path().substring(f.path().lastIndexOf('.') + 1)
                            : f.path();
                    return Map.<String, Object>of(
                            "fieldPath",  f.path(),
                            "fieldName",  fieldName,
                            "type",       f.type() != null ? f.type() : "string",
                            "format",     f.format() != null ? f.format() : "",
                            "required",   f.required());
                })
                .toList();

        return ResponseEntity.ok(enumFields);
    }
}
