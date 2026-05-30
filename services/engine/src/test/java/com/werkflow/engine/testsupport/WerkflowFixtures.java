package com.werkflow.engine.testsupport;

import org.flowable.engine.delegate.JavaDelegate;

import java.util.HashMap;
import java.util.Map;

/**
 * Variable builders and stub-bean factory for Werkflow Scope-2 process tests (ADR-028).
 *
 * <p>Variable names differ per BPMN/DMN — always read the source file to verify before seeding.
 * Helpers here correspond to the variable names actually declared in the shipped processes.
 */
public final class WerkflowFixtures {

    private WerkflowFixtures() {}

    // -------------------------------------------------------------------------
    // Stub delegates
    // -------------------------------------------------------------------------

    /** Delegate that sets one output variable and does nothing else. */
    public static JavaDelegate outputDelegate(String varName, Object value) {
        return execution -> execution.setVariable(varName, value);
    }

    /** Delegate that does nothing — for notification / side-effect tasks. */
    public static JavaDelegate noOpDelegate() {
        return execution -> {};
    }

    // -------------------------------------------------------------------------
    // Variable builder (fluent)
    // -------------------------------------------------------------------------

    public static VarBuilder vars() {
        return new VarBuilder();
    }

    public static final class VarBuilder {

        private final Map<String, Object> map = new HashMap<>();

        /** Generic key-value pair. */
        public VarBuilder put(String key, Object value) {
            map.put(key, value);
            return this;
        }

        /** {@code decision} — canonical approval contract (ADR-025): "approve" | "reject" | "escalate". */
        public VarBuilder decision(String value) {
            return put("decision", value);
        }

        /** {@code requestAmount} — capex-approval-process gateway input. */
        public VarBuilder requestAmount(Number value) {
            return put("requestAmount", value);
        }

        /** {@code amount} — procurement-matrix DMN input. */
        public VarBuilder amount(Number value) {
            return put("amount", value);
        }

        /** {@code category} — procurement-matrix DMN input. */
        public VarBuilder category(String value) {
            return put("category", value);
        }

        /** {@code doaLevel} — seeded for BPMNs/DMNs that read it as a process variable. */
        public VarBuilder doaLevel(int level) {
            return put("doaLevel", level);
        }

        /** {@code approverGroup} — pre-resolved candidate group for DMN-routing BPMNs. */
        public VarBuilder approverGroup(String group) {
            return put("approverGroup", group);
        }

        public Map<String, Object> build() {
            return Map.copyOf(map);
        }
    }
}
