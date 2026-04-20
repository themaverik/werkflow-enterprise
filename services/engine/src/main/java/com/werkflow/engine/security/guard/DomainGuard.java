package com.werkflow.engine.security.guard;

import org.springframework.security.core.Authentication;

import java.io.Serializable;

/**
 * Extension point for domain-specific, fine-grained authorization.
 *
 * Implement this interface and register the implementation as a Spring bean to add
 * permission checks for a new resource type. WerkflowPermissionEvaluator discovers
 * all DomainGuard beans automatically — no code changes to the evaluator are needed.
 *
 * The {@code supports()} method returns the targetType string used in
 * {@code @PreAuthorize("hasPermission(#id, 'TargetType', 'RESOURCE:ACTION')")}.
 */
public interface DomainGuard {

    /**
     * The resource type this guard handles (e.g. "AssetRequest", "Task", "Hub").
     */
    String supports();

    /**
     * Returns true if the authenticated user may perform {@code action} on the given target.
     *
     * @param auth     the current authentication
     * @param targetId the resource identifier
     * @param action   the action suffix from the permission key (e.g. "APPROVE" from "ASSET_REQUEST:APPROVE")
     */
    boolean canAct(Authentication auth, Serializable targetId, String action);
}
