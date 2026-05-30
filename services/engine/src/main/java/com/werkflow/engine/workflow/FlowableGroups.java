package com.werkflow.engine.workflow;

/**
 * Constants for Flowable candidateGroup identifiers.
 *
 * Administrative role constants and DOA level constants (underscore format — ADR-029).
 * Dynamic groups (DEPT:*) are resolved at runtime and not listed here.
 */
public final class FlowableGroups {

    private FlowableGroups() {}

    // Administrative roles
    public static final String ADMIN        = "ADMIN";
    public static final String SUPER_ADMIN  = "SUPER_ADMIN";

    // Workflow management
    public static final String WORKFLOW_DESIGNER = "WORKFLOW_DESIGNER";

    // Delegation of Authority levels — underscore format is canonical (ADR-029).
    // FlowableGroupResolver Step 4 emits DOA_L{level} from the JWT doa_level claim.
    public static final String DOA_L0 = "DOA_L0";
    public static final String DOA_L1 = "DOA_L1";
    public static final String DOA_L2 = "DOA_L2";
    public static final String DOA_L3 = "DOA_L3";
    public static final String DOA_L4 = "DOA_L4";
}
