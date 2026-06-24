package com.werkflow.engine.exception;

import java.util.List;

/**
 * Thrown when a BPMN being deployed references forms or DMN decisions that do not
 * exist for the caller's tenant. The deploy is aborted and every missing ref is
 * listed so the caller can fix all gaps in one round-trip (aggregate, not first-fail).
 */
public class DanglingReferenceException extends RuntimeException {

    private final List<String> missingForms;
    private final List<String> missingDecisions;

    public DanglingReferenceException(List<String> missingForms, List<String> missingDecisions) {
        super(buildMessage(missingForms, missingDecisions));
        this.missingForms = List.copyOf(missingForms);
        this.missingDecisions = List.copyOf(missingDecisions);
    }

    public List<String> getMissingForms() {
        return missingForms;
    }

    public List<String> getMissingDecisions() {
        return missingDecisions;
    }

    private static String buildMessage(List<String> forms, List<String> decisions) {
        StringBuilder sb = new StringBuilder("Deploy rejected: ");
        if (!forms.isEmpty()) {
            sb.append(forms.size()).append(" missing form(s): ").append(forms);
        }
        if (!forms.isEmpty() && !decisions.isEmpty()) {
            sb.append("; ");
        }
        if (!decisions.isEmpty()) {
            sb.append(decisions.size()).append(" missing decision(s): ").append(decisions);
        }
        return sb.toString();
    }
}
