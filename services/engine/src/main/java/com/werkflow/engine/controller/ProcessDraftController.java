package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.ProcessDraftSummaryDTO;
import com.werkflow.engine.service.ProcessDraftService;
import com.werkflow.engine.workflow.ProcessDraft;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/process-drafts")
@RequiredArgsConstructor
@Tag(name = "Process Drafts", description = "Process definition draft management")
public class ProcessDraftController {

    private final ProcessDraftService processDraftService;

    @Data
    public static class SaveDraftRequest {
        private String processKey;
        private String name;
        private String bpmnXml;
    }

    @PreAuthorize("hasPermission(null, 'WORKFLOW:DESIGN')")
    @GetMapping
    public ResponseEntity<List<ProcessDraftSummaryDTO>> listDrafts() {
        return ResponseEntity.ok(processDraftService.listDrafts());
    }

    @PostMapping
    public ResponseEntity<ProcessDraft> saveDraft(
            @RequestBody SaveDraftRequest request,
            Authentication authentication) {
        String userId = extractUserId(authentication);
        ProcessDraft draft = processDraftService.saveDraft(
                request.getProcessKey(), request.getName(), request.getBpmnXml(), userId);
        return ResponseEntity.ok(draft);
    }

    @GetMapping("/{processKey}")
    public ResponseEntity<ProcessDraft> getDraft(@PathVariable String processKey) {
        Optional<ProcessDraft> draft = processDraftService.getDraft(processKey);
        return draft.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{processKey}")
    public ResponseEntity<Void> deleteDraft(@PathVariable String processKey) {
        processDraftService.deleteDraft(processKey);
        return ResponseEntity.noContent().build();
    }

    private String extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return new JwtUserContext(jwt).getUserId();
        }
        return "system";
    }
}
