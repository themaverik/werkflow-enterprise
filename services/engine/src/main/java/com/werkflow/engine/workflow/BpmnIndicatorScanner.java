package com.werkflow.engine.workflow;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Scans a BPMN 2.0 XML string and returns two boolean indicator flags:
 * <ul>
 *   <li>{@code hasDmn} — at least one {@code <serviceTask flowable:type="dmn">} is present
 *       (ADR-026 DMN binding pattern; {@code <businessRuleTask>} is intentionally excluded —
 *       it routes to Drools in Flowable 7.2 and never reaches the DMN engine)</li>
 *   <li>{@code hasConnector} — at least one {@code <serviceTask>} whose
 *       {@code flowable:delegateExpression} resolves to a known connector bean
 *       ({@code restConnectorDelegate}, {@code connectorWebhookDelegate}, or
 *       {@code connectorCallDelegate})</li>
 * </ul>
 *
 * <p>Reads directly from the raw XML (DOM) rather than via Flowable's {@code BpmnModel},
 * for the same reason as {@link BpmnBundleRefExtractor}: the model does not reliably
 * round-trip custom {@code flowable:} extension attributes.
 *
 * <p>XXE hardening is applied identically to {@link BpmnBundleRefExtractor#parse}.
 */
@Component
public class BpmnIndicatorScanner {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    /**
     * Exact connector bean names recognised as connector service tasks.
     * The regex requires the name to be surrounded by optional whitespace and the
     * closing brace — preventing substring false-positives like {@code myRestConnectorDelegateWrapper}.
     */
    private static final Pattern CONNECTOR_DELEGATE_PATTERN = Pattern.compile(
        "\\$\\{\\s*(restConnectorDelegate|connectorWebhookDelegate|connectorCallDelegate)\\s*}");

    /** Indicator flags computed from a single BPMN definition. */
    public record Indicators(boolean hasDmn, boolean hasConnector) {}

    /**
     * Scans {@code bpmnXml} and returns the two indicator flags.
     *
     * @param bpmnXml raw BPMN 2.0 XML
     * @return computed indicators; never {@code null}
     * @throws IllegalArgumentException if the XML is malformed or empty
     */
    public Indicators scan(String bpmnXml) {
        Document doc = parse(bpmnXml);

        boolean hasDmn = false;
        boolean hasConnector = false;

        NodeList tasks = doc.getElementsByTagNameNS("*", "serviceTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);

            if (!hasDmn && "dmn".equalsIgnoreCase(task.getAttributeNS(FLOWABLE_NS, "type"))) {
                hasDmn = true;
            }

            if (!hasConnector) {
                String delegateExpr = task.getAttributeNS(FLOWABLE_NS, "delegateExpression");
                if (delegateExpr != null && CONNECTOR_DELEGATE_PATTERN.matcher(delegateExpr).matches()) {
                    hasConnector = true;
                }
            }

            if (hasDmn && hasConnector) {
                break; // both found; no need to scan further
            }
        }

        return new Indicators(hasDmn, hasConnector);
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
