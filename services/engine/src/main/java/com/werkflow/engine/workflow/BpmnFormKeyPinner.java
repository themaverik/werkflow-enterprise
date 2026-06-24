package com.werkflow.engine.workflow;

import com.werkflow.engine.service.FormSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Pins every static {@code flowable:formKey} in a BPMN definition to the form's current
 * active version at bundle-deploy time (ADR-026 P2 / option F1): {@code "key"} is rewritten
 * to {@code "key@<version>"} so an in-flight instance always resolves the form definition it
 * was deployed with, instead of drifting to whatever "latest" becomes.
 *
 * <p>Forms are Werkflow-managed schema rows (not Flowable artifacts), so the pin is a
 * Werkflow convention carried in the formKey string and honoured by
 * {@link FormSchemaService#loadFormSchemaByRef(String)} at runtime — Flowable's deployment
 * binding cannot touch them.
 *
 * <p>Left unchanged: EL expressions ({@code ${...}}) and already-pinned keys (containing
 * {@code @}). A missing form now fails the deploy (see {@link DeployReferenceValidator}) —
 * if the validator fires first this method will not be reached; if it is somehow bypassed,
 * the {@code FormNotFoundException} is re-thrown here as a defence-in-depth backstop.
 * Reads/writes via DOM rather than Flowable's
 * {@code BpmnModel} for the same round-trip reason as {@link BpmnBundleRefExtractor}.
 */
@Component
@RequiredArgsConstructor
public class BpmnFormKeyPinner {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    private final FormSchemaService formSchemaService;

    /**
     * @param bpmnXml  raw BPMN 2.0 XML
     * @param tenantId the tenant owning this bundle (null/blank → "default")
     * @return the XML with static formKeys pinned to their current version; the original
     *         string (unchanged) if there was nothing to pin
     * @throws IllegalArgumentException if the XML is malformed
     */
    public String pinFormKeys(String bpmnXml, String tenantId) {
        Document doc = parse(bpmnXml);

        boolean changed = false;
        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            Attr formKeyAttr = el.getAttributeNodeNS(FLOWABLE_NS, "formKey");
            if (formKeyAttr == null) {
                continue;
            }
            String value = formKeyAttr.getValue().trim();
            if (value.isEmpty() || value.startsWith("${") || value.contains("@")) {
                continue; // expression, blank, or already pinned
            }
            // FormNotFoundException here means the validator was bypassed — rethrow as a
            // defence-in-depth backstop so the deploy never silently proceeds with a bare key.
            int version = formSchemaService.loadFormSchema(value, tenantId).getVersion();
            formKeyAttr.setValue(value + "@" + version);
            changed = true;
        }

        return changed ? serialize(doc) : bpmnXml;
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
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse BPMN XML: " + e.getMessage(), e);
        }
    }

    private String serialize(Document doc) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize BPMN XML: " + e.getMessage(), e);
        }
    }
}
