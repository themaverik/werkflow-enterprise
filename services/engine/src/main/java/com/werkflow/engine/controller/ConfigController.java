package com.werkflow.engine.controller;

import com.werkflow.engine.workflow.FlowableGroupProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final FlowableGroupProperties flowableGroupProperties;

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
}
