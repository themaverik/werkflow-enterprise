package com.werkflow.engine.config.flowable;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.validation.ValidationError;
import org.flowable.validation.validator.ProcessLevelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hard-rejects BPMN that carries known-dead {@code flowable:*} extension attributes — the
 * F-EV-2 dead-attr class identified during the M4.13 event-type audit (ADR-009).
 *
 * <p>These four attributes were invented by Werkflow's own properties panel and added to signal /
 * message event elements. They survive the Flowable 7.2 parser (stored in
 * {@code FlowElement.getAttributes()} under the Flowable namespace) but are <em>never read</em> by
 * any Flowable built-in or any Werkflow delegate. The portal panel that emitted them was deleted
 * (enterprise {@code ceafee3}); however a hand-authored or LLM-generated BPMN could re-introduce
 * them and the engine would silently ignore them, giving the author false confidence.
 *
 * <p><b>Dead attribute names (all in the {@code http://flowable.org/bpmn} namespace):</b>
 * <ul>
 *   <li>{@code signalName} — shadow of the standard BPMN {@code signalRef} attribute; ignored.</li>
 *   <li>{@code correlationKey} — never wired to any engine interceptor; ignored.</li>
 *   <li>{@code webhookConnector} — connector key on a signal event; ignored (connectors are
 *       bound by message-name convention, not on the event element directly).</li>
 *   <li>{@code correlationExpression} — never evaluated; ignored.</li>
 * </ul>
 *
 * <p>The validator scans all flow elements in the process recursively (including those nested in
 * sub-processes). An error is added for each occurrence of any dead attribute; multiple dead attrs
 * on the same element each produce a separate error.
 *
 * @see <a href="../../../../../../../../../../docs/adr/ADR-009-bpmn-task-action-block-mapping.md">ADR-009</a>
 */
public class WerkflowDeadExtensionAttrValidator extends ProcessLevelValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WerkflowDeadExtensionAttrValidator.class);

    /** Stable error code emitted for every dead extension attribute found. */
    public static final String WERKFLOW_DEAD_EXTENSION_ATTR = "WERKFLOW_DEAD_EXTENSION_ATTR";

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    private static final Set<String> DEAD_ATTR_NAMES = Set.of(
            "signalName",
            "correlationKey",
            "webhookConnector",
            "correlationExpression"
    );

    @Override
    protected void executeValidation(BpmnModel bpmnModel, Process process, List<ValidationError> errors) {
        List<FlowElement> elements = process.findFlowElementsOfType(FlowElement.class, true);
        for (FlowElement element : elements) {
            Map<String, List<ExtensionAttribute>> allAttrs = element.getAttributes();
            // getAttributes() is keyed by attribute NAME (not namespace); filter by Flowable NS on each attr.
            for (String deadAttrName : DEAD_ATTR_NAMES) {
                List<ExtensionAttribute> candidates = allAttrs.getOrDefault(deadAttrName, List.of());
                for (ExtensionAttribute attr : candidates) {
                    if (FLOWABLE_NS.equals(attr.getNamespace())) {
                        String id   = element.getId();
                        String name = element.getName();

                        LOGGER.warn("WerkflowDeadExtensionAttrValidator: rejecting dead attribute "
                                + "'flowable:{}' on element id='{}' name='{}' in process '{}'",
                                deadAttrName, id, name, process.getId());

                        String message = String.format(
                                "Dead extension attribute 'flowable:%s' on element id=%s, name='%s'. "
                                        + "This attribute is not read by Flowable 7.2 or any Werkflow delegate "
                                        + "(F-EV-2 dead-attr class, ADR-009). Remove it; use the standard BPMN "
                                        + "attribute or connector binding instead.",
                                deadAttrName, id, name);

                        addError(errors, WERKFLOW_DEAD_EXTENSION_ATTR, process, element, message);
                    }
                }
            }
        }
    }
}
