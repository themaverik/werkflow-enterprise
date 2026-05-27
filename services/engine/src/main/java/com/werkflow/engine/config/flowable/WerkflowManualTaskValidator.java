package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.ManualTask;
import org.flowable.bpmn.model.Process;
import org.flowable.validation.ValidationError;
import org.flowable.validation.validator.ProcessLevelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Rejects any {@code <bpmn:manualTask>} that carries a
 * {@code flowable:field name="confirmationRequired" value="true"} extension (D-MT-4).
 *
 * <p>Background: the {@code MANUAL_STEP + confirmationRequired=true} variant was deprecated by
 * ADR-017 — the designer can no longer author it, and the engine recognises no such field on a
 * {@code manualTask} (a ManualTask is a pure pass-through with zero execution semantics in
 * Flowable 7.x). A hand-crafted or LLM-generated BPMN that bypasses the designer could still
 * declare the field; at runtime the engine passes straight through, so the intended "wait for
 * acknowledgement" silently never happens while the process continues.
 *
 * <p>This validator is the defence-in-depth gate that turns that silent no-op into a loud
 * deployment error, keeping every deployment source (designer / hand-authored / LLM) in sync with
 * the designer's constraints. It mirrors {@link WerkflowScriptTaskQuarantineValidator}. Designers
 * needing a human acknowledgement step use {@code HUMAN_APPROVAL} (a UserTask).
 *
 * @see <a href="../../../../../../../../../../docs/adr/ADR-017-deprecate-manual-step-confirmation.md">ADR-017</a>
 */
public class WerkflowManualTaskValidator extends ProcessLevelValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerkflowManualTaskValidator.class);

    /** Stable error code emitted for a manualTask that declares confirmationRequired=true. */
    public static final String WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED =
            "WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED";

    private static final String FIELD_ELEMENT = "field";
    private static final String STRING_ELEMENT = "string";
    private static final String FIELD_CONFIRMATION_REQUIRED = "confirmationRequired";

    @Override
    protected void executeValidation(BpmnModel bpmnModel, Process process, List<ValidationError> errors) {
        List<ManualTask> manualTasks = process.findFlowElementsOfType(ManualTask.class);
        for (ManualTask manualTask : manualTasks) {
            if (!declaresConfirmationRequired(manualTask)) {
                continue;
            }

            String id = manualTask.getId();
            String name = manualTask.getName();

            LOGGER.warn("WerkflowManualTaskValidator: rejecting manualTask id='{}' name='{}' in process '{}' "
                    + "— confirmationRequired=true is unsupported (ADR-017)", id, name, process.getId());

            String message = String.format(
                    "manualTask with confirmationRequired=true is not supported (ADR-017): a manualTask is a "
                            + "pass-through and the engine never enforces the confirmation, so the step would "
                            + "silently complete without acknowledgement. Element id=%s, name='%s'. "
                            + "Use a HUMAN_APPROVAL user task to require a human acknowledgement instead.",
                    id, name);

            addError(errors, WERKFLOW_MANUAL_TASK_CONFIRMATION_UNSUPPORTED, process, manualTask, message);
        }
    }

    /**
     * True when the manualTask carries a {@code flowable:field} named {@code confirmationRequired}
     * whose value is {@code true}. Handles both authoring forms — the {@code value="true"} attribute
     * and a nested {@code <flowable:string>true</flowable:string>} child.
     */
    private boolean declaresConfirmationRequired(ManualTask manualTask) {
        List<ExtensionElement> fields = manualTask.getExtensionElements().getOrDefault(FIELD_ELEMENT, List.of());
        for (ExtensionElement field : fields) {
            if (!FIELD_CONFIRMATION_REQUIRED.equalsIgnoreCase(attributeByLocalName(field, "name"))) {
                continue;
            }
            String value = attributeByLocalName(field, "value");
            if (value == null || value.isBlank()) {
                // Idiomatic Flowable form: <flowable:field><flowable:string>true</flowable:string></flowable:field>
                value = childElementText(field, STRING_ELEMENT);
            }
            if (value == null || value.isBlank()) {
                value = field.getElementText();
            }
            if (value != null && "true".equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Reads an attribute by its local name, ignoring namespace (attrs may be in any/no namespace). */
    private String attributeByLocalName(ExtensionElement element, String localName) {
        return element.getAttributes().getOrDefault(localName, List.of()).stream()
                .map(attr -> attr.getValue())
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** Text content of the first child element with the given local name, or {@code null}. */
    private String childElementText(ExtensionElement element, String childLocalName) {
        return element.getChildElements().getOrDefault(childLocalName, List.of()).stream()
                .map(ExtensionElement::getElementText)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
    }
}
