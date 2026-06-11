package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.dto.TaskFormResponse;
import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.workflow.BpmnIndicatorScanner;
import com.werkflow.engine.workflow.BpmnIndicatorScanner.Indicators;
import com.werkflow.engine.workflow.ProcessIndicator;
import com.werkflow.engine.workflow.ProcessIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing BPMN process definitions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionService {

    private static final Set<String> JUEL_KEYWORDS;
    static {
        JUEL_KEYWORDS = new LinkedHashSet<>();
        JUEL_KEYWORDS.add("T(");
        JUEL_KEYWORDS.add("getClass");
        JUEL_KEYWORDS.add("forName");
        JUEL_KEYWORDS.add("Runtime");
        JUEL_KEYWORDS.add("exec");
        JUEL_KEYWORDS.add("ProcessBuilder");
        JUEL_KEYWORDS.add("getBean");
    }
    private static final int MAX_EXPRESSION_LENGTH = 2000;

    private final RepositoryService repositoryService;
    private final FormSchemaService formSchemaService;
    private final BpmnIndicatorScanner indicatorScanner;
    private final ProcessIndicatorRepository indicatorRepository;

    /**
     * Deploy a new process definition from BPMN XML string.
     */
    @Transactional
    public ProcessDefinitionResponse deployProcessDefinition(String bpmnXml, String resourceName) {
        return deployProcessDefinition(bpmnXml, resourceName, null);
    }

    /**
     * Deploy a process definition, optionally linking it to a parent deployment for bundle grouping (ADR-009).
     * When {@code parentDeploymentId} is set, Flowable resolves resources from the parent deployment
     * first, enabling incremental updates within a bundle without redundant re-deployment of unchanged files.
     */
    /**
     * Deploy without a tenant (standalone path). The process is deployed with no tenant id,
     * unlike the bundle path which is tenant-scoped (ADR-026). Same-deployment binding requires
     * matching tenant ids, so standalone-deployed processes are not bundle-pinned.
     */
    @Transactional
    public ProcessDefinitionResponse deployProcessDefinition(String bpmnXml, String resourceName,
                                                             String parentDeploymentId) {
        return deployProcessDefinition(bpmnXml, resourceName, parentDeploymentId, null);
    }

    /**
     * Deploy a process definition into a tenant, optionally linking it to a bundle's
     * {@code parentDeploymentId} (ADR-026 Phase 1). Setting {@code tenantId} is required
     * for same-deployment binding to resolve the bundle's DMNs in a multi-tenant engine.
     */
    @Transactional
    public ProcessDefinitionResponse deployProcessDefinition(String bpmnXml, String resourceName,
                                                             String parentDeploymentId, String tenantId) {
        validateBpmnExpressions(bpmnXml);
        log.info("Deploying process definition: {} (parentDeploymentId={}, tenantId={})",
            resourceName, parentDeploymentId, tenantId);

        try (InputStream inputStream = new java.io.ByteArrayInputStream(
                bpmnXml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {

            var builder = repositoryService.createDeployment()
                .name(resourceName)
                .addInputStream(resourceName, inputStream);

            if (parentDeploymentId != null && !parentDeploymentId.isBlank()) {
                builder = builder.parentDeploymentId(parentDeploymentId);
            }
            if (tenantId != null && !tenantId.isBlank()) {
                builder = builder.tenantId(tenantId);
            }

            Deployment deployment = builder.deploy();

            log.info("Process definition deployed. Deployment ID: {}", deployment.getId());

            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

            persistIndicators(bpmnXml, processDefinition.getId());

            return mapToResponse(processDefinition);

        } catch (IOException e) {
            log.error("Error deploying process definition", e);
            throw new RuntimeException("Failed to deploy process definition: " + e.getMessage(), e);
        }
    }

    /**
     * Deploy an example process definition with Flowable duplicate filtering enabled.
     * If the BPMN content is identical to the last deployment under the same name,
     * Flowable will skip creating a new version — stopping version numbers from climbing
     * on every restart (e.g. event-ticket reaching v104+).
     *
     * <p>IMPORTANT: this path must NOT be used for bundle/rollback deployments (ADR-026).
     * Those deployments intentionally create a new version on each deploy. This method is
     * exclusively for {@link com.werkflow.engine.init.ProcessExampleDeployer}.
     *
     * @param bpmnXml      the BPMN XML content
     * @param resourceName the filename used as both the deployment name and the resource
     *                     name — must be stable across restarts for deduplication to work
     * @param tenantId     the tenant under which the example is deployed; must match the
     *                     tenant used by portal queries (processDefinitionTenantId filter)
     */
    @Transactional
    public ProcessDefinitionResponse deployExampleProcessDefinition(String bpmnXml, String resourceName,
                                                                    String tenantId) {
        validateBpmnExpressions(bpmnXml);
        log.info("Deploying example process definition with duplicate filtering: {} (tenantId={})",
            resourceName, tenantId);

        try (InputStream inputStream = new java.io.ByteArrayInputStream(
                bpmnXml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {

            Deployment deployment = repositoryService.createDeployment()
                .name(resourceName)
                .addInputStream(resourceName, inputStream)
                .enableDuplicateFiltering()
                .tenantId(tenantId)
                .deploy();

            log.info("Example process definition deployed/reused. Deployment ID: {}", deployment.getId());

            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .processDefinitionTenantId(tenantId)
                .singleResult();

            if (processDefinition == null) {
                // Duplicate-filter returned a pre-existing deployment under a different tenant.
                // Fall back to latest version query for this resource name + tenant.
                processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionResourceName(resourceName)
                    .processDefinitionTenantId(tenantId)
                    .latestVersion()
                    .singleResult();
            }
            if (processDefinition == null) {
                log.warn("deployExampleProcessDefinition: no process definition found for '{}' " +
                         "under tenant '{}' — indicators not persisted", resourceName, tenantId);
                return mapToResponse(repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult());
            }

            persistIndicators(bpmnXml, processDefinition.getId());

            return mapToResponse(processDefinition);

        } catch (IOException e) {
            log.error("Error deploying example process definition", e);
            throw new RuntimeException("Failed to deploy example process definition: " + e.getMessage(), e);
        }
    }

    /**
     * The BPMN resource of a bundle's process deployment: its original Flowable resource name
     * plus its XML. Carrying the resource name lets a rollback redeploy under the same name the
     * bundle was originally deployed with (no naming asymmetry vs {@code deployBundle}).
     */
    public record BundleBpmn(String resourceName, String xml) {}

    /**
     * Reads the BPMN resource of the process deployed under a bundle's {@code parentDeploymentId}
     * (ADR-026 Phase 3 rollback). Flowable retains prior deployments, so a bundle's original
     * artifacts remain readable for redeploy. Returns the first process deployment's BPMN resource.
     *
     * @throws IllegalStateException if no process deployment exists for the parentDeploymentId
     */
    public BundleBpmn getBundleBpmnByParentDeployment(String parentDeploymentId, String tenantId) {
        List<Deployment> deployments = repositoryService.createDeploymentQuery()
            .parentDeploymentId(parentDeploymentId)
            .deploymentTenantId(tenantId)
            .list();

        for (Deployment deployment : deployments) {
            String resourceName = repositoryService.getDeploymentResourceNames(deployment.getId()).stream()
                .filter(n -> n.endsWith(".bpmn20.xml") || n.endsWith(".bpmn"))
                .findFirst()
                .orElse(null);
            if (resourceName == null) {
                continue;
            }
            try (InputStream in = repositoryService.getResourceAsStream(deployment.getId(), resourceName)) {
                String xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return new BundleBpmn(resourceName, xml);
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to read BPMN XML for bundle " + parentDeploymentId, e);
            }
        }
        throw new IllegalStateException("No process deployment found for bundle " + parentDeploymentId);
    }

    /**
     * Get all process definitions (latest versions).
     * Indicator flags are loaded in a single batch query to avoid N+1 per row.
     */
    public List<ProcessDefinitionResponse> getAllProcessDefinitions(String tenantId) {
        log.debug("Fetching all process definitions");

        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
            .processDefinitionTenantId(tenantId)
            .latestVersion()
            .list();

        List<String> ids = definitions.stream()
            .map(ProcessDefinition::getId)
            .collect(Collectors.toList());

        Map<String, ProcessIndicator> indicatorMap = indicatorRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(ProcessIndicator::getProcessDefinitionId, Function.identity()));

        return definitions.stream()
            .map(pd -> mapToResponse(pd, indicatorMap.get(pd.getId())))
            .collect(Collectors.toList());
    }

    /**
     * Get process definition by ID
     */
    public ProcessDefinitionResponse getProcessDefinitionById(String id) {
        log.debug("Fetching process definition by ID: {}", id);

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(id)
            .singleResult();

        if (processDefinition == null) {
            throw new RuntimeException("Process definition not found with ID: " + id);
        }

        return mapToResponse(processDefinition);
    }

    /**
     * Get process definition by key (latest version)
     */
    public ProcessDefinitionResponse getProcessDefinitionByKey(String key, String tenantId) {
        log.debug("Fetching process definition by key: {}", key);

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key)
            .processDefinitionTenantId(tenantId)
            .latestVersion()
            .singleResult();

        if (processDefinition == null) {
            throw new RuntimeException("Process definition not found with key: " + key);
        }

        return mapToResponse(processDefinition);
    }

    /**
     * Get all versions of a process definition by key
     */
    public List<ProcessDefinitionResponse> getProcessDefinitionVersions(String key, String tenantId) {
        log.debug("Fetching all versions of process definition: {}", key);

        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key)
            .processDefinitionTenantId(tenantId)
            .orderByProcessDefinitionVersion()
            .desc()
            .list();

        return definitions.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Delete process definition by deployment ID
     */
    @Transactional
    public void deleteProcessDefinition(String deploymentId, boolean cascade) {
        log.info("Deleting process definition deployment: {} (cascade: {})", deploymentId, cascade);

        repositoryService.deleteDeployment(deploymentId, cascade);

        log.info("Process definition deployment deleted successfully");
    }

    /**
     * Suspend process definition
     */
    @Transactional
    public void suspendProcessDefinition(String processDefinitionId) {
        log.info("Suspending process definition: {}", processDefinitionId);

        repositoryService.suspendProcessDefinitionById(processDefinitionId);

        log.info("Process definition suspended successfully");
    }

    /**
     * Activate process definition
     */
    @Transactional
    public void activateProcessDefinition(String processDefinitionId) {
        log.info("Activating process definition: {}", processDefinitionId);

        repositoryService.activateProcessDefinitionById(processDefinitionId);

        log.info("Process definition activated successfully");
    }

    /**
     * Get BPMN XML of a process definition
     */
    public String getProcessDefinitionXml(String processDefinitionId) {
        log.debug("Fetching BPMN XML for process definition: {}", processDefinitionId);

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId(processDefinitionId)
            .singleResult();

        if (processDefinition == null) {
            throw new RuntimeException("Process definition not found with ID: " + processDefinitionId);
        }

        try {
            // Get the BPMN XML using the resource name from process definition
            InputStream resourceStream = repositoryService.getResourceAsStream(
                processDefinition.getDeploymentId(),
                processDefinition.getResourceName()
            );

            if (resourceStream == null) {
                throw new RuntimeException("Resource not found for process definition: " + processDefinitionId);
            }

            String xml = new String(resourceStream.readAllBytes());
            log.debug("Successfully retrieved BPMN XML for process definition: {}", processDefinitionId);
            return xml;
        } catch (IOException e) {
            log.error("Error retrieving BPMN XML for process definition: {}", processDefinitionId, e);
            throw new RuntimeException("Failed to retrieve BPMN XML: " + e.getMessage(), e);
        }
    }

    /**
     * Get BPMN XML by process key (latest deployed version).
     */
    public String getProcessDefinitionXmlByKey(String key) {
        log.debug("Fetching BPMN XML for process key: {}", key);

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key)
            .latestVersion()
            .singleResult();

        if (processDefinition == null) {
            throw new RuntimeException("Process definition not found with key: " + key);
        }

        try {
            InputStream resourceStream = repositoryService.getResourceAsStream(
                processDefinition.getDeploymentId(),
                processDefinition.getResourceName()
            );

            if (resourceStream == null) {
                throw new RuntimeException("Resource not found for process key: " + key);
            }

            return new String(resourceStream.readAllBytes());
        } catch (IOException e) {
            log.error("Error retrieving BPMN XML for process key: {}", key, e);
            throw new RuntimeException("Failed to retrieve BPMN XML: " + e.getMessage(), e);
        }
    }

    /**
     * Get start form schema for a process definition.
     * Accepts either a full Flowable process definition ID (key:version:hash)
     * or a plain process key — in which case the latest deployed version is used.
     */
    public TaskFormResponse getStartForm(String processDefinitionId) {
        log.info("Getting start form for process definition: {}", processDefinitionId);

        // If the caller passed a plain key (no colons), resolve it to the latest version ID
        ProcessDefinition processDefinition;
        if (!processDefinitionId.contains(":")) {
            processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionId)
                .latestVersion()
                .singleResult();
        } else {
            processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        }

        if (processDefinition == null) {
            throw new RuntimeException("Process definition not found with ID: " + processDefinitionId);
        }

        // Always use the resolved Flowable ID (not the incoming param) for BPMN model lookup
        String resolvedId = processDefinition.getId();
        String formKey = extractStartFormKey(resolvedId);

        if (formKey == null || formKey.isEmpty()) {
            throw new FormNotFoundException("start-form for process: " + processDefinitionId);
        }

        var formSchema = formSchemaService.loadFormSchemaByRef(formKey);

        return TaskFormResponse.builder()
            .formKey(formSchema.getFormKey())
            .version(formSchema.getVersion())
            .schema(formSchema.getSchemaJson())
            .description(formSchema.getDescription())
            .formType(formSchema.getFormType().name())
            .processInstanceId(null)
            .taskId(null)
            .build();
    }

    private void validateBpmnExpressions(String bpmnXml) {
        for (String keyword : JUEL_KEYWORDS) {
            if (bpmnXml.contains(keyword)) {
                throw new IllegalArgumentException(
                    "BPMN contains a disallowed expression keyword: " + keyword);
            }
        }
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("\\$\\{([^}]{" + (MAX_EXPRESSION_LENGTH + 1) + ",})")
                .matcher(bpmnXml);
        if (m.find()) {
            throw new IllegalArgumentException("BPMN expression exceeds maximum length of "
                + MAX_EXPRESSION_LENGTH + " characters.");
        }
    }

    /**
     * Extract start event formKey from BPMN model
     */
    private String extractStartFormKey(String processDefinitionId) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
            return null;
        }

        Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
        for (FlowElement element : flowElements) {
            if (element instanceof StartEvent startEvent) {
                String formKey = startEvent.getFormKey();
                if (formKey != null && !formKey.isEmpty()) {
                    return formKey;
                }
            }
        }
        return null;
    }

    /**
     * Map ProcessDefinition entity to response DTO (single-row path; does a per-row indicator lookup).
     * Used by all single-result GET endpoints. For the list endpoint use the overload that accepts
     * a pre-loaded {@link ProcessIndicator} to avoid N+1 queries.
     */
    private ProcessDefinitionResponse mapToResponse(ProcessDefinition pd) {
        ProcessIndicator ind = indicatorRepository.findById(pd.getId()).orElse(null);
        return mapToResponse(pd, ind);
    }

    /**
     * Map ProcessDefinition entity to response DTO using a pre-loaded indicator (may be {@code null}).
     * A null indicator is treated as both flags false (mirrors the LEFT JOIN / missing-row semantic).
     */
    private ProcessDefinitionResponse mapToResponse(ProcessDefinition pd, ProcessIndicator ind) {
        String startFormKey = null;
        if (pd.hasStartFormKey()) {
            startFormKey = extractStartFormKey(pd.getId());
        }

        return ProcessDefinitionResponse.builder()
            .id(pd.getId())
            .key(pd.getKey())
            .name(pd.getName())
            .description(pd.getDescription())
            .version(pd.getVersion())
            .category(pd.getCategory())
            .deploymentId(pd.getDeploymentId())
            .resourceName(pd.getResourceName())
            .tenantId(pd.getTenantId())
            .suspended(pd.isSuspended())
            .hasStartFormKey(pd.hasStartFormKey())
            .hasGraphicalNotation(pd.hasGraphicalNotation())
            .startFormKey(startFormKey)
            .hasDmn(ind != null && ind.isHasDmn())
            .hasConnector(ind != null && ind.isHasConnector())
            .hasNotification(ind != null && ind.isHasNotification())
            .build();
    }

    /**
     * Scans {@code bpmnXml} for indicators and upserts the row for {@code processDefinitionId}.
     */
    private void persistIndicators(String bpmnXml, String processDefinitionId) {
        Indicators ind = indicatorScanner.scan(bpmnXml);
        indicatorRepository.save(ProcessIndicator.builder()
            .processDefinitionId(processDefinitionId)
            .hasDmn(ind.hasDmn())
            .hasConnector(ind.hasConnector())
            .hasNotification(ind.hasNotification())
            .build());
    }
}
