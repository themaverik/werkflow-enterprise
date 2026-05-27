package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.validation.ValidationError;
import org.flowable.validation.validator.ProcessLevelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Hard-rejects any link event (catch or throw) encountered during BPMN deployment.
 *
 * <p><b>Why link events are unsupported:</b> Flowable 7.2 has no {@code LinkEventDefinition}
 * model class, no parser, and no behavior. The BPMN spec uses link events as "go-to" connectors
 * (a goto that avoids crossing sequence-flow lines). bpmn-js can author them, so a designer CAN
 * draw one — but the Flowable parser silently drops the {@code <linkEventDefinition>} element,
 * leaving a structurally broken process that either fails at deploy time or silently misbehaves
 * at runtime. This validator makes the failure loud and actionable at deploy time for both forms.
 *
 * <p><b>Detection strategy (empirically verified against Flowable 7.2.0 — see
 * {@link com.werkflow.engine.workflow.LinkEventProbeTest}):</b>
 * <ol>
 *   <li><b>Link catch</b> ({@code <intermediateCatchEvent>} + {@code <linkEventDefinition>}):
 *       Flowable's parser drops the definition; the model produces an {@code IntermediateCatchEvent}
 *       with an empty {@code eventDefinitions} list. Flowable's own built-in validator
 *       ({@code flowable-intermediate-catch-event-no-eventdefinition}) already rejects this, but
 *       its message is opaque. This validator fires FIRST (added before Flowable's set runs
 *       within the same validator set iteration) and emits the Werkflow-specific error code and
 *       an actionable message. Both errors will appear in the deployment exception.</li>
 *   <li><b>Link throw</b> ({@code <intermediateThrowEvent>} + {@code <linkEventDefinition>}):
 *       Flowable's parser drops the definition; the model produces a {@code ThrowEvent} with an
 *       empty {@code eventDefinitions} list. Flowable has NO built-in guard here — a "none
 *       intermediate throw" (marker event) also produces a {@code ThrowEvent} with zero defs,
 *       and Flowable accepts it. Detection via
 *       {@code findFlowElementsOfType(ThrowEvent.class)} plus {@code eventDefinitions.isEmpty()}.
 *       This also catches genuine none-throws, which is correct: none-throws are not a
 *       Werkflow-designer construct; rejecting them is conservative and the error message
 *       correctly advises using a sequence flow instead.</li>
 * </ol>
 *
 * <p>{@code EndEvent} does NOT extend {@code ThrowEvent} in Flowable 7.2 (it extends
 * {@code Event} directly), so terminate end events are unaffected by the throw check.
 */
public class WerkflowLinkEventValidator extends ProcessLevelValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerkflowLinkEventValidator.class);

    /** Stable error code emitted for every rejected link event element. */
    public static final String WERKFLOW_LINK_EVENT_UNSUPPORTED = "WERKFLOW_LINK_EVENT_UNSUPPORTED";

    private static final String SUGGESTION =
            "Restructure with a direct sequence flow or sub-process boundary instead.";

    @Override
    protected void executeValidation(BpmnModel bpmnModel, Process process, List<ValidationError> errors) {
        rejectLinkCatchEvents(process, errors);
        rejectLinkThrowEvents(process, errors);
    }

    private void rejectLinkCatchEvents(Process process, List<ValidationError> errors) {
        List<IntermediateCatchEvent> catchEvents =
                process.findFlowElementsOfType(IntermediateCatchEvent.class);
        for (IntermediateCatchEvent catchEvent : catchEvents) {
            if (!catchEvent.getEventDefinitions().isEmpty()) {
                continue;
            }
            String id   = catchEvent.getId();
            String name = catchEvent.getName();

            LOGGER.warn("WerkflowLinkEventValidator: rejecting intermediateCatchEvent id='{}' name='{}' "
                    + "in process '{}' — no eventDefinition (link catch silently stripped by Flowable parser)",
                    id, name, process.getId());

            String message = String.format(
                    "Link catch events are not supported in Flowable 7.2: the engine has no "
                            + "LinkEventDefinition and the parser silently drops the element, leaving a "
                            + "broken catch with no event definition. "
                            + "Element id=%s, name='%s'. %s",
                    id, name, SUGGESTION);

            addError(errors, WERKFLOW_LINK_EVENT_UNSUPPORTED, process, catchEvent, message);
        }
    }

    private void rejectLinkThrowEvents(Process process, List<ValidationError> errors) {
        List<ThrowEvent> throwEvents = process.findFlowElementsOfType(ThrowEvent.class);
        for (ThrowEvent throwEvent : throwEvents) {
            if (!throwEvent.getEventDefinitions().isEmpty()) {
                continue;
            }
            String id   = throwEvent.getId();
            String name = throwEvent.getName();

            LOGGER.warn("WerkflowLinkEventValidator: rejecting intermediateThrowEvent id='{}' name='{}' "
                    + "in process '{}' — empty eventDefinitions (link throw silently stripped, or none-throw "
                    + "not supported as designer construct)",
                    id, name, process.getId());

            String message = String.format(
                    "Link throw events (and none-intermediate-throws) are not supported in Flowable 7.2: "
                            + "the engine has no LinkEventDefinition and the parser silently drops the element, "
                            + "leaving a throw event with no behavior. "
                            + "Element id=%s, name='%s'. %s",
                    id, name, SUGGESTION);

            addError(errors, WERKFLOW_LINK_EVENT_UNSUPPORTED, process, throwEvent, message);
        }
    }
}
