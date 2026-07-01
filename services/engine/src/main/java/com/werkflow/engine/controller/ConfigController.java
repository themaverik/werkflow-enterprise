package com.werkflow.engine.controller;

import com.werkflow.engine.workflow.BpmnCandidateGroupExtractor;
import com.werkflow.engine.workflow.FlowableGroupProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final FlowableGroupProperties flowableGroupProperties;
    private final BpmnCandidateGroupExtractor bpmnCandidateGroupExtractor;

    /**
     * Returns Tier-1 (YAML-backed) role→candidateGroup mappings.
     * These are deployment-gated; changes require redeployment.
     */
    @GetMapping("/flowable-role-mappings")
    public ResponseEntity<Map<String, Object>> getFlowableRoleMappings() {
        List<Map<String, Object>> mappings = flowableGroupProperties.getRoleMappings()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> Map.<String, Object>of("role", e.getKey(), "groups", e.getValue()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("mappings", mappings));
    }

    /**
     * Returns the distinct static candidateGroup literals referenced by the latest deployed
     * BPMN process definitions for the given tenant.
     *
     * <p>Excludes EL expressions ({@code ${...}}, {@code #{...}}) and the Tier-1 system
     * groups (SUPER_ADMIN, ADMIN, WORKFLOW_DESIGNER). Results are sorted ascending.
     *
     * <p>Secured as {@code permitAll()} — same as {@code /flowable-role-mappings} — because
     * this is an internal S2S call from admin-service with no authentication context.
     * It returns group names only (no user data, no credentials).
     *
     * @param tenantCode the tenant to scope the query to (defaults to {@code "default"})
     */
    @GetMapping("/bpmn-candidate-groups")
    public ResponseEntity<Map<String, Object>> getBpmnCandidateGroups(
            @RequestParam(defaultValue = "default") String tenantCode) {
        List<String> groups = bpmnCandidateGroupExtractor.extractStaticGroups(tenantCode);
        return ResponseEntity.ok(Map.of("groups", groups));
    }
}
