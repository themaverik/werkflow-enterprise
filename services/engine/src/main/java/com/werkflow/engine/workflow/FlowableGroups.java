package com.werkflow.engine.workflow;

/**
 * Constants for Flowable candidateGroup identifiers.
 *
 * Static constants cover administrative roles and backward-compatible DOA underscore
 * identifiers. New-format groups (DOA:L*, DEPT:*::DOA:L*) are validated structurally
 * by BpmnGroupValidator rather than being listed here.
 */
public final class FlowableGroups {

    private FlowableGroups() {}

    // Administrative roles
    public static final String ADMIN        = "ADMIN";
    public static final String SUPER_ADMIN  = "SUPER_ADMIN";

    // Workflow management
    public static final String WORKFLOW_DESIGNER = "WORKFLOW_DESIGNER";

    // Delegation of Authority levels — backward-compatible underscore format.
    // FlowableGroupResolver emits both DOA_LN and DOA:LN simultaneously during Phase 2
    // so existing code referencing these constants continues to work.
    public static final String DOA_L0 = "DOA_L0";
    public static final String DOA_L1 = "DOA_L1";
    public static final String DOA_L2 = "DOA_L2";
    public static final String DOA_L3 = "DOA_L3";
    public static final String DOA_L4 = "DOA_L4";
}
