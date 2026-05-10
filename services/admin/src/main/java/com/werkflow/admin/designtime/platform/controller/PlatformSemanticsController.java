package com.werkflow.admin.designtime.platform.controller;

import com.werkflow.admin.designtime.platform.dto.*;
import com.werkflow.admin.designtime.platform.dto.LocaleEntry;
import com.werkflow.admin.designtime.platform.service.*;
import com.werkflow.admin.security.JwtClaimsExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform Semantics Service — design-time read-side projection.
 * All endpoints are tenant-scoped via JWT, cached 5 minutes per tenant.
 */
@RestController
@RequestMapping("/api/v1/design/platform")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WORKFLOW_ADMIN','ADMIN','SUPER_ADMIN')")
public class PlatformSemanticsController {

    private final CapabilityAggregator capabilityAggregator;
    private final CandidateGroupsAggregator candidateGroupsAggregator;
    private final FeelExpressionGenerator feelExpressionGenerator;
    private final CategoryProjector categoryProjector;
    private final TagProjector tagProjector;
    private final VisibilityPolicyProjector visibilityPolicyProjector;
    private final VisibilityFilterService visibilityFilterService;
    private final ProcessVisibilityProjector processVisibilityProjector;
    private final DepartmentProjector departmentProjector;
    private final ProcessVariableCatalog processVariableCatalog;
    private final LocaleProjector localeProjector;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    /** Full capability discovery — all three designers read this on load. */
    @GetMapping("/capabilities")
    public ResponseEntity<PlatformCapabilityResponse> capabilities(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(capabilityAggregator.aggregate(tenant(jwt)));
    }

    /** Unified candidate-groups list — no department-routing groups (ADR-010). */
    @GetMapping("/candidate-groups")
    public ResponseEntity<List<CandidateGroupEntry>> candidateGroups(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(candidateGroupsAggregator.aggregate(tenant(jwt)));
    }

    /** FEEL expression catalog for DMN cell autocomplete. */
    @GetMapping("/feel-expressions")
    public ResponseEntity<FeelExpressionCatalog> feelExpressions(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(feelExpressionGenerator.generate(tenant(jwt)));
    }

    /** Tenant-registered categories for artifact catalog grouping. */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryEntry>> categories(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(categoryProjector.list(tenant(jwt)));
    }

    /** Create a new tenant category. */
    @PostMapping("/categories")
    public ResponseEntity<CategoryEntry> createCategory(
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryProjector.create(tenant(jwt), request));
    }

    /** Update an existing tenant category. */
    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryEntry> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(categoryProjector.update(tenant(jwt), id, request));
    }

    /** Delete a tenant category. Artifact references become null. */
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        categoryProjector.delete(tenant(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Previously-used tag autocomplete. Optional prefix for typeahead. */
    @GetMapping("/tags")
    public ResponseEntity<List<TagEntry>> tags(
            @RequestParam(required = false) String prefix,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tagProjector.listTags(tenant(jwt), prefix, 50));
    }

    /** ERP departments for artifact metadata visibility-scope picker. */
    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentEntry>> departments(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(departmentProjector.list(tenant(jwt)));
    }

    /** Tenant visibility policy. */
    @GetMapping("/visibility-policy")
    public ResponseEntity<VisibilityPolicyEntry> visibilityPolicy(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(visibilityPolicyProjector.project(tenant(jwt)));
    }

    /** Static catalog of enterprise delegate-set process variables. */
    @GetMapping("/process-variables")
    public ResponseEntity<ProcessVariableCatalogDto> processVariables(@AuthenticationPrincipal Jwt jwt) {
        // tenant param unused for static catalog, but JWT is required for auth auditing
        return ResponseEntity.ok(processVariableCatalog.getCatalog());
    }

    /** Tenant locale configuration — currency, number format, timezone, and date format. */
    @GetMapping("/locale")
    public ResponseEntity<LocaleEntry> locale(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(localeProjector.project(tenant(jwt)));
    }

    /**
     * Process definitions visible to the requesting user per ADR-010 §3.
     *
     * <p>Admins and ALL_DEPTS managers receive the full set of process drafts.
     * Regular users receive only drafts scoped to their department or globally visible
     * (null department_code) ones. The frontend Service Catalog uses this to replace
     * client-side visibility filtering with server-authoritative results.
     */
    @GetMapping("/visible-processes")
    public ResponseEntity<List<VisibleProcessEntry>> visibleProcesses(@AuthenticationPrincipal Jwt jwt) {
        VisibilityFilterService.VisibilitySpec spec = visibilityFilterService.buildSpec(jwt, tenant(jwt));
        return ResponseEntity.ok(processVisibilityProjector.listVisible(spec));
    }

    private String tenant(Jwt jwt) {
        return jwtClaimsExtractor.getTenantId(jwt);
    }
}
