package com.werkflow.engine.action.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping({"/api/notification-templates", "/notification-templates"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Templates", description = "Notification template CRUD for the email template designer")
public class NotificationTemplateController {

    private final NotificationTemplateRepository repository;

    // ---- DTOs -------------------------------------------------------

    public record TemplateInfo(String key, String name, String channel) {}

    public record TemplateResponse(
        Long id,
        String key,
        String name,
        String channel,
        String subject,
        String body,
        String designJson,
        String linkedFormKey,
        String createdAt,
        String updatedAt
    ) {}

    public record TemplateRequest(
        @NotBlank @Size(max = 100) String key,
        @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String channel,
        @Size(max = 500) String subject,
        @NotBlank String body,
        String designJson,
        @Size(max = 100) String linkedFormKey
    ) {}

    // ---- Mapping helper ---------------------------------------------

    private TemplateResponse toResponse(NotificationTemplate t) {
        return new TemplateResponse(
            t.getId(),
            t.getTemplateKey(),
            t.getName() != null ? t.getName() : t.getTemplateKey(),
            t.getChannel(),
            t.getSubject(),
            t.getBody(),
            t.getDesignJson(),
            t.getLinkedFormKey(),
            t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
            t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null
        );
    }

    // ---- Endpoints --------------------------------------------------

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List templates", description = "Returns all active notification templates")
    public ResponseEntity<List<TemplateInfo>> listTemplates(Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        List<TemplateInfo> templates = repository.findAllByTenantIdAndDeletedAtIsNull(tenantId)
            .stream()
            .map(t -> new TemplateInfo(
                t.getTemplateKey(),
                t.getName() != null ? t.getName() : t.getTemplateKey(),
                t.getChannel()
            ))
            .toList();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "List all templates (full)", description = "Returns full template details for the admin designer")
    public ResponseEntity<List<TemplateResponse>> listAll(Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        return ResponseEntity.ok(
            repository.findAllByTenantIdAndDeletedAtIsNull(tenantId).stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Get template by key")
    public ResponseEntity<TemplateResponse> getByKey(@PathVariable String key, Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        return repository.findByTemplateKeyAndTenantIdAndDeletedAtIsNull(key, tenantId)
            .map(t -> ResponseEntity.ok(toResponse(t)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Create template")
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest request,
            Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        if (repository.findByTemplateKeyAndTenantIdAndDeletedAtIsNull(request.key(), tenantId).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        NotificationTemplate t = new NotificationTemplate();
        t.setTemplateKey(request.key());
        t.setTenantId(tenantId);
        t.setName(request.name() != null ? request.name() : request.key());
        t.setChannel(request.channel());
        t.setSubject(request.subject());
        t.setBody(request.body());
        t.setDesignJson(request.designJson());
        t.setLinkedFormKey(request.linkedFormKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(repository.save(t)));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Update template")
    public ResponseEntity<TemplateResponse> update(
            @PathVariable String key,
            @Valid @RequestBody TemplateRequest request,
            Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        return repository.findByTemplateKeyAndTenantIdAndDeletedAtIsNull(key, tenantId)
            .map(t -> {
                t.setName(request.name() != null ? request.name() : t.getName());
                t.setChannel(request.channel());
                t.setSubject(request.subject());
                t.setBody(request.body());
                t.setDesignJson(request.designJson());
                t.setLinkedFormKey(request.linkedFormKey());
                return ResponseEntity.ok(toResponse(repository.save(t)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Soft-delete template")
    public ResponseEntity<Void> delete(@PathVariable String key, Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        return repository.findByTemplateKeyAndTenantIdAndDeletedAtIsNull(key, tenantId)
            .map(t -> {
                t.setDeletedAt(OffsetDateTime.now());
                repository.save(t);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ---- Private helpers --------------------------------------------

    private String extractTenantId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String ti = jwt.getClaimAsString("tenant_id");
            return (ti != null && !ti.isBlank()) ? ti : "default";
        }
        return "default";
    }
}
