package com.werkflow.engine.init;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.service.ProcessDefinitionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deploys example BPMN processes from classpath:processes/examples/ on startup.
 * Controlled by the werkflow.examples.deploy-on-startup flag (default: false).
 *
 * <p>Optional reset mode (werkflow.examples.reset-on-startup, default false): before
 * deploying each example, removes every prior Flowable deployment registered under the
 * same name via RepositoryService.deleteDeployment(id, true). Use to clear accumulated
 * version history (e.g. Leave v119, Event Ticket v104) from dev databases predating
 * enableDuplicateFiltering(). After one boot with the flag set, examples redeploy fresh
 * at version 1; subsequent restarts without the flag stay at version 1 thanks to
 * duplicate filtering.
 */
@Slf4j
@Component
@DependsOn("dmnExampleDeployer")
public class ProcessExampleDeployer {

    private static final String BPMN_SUFFIX = ".bpmn20.xml";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String FORMS_PREFIX = "examples/tenants/default/forms/";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final ProcessDefinitionService processDefinitionService;
    private final RepositoryService repositoryService;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ResourcePatternResolver resourcePatternResolver;
    private final boolean deployOnStartup;
    private final boolean resetOnStartup;
    private final String exampleTenantId;

    public ProcessExampleDeployer(ProcessDefinitionService processDefinitionService,
                                   RepositoryService repositoryService,
                                   JdbcTemplate jdbcTemplate,
                                   NamedParameterJdbcTemplate namedJdbc,
                                   ResourcePatternResolver resourcePatternResolver,
                                   @Value("${werkflow.examples.deploy-on-startup:false}") boolean deployOnStartup,
                                   @Value("${werkflow.examples.reset-on-startup:false}") boolean resetOnStartup,
                                   @Value("${werkflow.examples.tenant-id:default}") String exampleTenantId) {
        this.processDefinitionService = processDefinitionService;
        this.repositoryService = repositoryService;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
        this.resourcePatternResolver = resourcePatternResolver;
        this.deployOnStartup = deployOnStartup;
        this.resetOnStartup = resetOnStartup;
        this.exampleTenantId = exampleTenantId;
    }

    @PostConstruct
    public void deploy() throws IOException {
        if (!deployOnStartup) {
            log.info("Example process deployment skipped (werkflow.examples.deploy-on-startup=false)");
            return;
        }

        Resource[] resources = resourcePatternResolver.getResources("classpath:examples/tenants/default/bpmn/*.bpmn20.xml");
        if (resources.length == 0) {
            log.info("No example BPMN files found in classpath:examples/tenants/default/bpmn/");
            return;
        }

        Set<String> currentKeys = new HashSet<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            String processKey = stripBpmnSuffix(filename);
            try {
                if (resetOnStartup) {
                    resetExampleDraftAndBundle(processKey);
                    resetExampleDeployments(filename);
                }
                String bpmnXml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                ProcessDefinitionResponse def = processDefinitionService.deployExampleProcessDefinition(bpmnXml, filename, exampleTenantId);
                upsertCatalogEntry(def.getKey(), def.getName(), bpmnXml, exampleTenantId);
                seedFormSchemas(bpmnXml, def.getKey());
                currentKeys.add(def.getKey());
                log.info("Deployed example process: {}", filename);
            } catch (Exception e) {
                log.error("Failed to deploy example process '{}': {}", filename, e.getMessage());
            }
        }

