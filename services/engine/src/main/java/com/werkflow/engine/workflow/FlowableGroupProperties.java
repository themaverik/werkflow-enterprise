package com.werkflow.engine.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * Typed binding for app.flowable configuration.
 *
 * Example YAML:
 * <pre>
 * app:
 *   flowable:
 *     role-mappings:
 *       admin:               [ADMIN, SUPER_ADMIN]
 *       super_admin:         [SUPER_ADMIN]
 *       doa_approver_level1: [DOA_L1]
 *       doa_approver_level2: [DOA_L1, DOA_L2]
 *       doa_approver_level3: [DOA_L1, DOA_L2, DOA_L3]
 *       doa_approver_level4: [DOA_L1, DOA_L2, DOA_L3, DOA_L4]
 *       workflow_designer:   [WORKFLOW_DESIGNER]
 *     include-department-as-group: true
 * </pre>
 *
 * Fails fast at startup if YAML is malformed or role-mappings is empty.
 */
@ConfigurationProperties(prefix = "app.flowable")
@Validated
public class FlowableGroupProperties {

    /**
     * Maps Keycloak realm role names to Flowable candidateGroup identifiers.
     * Keys are lowercase role names as they appear in the JWT realm_access.roles claim.
     * Values are the FlowableGroups constants to assign.
     */
    @NotEmpty(message = "app.flowable.role-mappings must not be empty")
    private Map<String, List<String>> roleMappings = Map.of();

    /**
     * When true, the JWT department claim is passed through as a Flowable group.
     * Enables department-scoped tasks (e.g. Custodian Review routed to "IT").
     */
    private boolean includeDepartmentAsGroup = true;

    public Map<String, List<String>> getRoleMappings() {
        return roleMappings;
    }

    public void setRoleMappings(Map<String, List<String>> roleMappings) {
        this.roleMappings = roleMappings;
    }

    public boolean isIncludeDepartmentAsGroup() {
        return includeDepartmentAsGroup;
    }

    public void setIncludeDepartmentAsGroup(boolean includeDepartmentAsGroup) {
        this.includeDepartmentAsGroup = includeDepartmentAsGroup;
    }
}
