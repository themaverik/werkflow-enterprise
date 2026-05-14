package com.werkflow.admin.designtime.bpmn.service;

import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse;
import com.werkflow.admin.designtime.bpmn.dto.VariableAtActivityResponse.ProcessVariableEntry;
import com.werkflow.admin.designtime.platform.client.EngineClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traverses a deployed BPMN process definition and accumulates the set of process
 * variables reachable at a given activity, with provenance information.
 *
 * <h3>Traversal approach</h3>
 * <ol>
 *   <li>Fetch the deployed BPMN XML from the engine service.</li>
 *   <li>Perform a topological traversal from the start event to the target activity
 *       following sequence flows (ignoring boundary events and sub-process internals).</li>
 *   <li>For each service/user/script task encountered in the traversal path, extract
 *       variable names from {@code flowable:field} extensions with {@code name="output"}
 *       or {@code name="extractFields"} attributes, and from {@code camunda:outputParameter}
 *       elements.</li>
 * </ol>
 *
 * <p>This is a best-effort analysis — it covers the Werkflow delegate patterns
 * ({@code restConnectorDelegate}, {@code notificationDelegate}, etc.).
 * Custom Java delegates that set variables imperatively are not detected.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessVariableScopeService {

    private static final Pattern EXTRACT_FIELDS_PATTERN =
            Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*:");

    private static final int MAX_TRAVERSAL_DEPTH = 200;

    private final RestTemplate restTemplate;

    @Value("${app.engine-service.url:http://werkflow-engine:8081}")
    private String engineBaseUrl;

    /**
     * Returns the variables accumulated from the process start up to (but not including)
     * the named {@code activityId}.
     *
     * @param processDefId Flowable process definition ID (includes the :version:deploymentId suffix)
     * @param activityId   BPMN element ID of the target activity
     * @return response with accumulated variables and their provenance
     */
    public VariableAtActivityResponse variablesAt(String tenantId, String processDefId, String activityId, String bearerToken) {
        String bpmnXml = fetchBpmnXml(tenantId, processDefId, bearerToken);
        Document doc = parseBpmnXml(bpmnXml);
        List<ProcessVariableEntry> variables = traverse(doc, activityId);
        return new VariableAtActivityResponse(variables);
    }

    // -------------------------------------------------------------------------
    // XML fetch
    // -------------------------------------------------------------------------

    private String fetchBpmnXml(String tenantId, String processDefId, String bearerToken) {
        String url = engineBaseUrl + "/api/process-definitions/key/"
                + processDefId + "/xml";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            String xml = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            if (xml == null || xml.isBlank()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No BPMN XML returned for processDefId: " + processDefId);
            }
            return xml;
        } catch (RestClientException e) {
            log.warn("ProcessVariableScopeService: engine unreachable for processDefId={} — {}",
                    processDefId, e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Engine service unavailable: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // XML parsing
    // -------------------------------------------------------------------------

    private Document parseBpmnXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid BPMN XML: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Traversal
    // -------------------------------------------------------------------------

    /**
     * BFS traversal from start events to the target activity.
     * Collects variables set by tasks that precede the target.
     */
    private List<ProcessVariableEntry> traverse(Document doc, String targetActivityId) {
        // Index all elements by their id attribute
        Map<String, Element> elementById = indexElements(doc);

        // Build adjacency: source → [targets] via sequenceFlows
        Map<String, List<String>> adjacency = buildAdjacency(doc, elementById);

        // BFS
        Set<String> startIds = findStartEvents(doc);
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>(startIds);

        while (!queue.isEmpty() && visited.size() < MAX_TRAVERSAL_DEPTH) {
            String current = queue.poll();
            if (visited.contains(current)) continue;
            if (targetActivityId.equals(current)) break; // stop before target
            visited.add(current);
            List<String> successors = adjacency.getOrDefault(current, List.of());
            queue.addAll(successors);
        }

        // Collect variables from visited tasks
        Map<String, ProcessVariableEntry> variables = new LinkedHashMap<>();
        for (String id : visited) {
            Element el = elementById.get(id);
            if (el == null) continue;
            extractVariables(el, id, variables);
        }
        return new ArrayList<>(variables.values());
    }

    private Map<String, Element> indexElements(Document doc) {
        Map<String, Element> index = new HashMap<>();
        NodeList allElements = doc.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element el) {
                String id = el.getAttribute("id");
                if (!id.isBlank()) index.put(id, el);
            }
        }
        return index;
    }

    private Map<String, List<String>> buildAdjacency(Document doc, Map<String, Element> elementById) {
        Map<String, List<String>> adj = new HashMap<>();
        // Handle both namespaced and non-namespaced sequenceFlow
        NodeList flows = doc.getElementsByTagNameNS("*", "sequenceFlow");
        if (flows.getLength() == 0) {
            flows = doc.getElementsByTagName("sequenceFlow");
        }
        for (int i = 0; i < flows.getLength(); i++) {
            if (flows.item(i) instanceof Element flow) {
                String source = flow.getAttribute("sourceRef");
                String target = flow.getAttribute("targetRef");
                if (!source.isBlank() && !target.isBlank()) {
                    adj.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                }
            }
        }
        return adj;
    }

    private Set<String> findStartEvents(Document doc) {
        Set<String> ids = new LinkedHashSet<>();
        NodeList starts = doc.getElementsByTagNameNS("*", "startEvent");
        if (starts.getLength() == 0) starts = doc.getElementsByTagName("startEvent");
        for (int i = 0; i < starts.getLength(); i++) {
            if (starts.item(i) instanceof Element el) {
                String id = el.getAttribute("id");
                if (!id.isBlank()) ids.add(id);
            }
        }
        return ids;
    }

    private void extractVariables(Element el, String activityId, Map<String, ProcessVariableEntry> out) {
        String taskName = el.getAttribute("name");
        String displayTask = taskName.isBlank() ? activityId : taskName;

        // Pattern 1: flowable:field name="extractFields" value="varA:$.a,varB:$.b"
        NodeList fields = el.getElementsByTagNameNS("*", "field");
        for (int i = 0; i < fields.getLength(); i++) {
            if (fields.item(i) instanceof Element field) {
                String name = field.getAttribute("name");
                if ("extractFields".equals(name) || "outputMappings".equals(name)) {
                    String value = getFieldValue(field);
                    parseExtractFields(value, activityId, displayTask, out);
                } else if ("outputVariable".equals(name) || "resultVariable".equals(name)) {
                    String varName = getFieldValue(field);
                    if (!varName.isBlank()) {
                        out.put(varName, new ProcessVariableEntry(varName, null, activityId, displayTask));
                    }
                }
            }
        }

        // Pattern 2: flowable:outputParameter / camunda:outputParameter
        NodeList outputParams = el.getElementsByTagNameNS("*", "outputParameter");
        for (int i = 0; i < outputParams.getLength(); i++) {
            if (outputParams.item(i) instanceof Element param) {
                String varName = param.getAttribute("name");
                if (!varName.isBlank()) {
                    out.put(varName, new ProcessVariableEntry(varName, null, activityId, displayTask));
                }
            }
        }
    }

    private String getFieldValue(Element fieldEl) {
        NodeList strings = fieldEl.getElementsByTagNameNS("*", "string");
        if (strings.getLength() > 0) return strings.item(0).getTextContent().trim();
        NodeList expressions = fieldEl.getElementsByTagNameNS("*", "expression");
        if (expressions.getLength() > 0) return expressions.item(0).getTextContent().trim();
        String strAttr = fieldEl.getAttribute("stringValue");
        return strAttr.isBlank() ? fieldEl.getAttribute("expression") : strAttr;
    }

    private void parseExtractFields(String value, String activityId, String taskName,
                                    Map<String, ProcessVariableEntry> out) {
        if (value == null || value.isBlank()) return;
        Matcher m = EXTRACT_FIELDS_PATTERN.matcher(value);
        while (m.find()) {
            String varName = m.group(1);
            out.put(varName, new ProcessVariableEntry(varName, null, activityId, taskName));
        }
    }
}
