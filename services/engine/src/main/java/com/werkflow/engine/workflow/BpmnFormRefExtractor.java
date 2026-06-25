package com.werkflow.engine.workflow;

import com.werkflow.engine.dto.FormSchema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts form key → form type pairs from a parsed BPMN 2.0 {@link Document}.
 *
 * <ul>
 *   <li>Start events → {@link FormSchema.FormType#TASK_FORM}</li>
 *   <li>User tasks with {@code flowable:actionType="HUMAN_APPROVAL"} → {@link FormSchema.FormType#APPROVAL}</li>
 *   <li>Other user tasks → {@link FormSchema.FormType#TASK_FORM}</li>
 * </ul>
 *
 * <p>Duplicate keys are deduplicated; the first occurrence wins (start event form type is
 * preserved when the same key appears on both a start event and a user task).
 *
 * <p>Pure stateless utility — all methods are static. The caller is responsible for
 * XXE-hardened DOM parsing before passing the {@link Document}.
 */
public final class BpmnFormRefExtractor {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    private BpmnFormRefExtractor() {
        // utility class — no instances
    }

    /**
     * Extracts form key → form type pairs from a BPMN document.
     *
     * @param doc a namespace-aware {@link Document} parsed from a BPMN 2.0 XML source
     * @return immutable map of formKey to {@link FormSchema.FormType}; insertion order
     *         follows document order (start events before user tasks); first occurrence wins
     */
    public static Map<String, FormSchema.FormType> extractFormRefs(Document doc) {
        Map<String, FormSchema.FormType> refs = new LinkedHashMap<>();

        NodeList startEvents = doc.getElementsByTagNameNS("*", "startEvent");
        for (int i = 0; i < startEvents.getLength(); i++) {
            String key = ((Element) startEvents.item(i)).getAttributeNS(FLOWABLE_NS, "formKey");
            if (key != null && !key.isBlank()) {
                refs.putIfAbsent(key, FormSchema.FormType.TASK_FORM);
            }
        }

        NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            Element task = (Element) userTasks.item(i);
            String key = task.getAttributeNS(FLOWABLE_NS, "formKey");
            if (key == null || key.isBlank()) continue;
            String actionType = task.getAttributeNS(FLOWABLE_NS, "actionType");
            FormSchema.FormType type = "HUMAN_APPROVAL".equals(actionType)
                    ? FormSchema.FormType.APPROVAL
                    : FormSchema.FormType.TASK_FORM;
            refs.putIfAbsent(key, type);
        }

        return Map.copyOf(refs);
    }
}
