package com.werkflow.engine.service;

import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.dto.TaskFormResponse;
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
import java.util.Set;
import java.util.LinkedHashSet;
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

            return mapToResponse(processDefinition);

        } catch (IOException e) {
            log.error("Error deploying process definition", e);
            throw new RuntimeException("Failed to deploy process definition: " + e.getMessage(), e);
        }
    }

    /**
     * Get all process definitions (latest versions)
     */
    public List<ProcessDefinitionResponse> getAllProcessDefinitions() {
        log.debug("Fetching all process definitions");

        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
            .latestVersion()
            .list();

        return definitions.stream()
            .map(this::mapToResponse)
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
    public ProcessDefinitionResponse getProcessDefinitionByKey(String key) {
        log.debug("Fetching process definition by key: {}", key);

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key)
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
    public List<ProcessDefinitionResponse> getProcessDefinitionVersions(String key) {
        log.debug("Fetching all versions of process definition: {}", key);

        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(key)
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
            throw new RuntimeException("Process definition " + processDefinitionId + " has no start form");
        }

        var formSchema = formSchemaService.loadFormSchema(formKey);

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
     * Map ProcessDefinition entity to response DTO
     */
    private ProcessDefinitionResponse mapToResponse(ProcessDefinition pd) {
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
            .build();
    }
}
