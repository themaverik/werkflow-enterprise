package com.werkflow.engine.workflow;

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
 * Validates candidateGroups in deployed BPMN process definitions at startup.
 *
 * Scans all deployed process definitions after the application is ready and checks
 * that every static candidateGroup value matches one of the recognised structural patterns:
 *
 *   - Exact administrative roles: ADMIN, SUPER_ADMIN, WORKFLOW_DESIGNER
 *   - DOA colon format:      DOA:L* (e.g. DOA:L1, DOA:L3)
 *   - DOA underscore format: DOA_L* (e.g. DOA_L1) — backward compatible during Phase 2
 *   - Department groups:     DEPT:* (e.g. DEPT:IT, DEPT:FIN::DOA:L2)
 *
 * Dynamic EL expressions (${...}) are skipped — they are resolved at runtime.
 *
 * Only validates definitions whose BPMN resource still exists on the classpath.
 * Stale definitions (e.g. renamed or removed BPMNs still in the DB with active instances)
 * are skipped with a warning — they cannot receive new instances from this deployment.
 *
 * Throws IllegalStateException on startup if any active classpath BPMN references an
 * unrecognised candidateGroup, preventing silent routing failures in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BpmnGroupValidator {

    private final RepositoryService repositoryService;

    @EventListener(ApplicationReadyEvent.class)
    public void validateDeployedBpmns() {
        List<ProcessDefinition> definitions = repositoryService
            .createProcessDefinitionQuery()
            .latestVersion()
            .list();

        if (definitions.isEmpty()) {
            log.info("BpmnGroupValidator: no deployed process definitions found");
            return;
        }

        List<String> violations = new ArrayList<>();

        for (ProcessDefinition definition : definitions) {
            String resourceName = definition.getResourceName();
            // Resource names in the DB are bare filenames; BPMNs live under processes/ on the classpath.
            // Strip any stored path prefix and look in the processes/ directory.
            String filename = resourceName.contains("/")
                ? resourceName.substring(resourceName.lastIndexOf('/') + 1)
                : resourceName;
            if (!new ClassPathResource("processes/" + filename).exists()) {
                log.warn("BpmnGroupValidator: skipping '{}' (key: {}) — resource '{}' not on classpath; " +
                    "this is a stale definition with active instances from a previous deployment",
                    definition.getName(), definition.getKey(), resourceName);
                continue;
            }

            BpmnModel model = repositoryService.getBpmnModel(definition.getId());
            model.getProcesses().forEach(process ->
                process.findFlowElementsOfType(UserTask.class).forEach(userTask -> {
                    List<String> candidateGroups = userTask.getCandidateGroups();
                    if (candidateGroups == null || candidateGroups.isEmpty()) {
                        return;
                    }
                    candidateGroups.stream()
                        .map(String::trim)
                        .filter(group -> !group.isEmpty())
                        .filter(group -> !group.startsWith("${")) // skip EL expressions
                        .forEach(group -> {
                            if (!isValidGroup(group)) {
                                violations.add(String.format(
                                    "  [%s] task '%s' (%s): unknown candidateGroup '%s'",
                                    definition.getKey(), userTask.getName(),
                                    userTask.getId(), group
                                ));
                            }
                        });
                })
            );
        }

        if (violations.isEmpty()) {
            log.info("BpmnGroupValidator: all {} process definition(s) passed group validation",
                definitions.size());
        } else {
            throw new IllegalStateException(
                "BpmnGroupValidator: " + violations.size() + " unknown candidateGroup value(s) found in " +
                "deployed BPMNs — these will never match any user's resolved groups. " +
                "Fix the BPMN or ensure the group matches a recognised structural pattern " +
                "(ADMIN, SUPER_ADMIN, WORKFLOW_DESIGNER, DOA:L*, DOA_L*, DEPT:*) before starting:\n" +
                String.join("\n", violations)
            );
        }
    }

    /**
     * Returns true if the group identifier matches a recognised structural pattern.
     *
     * Accepted patterns:
     *   - Exact administrative constants:   ADMIN, SUPER_ADMIN, WORKFLOW_DESIGNER
     *   - DOA colon format (new):           DOA:L*
     *   - DOA underscore format (Phase 2):  DOA_L*
     *   - Department groups:                DEPT:*
     */
    private boolean isValidGroup(String group) {
        return group.equals(FlowableGroups.ADMIN)
            || group.equals(FlowableGroups.SUPER_ADMIN)
            || group.equals(FlowableGroups.WORKFLOW_DESIGNER)
            || group.startsWith("DOA:L")
            || group.startsWith("DOA_L")
            || group.startsWith("DEPT:");
    }
}
