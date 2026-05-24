package com.werkflow.engine.workflow;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Extracts the bundle-relevant references from a BPMN 2.0 XML string: the process
 * key and the set of DMN decision keys the process invokes.
 *
 * <p>In Flowable 7.2 a DMN decision is invoked by a {@code serviceTask} with
 * {@code flowable:type="dmn"} and a {@code decisionTableReferenceKey} field extension —
 * NOT by {@code businessRuleTask}, which routes to the legacy Drools engine and never
 * reaches the DMN engine (see the ADR-026 re-audit). This extractor therefore reads the
 * {@code decisionTableReferenceKey} string value from every DMN service task.
 *
 * <p>Read directly from the raw XML (DOM) rather than via Flowable's {@code BpmnModel},
 * because the model does not reliably round-trip custom {@code flowable:} extensions (cf.
 * the SendTask {@code implementationType} gotcha). Used by
 * {@link com.werkflow.engine.service.BundleDeploymentService} to co-deploy a process and its
 * referenced DMNs under one {@code parentDeploymentId} (ADR-026).
 */
@Component
public class BpmnBundleRefExtractor {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    /** Bundle references parsed from a single BPMN definition. */
    public record BundleRefs(String processKey, Set<String> decisionRefs) {}

    /**
     * @param bpmnXml raw BPMN 2.0 XML
     * @return the process key and distinct, non-blank DMN decision keys (insertion order preserved)
     * @throws IllegalArgumentException if the XML is malformed or declares no process
     */
    public BundleRefs extract(String bpmnXml) {
        Document doc = parse(bpmnXml);

        String processKey = firstProcessId(doc);
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalArgumentException("BPMN XML declares no <process id=...>");
        }

        Set<String> decisionRefs = new LinkedHashSet<>();
        NodeList tasks = doc.getElementsByTagNameNS("*", "serviceTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            if (!"dmn".equalsIgnoreCase(task.getAttributeNS(FLOWABLE_NS, "type"))) {
                continue;
            }
            String key = decisionTableReferenceKey(task);
            if (key != null && !key.isBlank()) {
                decisionRefs.add(key.trim());
            }
        }
        return new BundleRefs(processKey, Set.copyOf(decisionRefs));
    }

    /**
     * Reads the static {@code decisionTableReferenceKey} value from a DMN service task's
     * {@code <flowable:field name="decisionTableReferenceKey"><flowable:string>…} extension.
     * Returns {@code null} when the key is absent or supplied as an expression (not statically
     * resolvable, so not bundleable).
     */
    private String decisionTableReferenceKey(Element dmnServiceTask) {
        NodeList fields = dmnServiceTask.getElementsByTagNameNS(FLOWABLE_NS, "field");
        for (int i = 0; i < fields.getLength(); i++) {
            Element field = (Element) fields.item(i);
            if (!"decisionTableReferenceKey".equals(field.getAttribute("name"))) {
                continue;
            }
            NodeList strings = field.getElementsByTagNameNS(FLOWABLE_NS, "string");
            if (strings.getLength() > 0) {
                return strings.item(0).getTextContent();
            }
        }
        return null;
    }

    private String firstProcessId(Document doc) {
        NodeList processes = doc.getElementsByTagNameNS("*", "process");
        for (int i = 0; i < processes.getLength(); i++) {
            Node node = processes.item(i);
            if (node instanceof Element el) {
                String id = el.getAttribute("id");
                if (id != null && !id.isBlank()) {
                    return id.trim();
                }
            }
        }
        return null;
    }

    private Document parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML is empty");
        }
        try {
            // Factory built per-call: DocumentBuilderFactory/DocumentBuilder are not thread-safe.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE hardening — no DOCTYPE, no external entities.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse BPMN XML: " + e.getMessage(), e);
        }
    }
}
