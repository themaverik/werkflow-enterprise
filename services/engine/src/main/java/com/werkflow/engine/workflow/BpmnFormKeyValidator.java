package com.werkflow.engine.workflow;

import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.service.FormSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
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
 * Static formKey values are looked up via FormSchemaService.loadFormSchema().
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
@RequiredArgsConstructor
@Slf4j
public class BpmnFormKeyValidator {

    private final RepositoryService repositoryService;
    private final FormSchemaService formSchemaService;

    @EventListener(ApplicationReadyEvent.class)
    public void validateDeployedBpmns() {
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
            if (!new ClassPathResource("processes/" + filename).exists()) {
                log.warn("BpmnFormKeyValidator: skipping '{}' (key: {}) — resource '{}' not on classpath; " +
                    "this is a stale definition with active instances from a previous deployment",
                    definition.getName(), definition.getKey(), resourceName);
                continue;
            }

            BpmnModel model = repositoryService.getBpmnModel(definition.getId());
            model.getProcesses().forEach(process ->
                process.findFlowElementsOfType(UserTask.class).forEach(userTask -> {
                    String formKey = userTask.getFormKey();
                    if (formKey == null || formKey.isBlank()) {
                        return;
                    }
                    String trimmed = formKey.trim();
                    if (trimmed.startsWith("${")) {
                        return;
                    }
                    try {
                        formSchemaService.loadFormSchema(trimmed);
                    } catch (FormNotFoundException ex) {
                        violations.add(String.format(
                            "  [%s] task '%s' (%s): formKey '%s' has no active row in form_schemas",
                            definition.getKey(), userTask.getName(),
                            userTask.getId(), trimmed
                        ));
                    }
                })
            );
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
}
