package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.security.JwtClaimsExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Computes the artifact visibility specification for a given JWT principal.
 *
 * <p>Visibility rules (ADR-010 §3 + M4.4 PSS spec):
 * <ol>
 *   <li><b>Admin</b> — roles {@code ADMIN}, {@code SUPER_ADMIN}, or {@code WORKFLOW_ADMIN}
 *       see ALL artifacts regardless of department.</li>
 *   <li><b>Manager with ALL_DEPTS scope</b> — a user that belongs to at least one
 *       manager-tier group AND the tenant has configured {@code managerScope=ALL_DEPTS}
 *       also sees all artifacts (same as admin).</li>
 *   <li><b>Regular user</b> — sees only artifacts where
 *       {@code department_code = user's department} OR {@code department_code IS NULL}
 *       (null = globally visible).</li>
 * </ol>
 *
 * <p>The returned {@link VisibilitySpec} is a plain value object; callers are
 * responsible for translating it into a JPA {@code Specification} or a SQL
 * WHERE clause fragment.
 *
 * <p>This service is <em>stateless</em> — it does not cache results because the
 * policy configuration is already cached inside {@link VisibilityPolicyProjector}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisibilityFilterService {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "WORKFLOW_ADMIN");
    private static final String ALL_DEPTS_SCOPE = "ALL_DEPTS";

    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final VisibilityPolicyProjector visibilityPolicyProjector;

    /**
     * Builds a {@link VisibilitySpec} for the authenticated user.
     *
     * <p>The spec encodes exactly three mutually exclusive states:
     * <ul>
     *   <li>{@code isUnrestricted = true}  → no WHERE filter; caller shows all artifacts.</li>
     *   <li>{@code isUnrestricted = false} → apply {@code dept_code = userDept OR dept_code IS NULL}.</li>
     * </ul>
     *
     * @param jwt       the authenticated principal's JWT
     * @param tenantCode the resolved tenant code (must be pre-extracted by the caller)
     * @return a non-null {@link VisibilitySpec}
     */
    public VisibilitySpec buildSpec(Jwt jwt, String tenantCode) {
        List<String> roles  = jwtClaimsExtractor.getRoles(jwt);
        String userDept     = jwtClaimsExtractor.getDepartment(jwt);
        List<String> groups = jwtClaimsExtractor.getGroups(jwt);

        // Rule 1: admin roles bypass all department filters.
        boolean isAdmin = roles.stream().anyMatch(ADMIN_ROLES::contains);
        if (isAdmin) {
            log.debug("VisibilityFilterService: admin role detected — unrestricted access");
            return VisibilitySpec.unrestricted(userDept);
        }

        // Rule 2: manager-tier + ALL_DEPTS policy → same as admin.
        boolean managerCanSeeAll = isManagerWithAllDeptsScope(tenantCode, groups);
        if (managerCanSeeAll) {
            log.debug("VisibilityFilterService: manager-tier ALL_DEPTS scope — unrestricted access");
            return VisibilitySpec.unrestricted(userDept);
        }

        // Rule 3: regular user — department-scoped.
        log.debug("VisibilityFilterService: department-scoped access for dept={}", userDept);
        return VisibilitySpec.departmentScoped(userDept);
    }

    /**
     * Returns {@code true} when the user belongs to at least one manager-tier group
     * AND the tenant's {@code managerScope} configuration is {@code ALL_DEPTS}.
     *
     * <p>Group membership is determined by matching the JWT {@code groups} claim
     * (Keycloak group paths, e.g. {@code "/Finance/Managers"}) against the
     * tenant-registered manager-tier group names. The match is exact on the
     * last path segment to accommodate both flat and hierarchical group paths.
     */
    private boolean isManagerWithAllDeptsScope(String tenantCode, List<String> userGroups) {
        if (userGroups == null || userGroups.isEmpty()) {
            return false;
        }

        var policy = visibilityPolicyProjector.project(tenantCode);
        if (!ALL_DEPTS_SCOPE.equals(policy.managerScope())) {
            return false;
        }

        List<String> managerTierGroups = policy.managerTierGroups();
        if (managerTierGroups == null || managerTierGroups.isEmpty()) {
            return false;
        }

        // Match the user's JWT group paths against manager-tier group names.
        // A group path like "/HR Department/Managers" matches the name "Managers"
        // or the full path if the group name was registered with a slash prefix.
        return userGroups.stream().anyMatch(path ->
                managerTierGroups.stream().anyMatch(managerGroup ->
                        path.equals(managerGroup) || path.endsWith("/" + managerGroup)));
    }

    // -------------------------------------------------------------------------
    // Value object
    // -------------------------------------------------------------------------

    /**
     * Encapsulates the visibility filter decision for a single request.
     *
     * <p>Callers use this to construct query predicates:
     * <pre>
     *   VisibilitySpec spec = visibilityFilterService.buildSpec(jwt, tenantCode);
     *   if (spec.isUnrestricted()) {
     *       // no WHERE clause on department_code
     *   } else {
     *       // WHERE department_code = :dept OR department_code IS NULL
     *       // bind spec.userDept() as :dept
     *   }
     * </pre>
     */
    public record VisibilitySpec(
            boolean isUnrestricted,
            String userDept
    ) {
        /** The user sees all artifacts — no department filter applied. */
        static VisibilitySpec unrestricted(String userDept) {
            return new VisibilitySpec(true, userDept);
        }

        /** The user sees only artifacts for their department (or globally scoped ones). */
        static VisibilitySpec departmentScoped(String userDept) {
            return new VisibilitySpec(false, userDept);
        }
    }
}
