package com.werkflow.engine.controller;

import com.werkflow.engine.security.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/groups", "/groups"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Groups", description = "Keycloak group lookup for BPMN designer")
public class GroupsController {

    private final KeycloakUserService keycloakUserService;

    public record GroupInfo(String id, String name) {}

    @GetMapping
    @Operation(summary = "List groups", description = "Returns all realm groups for the candidate groups dropdown in the BPMN designer")
    public ResponseEntity<List<GroupInfo>> listGroups() {
        List<GroupInfo> groups = keycloakUserService.getAllGroups()
            .stream()
            .map(g -> new GroupInfo(g.getId(), g.getName()))
            .toList();
        return ResponseEntity.ok(groups);
    }
}
