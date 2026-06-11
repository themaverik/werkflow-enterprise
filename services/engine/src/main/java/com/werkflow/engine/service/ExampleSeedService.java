package com.werkflow.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.dto.SeedResult;
import com.werkflow.engine.dto.WorkflowSeedResult;
import com.werkflow.engine.exception.FormNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.engine.RepositoryService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds example workflow artifacts (forms, DMNs, BPMNs) for a given tenant (ADR-031 Phase C).
 *
 * <p>Resolves files from {@code classpath:examples/tenants/{tenantId}/} with automatic fallback
 * to {@code examples/tenants/default/} when no tenant-specific folder exists.
 *
 * <p>Each BPMN in the folder is treated as one workflow unit. Deployment order within each unit
 * is Forms → DMNs → BPMN, so Flowable's process engine can locate referenced decision tables
 * and form schemas at execution time.
 *
 * <p>All operations are idempotent: forms are skipped when the key already exists in
 * {@code form_schemas}; DMNs and BPMNs are skipped when a deployment with the same filename
 * and tenant already exists in Flowable ({@code enableDuplicateFiltering} enforces this at deploy
 * time; a pre-flight query gives an explicit skip-signal for result reporting).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExampleSeedService {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String EXAMPLES_BASE = "examples/tenants/";

    private final FormSchemaService formSchemaService;
    private final ProcessDefinitionService processDefinitionService;
    private final DmnRepositoryService dmnRepositoryService;
    private final RepositoryService repositoryService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper objectMapper;

    /**
     * Seeds all example workflows for {@code tenantId}.
     *
     * @param tenantId Flowable tenant identifier (must be non-null, non-blank)
     * @return aggregate seed result; never {@code null}
     */
    public SeedResult seedForTenant(String tenantId) {
        String folder = resolveFolder(tenantId);
        log.info("Seeding examples for tenant '{}' from folder '{}'", tenantId, folder);

        List<WorkflowSeedResult> results = new ArrayList<>();
        try {
            Resource[] bpmnResources = resourcePatternResolver.getResources(
                    "classpath:" + EXAMPLES_BASE + folder + "/bpmn/*.bpmn20.xml");
            for (Resource bpmnResource : bpmnResources) {
                String bpmnXml = readResource(bpmnResource);
                results.add(seedWorkflow(tenantId, folder, bpmnResource.getFilename(), bpmnXml));
            }
        } catch (IOException e) {
            log.error("Failed to scan BPMN resources for tenant '{}': {}", tenantId, e.getMessage());
        }

        SeedResult result = SeedResult.of(tenantId, folder, results);
        log.info("Seeding complete for tenant '{}': deployed={} skipped={} failed={}",
                tenantId, result.deployed(), result.skipped(), result.failed());
        return result;
    }

    private WorkflowSeedResult seedWorkflow(String tenantId, String folder,
                                             String bpmnFilename, String bpmnXml) {
        String processKey = stripBpmnSuffix(bpmnFilename);

        if (bpmnAlreadyDeployed(processKey, tenantId)) {
            log.info("Process '{}' already deployed for tenant '{}' — skipping", processKey, tenantId);
            return WorkflowSeedResult.skipped(processKey);
        }

        try {
            Document doc = parseBpmn(bpmnXml);
            Map<String, FormSchema.FormType> formRefs = extractFormRefs(doc);
            Set<String> decisionKeys = extractDecisionKeys(doc);

            List<String> newForms = seedForms(folder, formRefs);
            List<String> newDmns  = seedDmns(folder, tenantId, decisionKeys);

            processDefinitionService.deployExampleProcessDefinition(bpmnXml, bpmnFilename, tenantId);
            log.info("Deployed example process '{}' for tenant '{}'", processKey, tenantId);

            return WorkflowSeedResult.deployed(processKey, newForms, newDmns);

        } catch (Exception e) {
            log.error("Failed to seed workflow '{}' for tenant '{}': {}", processKey, tenantId, e.getMessage());
            return WorkflowSeedResult.failed(processKey, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Folder resolution
    // -------------------------------------------------------------------------

    private String resolveFolder(String tenantId) {
        try {
            Resource[] resources = resourcePatternResolver.getResources(
                    "classpath:" + EXAMPLES_BASE + tenantId + "/bpmn/*.bpmn20.xml");
            if (resources.length > 0) {
                log.debug("Using tenant-specific example folder for '{}'", tenantId);
                return tenantId;
            }
        } catch (IOException ignored) { /* fall through to default */ }
        return "default";
    }

    // -------------------------------------------------------------------------
    // Form seeding
    // -------------------------------------------------------------------------

    private List<String> seedForms(String folder, Map<String, FormSchema.FormType> formRefs) {
        List<String> newForms = new ArrayList<>();
        for (Map.Entry<String, FormSchema.FormType> entry : formRefs.entrySet()) {
            String formKey = entry.getKey();
            FormSchema.FormType formType = entry.getValue();

            if (formExists(formKey)) {
                log.debug("Form '{}' already exists — skipping", formKey);
                continue;
            }

            String resourcePath = EXAMPLES_BASE + folder + "/forms/" + formKey + ".json";
            try {
                String json = readClasspathFile(resourcePath);
                JsonNode schema = objectMapper.readTree(json);
                formSchemaService.saveFormSchema(
                        formKey, schema, "Example form: " + formKey, formType, "system");
                newForms.add(formKey);
                log.info("Seeded form '{}' (type={})", formKey, formType);
            } catch (IOException e) {
                log.warn("No form file at '{}' for key '{}' — skipping ({})",
                        resourcePath, formKey, e.getMessage());
            }
        }
        return List.copyOf(newForms);
    }

    private boolean formExists(String formKey) {
        try {
            formSchemaService.loadFormSchema(formKey);
            return true;
        } catch (FormNotFoundException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // DMN seeding
    // -------------------------------------------------------------------------

    private List<String> seedDmns(String folder, String tenantId, Set<String> decisionKeys)
            throws IOException {
        if (decisionKeys.isEmpty()) {
            return List.of();
        }

        Resource[] dmnResources = resourcePatternResolver.getResources(
                "classpath:" + EXAMPLES_BASE + folder + "/dmn/*.dmn");

        List<String> newDmns = new ArrayList<>();
        for (Resource dmnResource : dmnResources) {
            String filename = dmnResource.getFilename();
            String dmnXml   = readResource(dmnResource);

            Set<String> decisionIdsInFile = extractDmnDecisionIds(dmnXml);
            if (decisionIdsInFile.stream().noneMatch(decisionKeys::contains)) {
                continue;
            }

            if (dmnAlreadyDeployed(filename, tenantId)) {
                log.debug("DMN '{}' already deployed for tenant '{}' — skipping", filename, tenantId);
                continue;
            }

            dmnRepositoryService.createDeployment()
                    .name(filename)
                    .tenantId(tenantId)
                    .enableDuplicateFiltering()
                    .addClasspathResource(EXAMPLES_BASE + folder + "/dmn/" + filename)
                    .deploy();
            newDmns.add(filename);
            log.info("Deployed DMN '{}' for tenant '{}'", filename, tenantId);
        }
        return List.copyOf(newDmns);
    }

    private boolean dmnAlreadyDeployed(String filename, String tenantId) {
        return dmnRepositoryService.createDeploymentQuery()
                .deploymentName(filename)
                .deploymentTenantId(tenantId)
                .count() > 0;
    }

    private boolean bpmnAlreadyDeployed(String processKey, String tenantId) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .processDefinitionTenantId(tenantId)
                .count() > 0;
    }

    // -------------------------------------------------------------------------
    // BPMN / DMN parsing (XXE-hardened DOM, same approach as BpmnIndicatorScanner)
    // -------------------------------------------------------------------------

    /**
     * Extracts form key → form type pairs from a BPMN document.
     *
     * <ul>
     *   <li>Start events → {@code TASK_FORM}</li>
     *   <li>User tasks with {@code flowable:actionType="HUMAN_APPROVAL"} → {@code APPROVAL}</li>
     *   <li>Other user tasks → {@code TASK_FORM}</li>
     * </ul>
     *
     * <p>Duplicate keys are deduplicated; the first occurrence wins (start event form type is
     * preserved when the same key appears on both a start event and a user task).
     */
    Map<String, FormSchema.FormType> extractFormRefs(Document doc) {
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

    /**
     * Extracts the {@code decisionTableReferenceKey} values from all
     * {@code <serviceTask flowable:type="dmn">} elements.
     */
    Set<String> extractDecisionKeys(Document doc) {
        Set<String> keys = new LinkedHashSet<>();
        NodeList tasks = doc.getElementsByTagNameNS("*", "serviceTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            if (!"dmn".equalsIgnoreCase(task.getAttributeNS(FLOWABLE_NS, "type"))) continue;

            NodeList fields = task.getElementsByTagNameNS(FLOWABLE_NS, "field");
            for (int j = 0; j < fields.getLength(); j++) {
                Element field = (Element) fields.item(j);
                if (!"decisionTableReferenceKey".equals(field.getAttribute("name"))) continue;

                NodeList strings = field.getElementsByTagNameNS(FLOWABLE_NS, "string");
                if (strings.getLength() > 0) {
                    String key = strings.item(0).getTextContent().trim();
                    if (!key.isBlank()) keys.add(key);
                }
            }
        }
        return Set.copyOf(keys);
    }

    /**
     * Extracts all {@code <decision id="...">} identifiers from a DMN XML string.
     */
    Set<String> extractDmnDecisionIds(String dmnXml) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            Document doc = parseXml(dmnXml);
            NodeList decisions = doc.getElementsByTagNameNS("*", "decision");
            for (int i = 0; i < decisions.getLength(); i++) {
                String id = ((Element) decisions.item(i)).getAttribute("id");
                if (id != null && !id.isBlank()) ids.add(id);
            }
        } catch (Exception e) {
            log.warn("Failed to parse DMN XML for decision IDs: {}", e.getMessage());
        }
        return Set.copyOf(ids);
    }

    private Document parseBpmn(String xml) {
        return parseXml(xml);
    }

    private Document parseXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("XML is empty");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Classpath reading helpers
    // -------------------------------------------------------------------------

    private String readResource(Resource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String readClasspathFile(String path) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath:" + path);
        if (resources.length == 0) {
            throw new IOException("Classpath resource not found: " + path);
        }
        return readResource(resources[0]);
    }

    private static String stripBpmnSuffix(String filename) {
        if (filename != null && filename.endsWith(".bpmn20.xml")) {
            return filename.substring(0, filename.length() - ".bpmn20.xml".length());
        }
        return filename;
    }
}
