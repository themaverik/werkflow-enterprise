package com.werkflow.engine.workflow;

import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.service.FormSchemaService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that every UserTask formKey in deployed BPMN process definitions
 * resolves to an active row in form_schemas at startup.
 *
 * Scans all deployed process definitions after the application is ready.
 * Static formKey values are looked up via FormSchemaService.loadFormSchemaByRef().
 * Dynamic EL expressions (${...}) are skipped — they are resolved at runtime.
 * UserTasks with no formKey are skipped — formKey is optional in BPMN.
 *
 * Catches typo'd formKey or unprovisioned form references in deployed BPMNs,
 * preventing silent task-completion failures at runtime.
 *
 * Throws IllegalStateException on startup if any active classpath BPMN
 * references a formKey that does not resolve, preventing silent
 * task-completion failures at runtime when users open the task page and
 * see an empty form.
 *
 * Implements M4.11 P3 User-Task audit decision D5 (User-Task.md §7.2 F1).
 */
@Component
@Slf4j
public class BpmnFormKeyValidator {

    private final RepositoryService repositoryService;
    private final FormSchemaService formSchemaService;
    private final boolean validateFormKeys;

    public BpmnFormKeyValidator(RepositoryService repositoryService,
                                FormSchemaService formSchemaService,
                                @Value("${werkflow.startup.validate-form-keys:true}") boolean validateFormKeys) {
        this.repositoryService = repositoryService;
        this.formSchemaService = formSchemaService;
        this.validateFormKeys = validateFormKeys;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateDeployedBpmns() {
        if (!validateFormKeys) {
            log.info("BpmnFormKeyValidator: startup form-key validation disabled " +
                "(werkflow.startup.validate-form-keys=false)");
            return;
        }
        List<ProcessDefinition> definitions = repositoryService
            .createProcessDefinitionQuery()
            .latestVersion()
            .list();

        if (definitions.isEmpty()) {
            log.info("BpmnFormKeyValidator: no deployed process definitions found");
            return;
        }

        List<String> violations = new ArrayList<>();

        for (ProcessDefinition definition : definitions) {
            String resourceName = definition.getResourceName();
            String filename = resourceName.contains("/")
                ? resourceName.substring(resourceName.lastIndexOf('/') + 1)
                : resourceName;
            if (!new ClassPathResource("examples/tenants/default/bpmn/" + filename).exists()) {
                log.warn("BpmnFormKeyValidator: skipping '{}' (key: {}) — resource '{}' not in curated " +
                    "example set on classpath; user-authored BPMNs are validated at deploy time, not startup",
                    definition.getName(), definition.getKey(), resourceName);
                continue;
            }

            String tenantId = tenantOf(definition);
            BpmnModel model = repositoryService.getBpmnModel(definition.getId());
            model.getProcesses().forEach(process -> {
                process.findFlowElementsOfType(UserTask.class).forEach(userTask ->
                    checkFormKey(definition.getKey(), "task", userTask.getId(),
                        userTask.getName(), userTask.getFormKey(), tenantId, violations));
                // Start-event forms are pinned at bundle deploy too (ADR-026 F1), so validate them.
                process.findFlowElementsOfType(StartEvent.class).forEach(startEvent ->
                    checkFormKey(definition.getKey(), "start event", startEvent.getId(),
                        startEvent.getName(), startEvent.getFormKey(), tenantId, violations));
            });
        }

        if (violations.isEmpty()) {
            log.info("BpmnFormKeyValidator: all {} process definition(s) passed formKey validation",
                definitions.size());
        } else {
            throw new IllegalStateException(
                "BpmnFormKeyValidator: " + violations.size() + " missing formKey reference(s) found " +
                "in deployed BPMNs — these UserTasks will render an empty form at runtime and silently " +
                "block task completion. Provision the form in form_schemas or fix the BPMN before starting:\n" +
                String.join("\n", violations)
            );
        }
    }

    /**
     * Records a violation if a static formKey does not resolve for the given tenant. Skips blank
     * keys (optional) and EL expressions ({@code ${...}}, resolved at runtime). Honours an ADR-026
     * {@code formKey@version} pin via {@link FormSchemaService#loadFormSchemaByRef(String, String)}.
     */
    private void checkFormKey(String processKey, String elementKind, String elementId,
                             String elementName, String formKey, String tenantId,
                             List<String> violations) {
        if (formKey == null || formKey.isBlank()) {
            return;
        }
        String trimmed = formKey.trim();
        if (trimmed.startsWith("${")) {
            return;
        }
        try {
            formSchemaService.loadFormSchemaByRef(trimmed, tenantId);
        } catch (FormNotFoundException ex) {
            violations.add(String.format(
                "  [%s] %s '%s' (%s): formKey '%s' has no active row in form_schemas (tenant='%s')",
                processKey, elementKind, elementName, elementId, trimmed, tenantId));
        }
    }

    /**
     * Returns the tenant for a ProcessDefinition, normalised to "default" when null or blank.
     */
    private static String tenantOf(ProcessDefinition definition) {
        String tid = definition.getTenantId();
        return (tid == null || tid.isBlank()) ? "default" : tid;
    }
}