        pruneOrphanSystemDrafts(currentKeys);
    }

    /**
     * Parses the deployed BPMN XML and upserts each referenced form schema into
     * {@code form_schemas}. Uses ON CONFLICT DO UPDATE so that classpath updates
     * propagate on the next engine restart (same contract as V8 migration seeding).
     *
     * <p>Only seeds forms whose JSON file exists on classpath at
     * {@code examples/tenants/default/forms/{formKey}.json}. Missing files are
     * logged at WARN and skipped — they do not fail the deployment.
     *
     * <p>FormType is derived from element context: startEvent → TASK_FORM;
     * userTask with actionType HUMAN_APPROVAL → APPROVAL; other userTask → TASK_FORM.
     * Duplicate formKey values in the same BPMN are deduplicated; the first
     * occurrence wins (matching ExampleSeedService.extractFormRefs behaviour).
     */
    private void seedFormSchemas(String bpmnXml, String processKey) {
        Map<String, String> formRefs;
        try {
            formRefs = extractFormRefs(bpmnXml);
        } catch (Exception e) {
            log.warn("Could not parse BPMN XML for form refs in '{}': {}", processKey, e.getMessage());
            return;
        }

        for (Map.Entry<String, String> entry : formRefs.entrySet()) {
            String formKey = entry.getKey();
            String formType = entry.getValue();
            String resourcePath = FORMS_PREFIX + formKey + ".json";
            try {
                Resource formResource = resourcePatternResolver.getResource("classpath:" + resourcePath);
                if (!formResource.exists()) {
                    log.warn("No form file at '{}' for key '{}' — skipping", resourcePath, formKey);
                    continue;
                }
                String jsonStr = new String(formResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String formName = extractNameFromJson(jsonStr, formKey);
                jdbcTemplate.update(
                    """
                    INSERT INTO form_schemas (form_key, version, name, schema_json, description, form_type,
                                             is_active, created_by, updated_by)
                    VALUES (?, 1, ?, ?::jsonb, ?, ?, true, 'system', 'system')
                    ON CONFLICT (form_key, version) DO UPDATE
                        SET schema_json = EXCLUDED.schema_json,
                            name        = EXCLUDED.name,
                            form_type   = EXCLUDED.form_type,
                            is_active   = true,
                            updated_by  = 'system'
                    """,
                    formKey, formName, jsonStr, "Example form: " + formKey, formType);
                log.info("Seeded form schema '{}' (type={}) for process '{}'", formKey, formType, processKey);
            } catch (Exception e) {
                log.warn("Failed to seed form '{}' for process '{}': {}", formKey, processKey, e.getMessage());
            }
        }
    }

    /**
     * Reads the top-level {@code "name"} field from the form-js JSON schema.
     * Falls back to a title-cased derivation from the form key if the field is absent or blank
     * (e.g. {@code "leave-request-form"} → {@code "Leave Request Form"}).
     */
    private static String extractNameFromJson(String jsonStr, String formKey) {
        try {
            JsonNode root = JSON_MAPPER.readTree(jsonStr);
            JsonNode nameNode = root.get("name");
            if (nameNode != null && nameNode.isTextual() && !nameNode.asText().isBlank()) {
                return nameNode.asText();
            }
        } catch (Exception e) {
            // fall through to key-based fallback
        }
        return Arrays.stream(formKey.split("[-_]"))
            .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    /**
     * Extracts formKey → formType pairs from BPMN XML.
     * Returns a LinkedHashMap to preserve insertion order; first occurrence of a key wins.
     */
    private Map<String, String> extractFormRefs(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> refs = new java.util.LinkedHashMap<>();

        NodeList startEvents = doc.getElementsByTagNameNS("*", "startEvent");
        for (int i = 0; i < startEvents.getLength(); i++) {
            String key = ((Element) startEvents.item(i)).getAttributeNS(FLOWABLE_NS, "formKey");
            if (key != null && !key.isBlank()) {
                refs.putIfAbsent(key, "TASK_FORM");
            }
        }

        NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            Element task = (Element) userTasks.item(i);
            String key = task.getAttributeNS(FLOWABLE_NS, "formKey");
            if (key == null || key.isBlank()) continue;
            String actionType = task.getAttributeNS(FLOWABLE_NS, "actionType");
            String type = "HUMAN_APPROVAL".equals(actionType) ? "APPROVAL" : "TASK_FORM";
            refs.putIfAbsent(key, type);
        }

        return refs;
    }

    /**
     * Removes system-seeded process_draft rows whose BPMN is no longer on classpath.
     * Only touches rows with {@code created_by = 'system'} to protect admin-created drafts.
     * Skips silently if {@code currentKeys} is empty (nothing to prune against).
     * Catches DataAccessException so a missing/migrating table never breaks startup.
     */
    private void pruneOrphanSystemDrafts(Set<String> currentKeys) {
        if (currentKeys.isEmpty()) {
            return;
        }
        try {
            int deleted = namedJdbc.update(
                """
                DELETE FROM process_draft
                WHERE created_by = 'system'
                  AND tenant_id = :tenantId
                  AND process_key NOT IN (:currentKeys)
                """,
                Map.of("tenantId", exampleTenantId, "currentKeys", currentKeys));
            if (deleted > 0) {
                log.info("Pruned {} orphan system-seeded draft(s) no longer on classpath", deleted);
            }
        } catch (DataAccessException e) {
            log.debug("Orphan draft pruning skipped: {}", e.getMessage());
        }
    }

    /**
     * Ensures a process_draft catalog entry exists for a deployed example. Uses
     * ON CONFLICT DO NOTHING so admin-edited drafts are never overwritten.
     * Skips silently if process_draft is absent (e.g. Flyway not yet applied).
     *
     * <p>The conflict target uses (process_key, tenant_id) — the composite unique
     * constraint introduced in V17 (the single-column process_key constraint was dropped).
     */
    private void upsertCatalogEntry(String processKey, String name, String bpmnXml, String tenantId) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO process_draft (process_key, tenant_id, name, bpmn_xml, created_by, updated_by, department_code)
                VALUES (?, ?, ?, ?, 'system', 'system', NULL)
                ON CONFLICT (process_key, tenant_id) DO NOTHING
                """,
                processKey, tenantId, name, bpmnXml);
        } catch (DataAccessException e) {
            log.debug("Catalog entry skipped for '{}': {}", processKey, e.getMessage());
        }
    }

    /**
     * Removes orphan example drafts + bundle index for a process key via JdbcTemplate
     * (implicit per-statement transaction; sidesteps the AOP self-call limitation of
     * a @PostConstruct calling a @Transactional method on the same bean). Either table
     * may be absent on fresh DBs that have not run Flyway — caught and logged at debug.
     *
     * <p><strong>Non-atomic with subsequent deploy:</strong> these JdbcTemplate deletes
     * auto-commit per statement and are not enrolled in the Flowable deployment
     * transaction. If the deploy fails after this method succeeds, the orphan rows are
     * gone but Flowable is unchanged — re-running with the flag set converges the state.
     *
     * <p>Assumes the BPMN filename stripped of {@value #BPMN_SUFFIX} equals the
     * {@code process_key} stored in those tables (true for shipped examples).
     */
    private void resetExampleDraftAndBundle(String processKey) {
        try {
            int n = jdbcTemplate.update("DELETE FROM process_draft WHERE process_key = ? AND tenant_id = ?",
                    processKey, exampleTenantId);
            if (n > 0) {
                log.info("Reset mode: removed {} orphan draft(s) for '{}'", n, processKey);
            }
        } catch (DataAccessException e) {
            log.debug("Reset mode: process_draft cleanup skipped for '{}' (table likely absent)", processKey);
        }
        try {
            jdbcTemplate.update("DELETE FROM process_bundle WHERE process_key = ?", processKey);
        } catch (DataAccessException e) {
            log.debug("Reset mode: process_bundle cleanup skipped for '{}' (table likely absent)", processKey);
        }
    }

    private static String stripBpmnSuffix(String filename) {
        if (filename != null && filename.endsWith(BPMN_SUFFIX)) {
            return filename.substring(0, filename.length() - BPMN_SUFFIX.length());
        }
        return filename;
    }

    /**
     * Deletes every Flowable deployment registered under {@code deploymentName} (the
     * example's BPMN filename — the same string used by deployExampleProcessDefinition
     * as the deployment name + duplicate-filter key), cascading through process
     * definitions, bytearrays, and synchronously-written history rows. Skips if no
     * deployments exist. Cascade=true is essential — direct SQL DELETE on
     * ACT_RE_DEPLOYMENT fails on Flowable's FK constraints.
     *
     * <p><strong>Filename stability:</strong> the deployment name equals the resource
     * filename. Renaming a BPMN file breaks both this reset query and the duplicate
     * filter at deploy time.
     *
     * <p><strong>Async history caveat:</strong> with async-history enabled, history
     * events that were enqueued but not yet flushed at delete time are not removed by
     * this cascade — they will fail and land in the dead-letter table on next flush.
     * Acceptable for dev-only one-shot reset where no live instances exist.
     */
    private void resetExampleDeployments(String deploymentName) {
        List<Deployment> existing = repositoryService.createDeploymentQuery()
            .deploymentName(deploymentName)
            .deploymentTenantId(exampleTenantId)
            .list();
        if (existing.isEmpty()) {
            return;
        }
        log.warn("Reset mode: deleting {} prior deployment(s) for '{}'", existing.size(), deploymentName);
        for (Deployment d : existing) {
            repositoryService.deleteDeployment(d.getId(), true);
        }
    }
}
