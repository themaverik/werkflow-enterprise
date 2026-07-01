package com.werkflow.engine.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts the distinct static candidateGroup literals that are referenced by the
 * <em>latest</em> deployed process definitions for a given tenant.
 *
 * <p>Used by the config API ({@code GET /api/v1/config/bpmn-candidate-groups}) to surface
 * Tier-2 candidate groups that are actually assignment-effective in deployed BPMNs,
 * so the admin Role Mappings UI can list them even before a mapping row has been saved
 * (solving the chicken-and-egg problem on a fresh tenant).
 *
 * <p>Filtering rules applied to every raw candidateGroup string:
 * <ul>
 *   <li>Split on commas — Flowable usually splits during parse, but this is defensive.</li>
 *   <li>Blank or null entries are discarded.</li>
 *   <li>EL expressions ({@code ${...}}, {@code #{...}}) are discarded — only static literals
 *       are returned.</li>
 *   <li>Tier-1 system groups (SUPER_ADMIN, ADMIN, WORKFLOW_DESIGNER) are discarded — they
 *       are already surfaced as Tier-1 entries by the aggregator.</li>
 * </ul>
 *
 * <p>Results are returned distinct and sorted ascending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BpmnCandidateGroupExtractor {

    /** Tier-1 system groups that must be excluded from Tier-2 results. */
    static final Set<String> TIER1_SYSTEM_GROUPS = Set.of(
            FlowableGroups.SUPER_ADMIN,
            FlowableGroups.ADMIN,
            FlowableGroups.WORKFLOW_DESIGNER
    );

    private final RepositoryService repositoryService;

    /**
     * Returns a sorted, distinct list of static candidateGroup literals across all latest
     * process definitions deployed for {@code tenantCode}.
     *
     * @param tenantCode the tenant to scope the query to (e.g. {@code "default"})
     * @return immutable sorted list; empty when no definitions are deployed for the tenant
     */
    public List<String> extractStaticGroups(String tenantCode) {
        List<ProcessDefinition> definitions = repositoryService
                .createProcessDefinitionQuery()
                .latestVersion()
                .processDefinitionTenantId(tenantCode)
                .list();

        if (definitions.isEmpty()) {
            log.debug("BpmnCandidateGroupExtractor: no deployed definitions for tenant '{}'", tenantCode);
            return List.of();
        }

        List<String> rawEntries = definitions.stream()
                .flatMap(def -> {
                    BpmnModel model = repositoryService.getBpmnModel(def.getId());
                    return model.getProcesses().stream()
                            .flatMap(process -> process.findFlowElementsOfType(UserTask.class).stream())
                            .flatMap(task -> {
                                List<String> groups = task.getCandidateGroups();
                                return (groups == null) ? java.util.stream.Stream.empty() : groups.stream();
                            });
                })
                .collect(Collectors.toList());

        List<String> result = filterRawGroups(rawEntries);
        log.debug("BpmnCandidateGroupExtractor: found {} static candidate group(s) for tenant '{}'",
                result.size(), tenantCode);
        return result;
    }

    /**
     * Filters and normalises a flat list of raw candidateGroup strings (as returned by
     * {@link UserTask#getCandidateGroups()}) into a sorted, distinct list of static literals.
     *
     * <p>Package-private and static so it can be exercised in isolation by unit tests without
     * needing a live {@link RepositoryService}.
     *
     * @param rawEntries the raw candidateGroup strings, may contain comma-separated values
     * @return immutable sorted list of static group literals
     */
    static List<String> filterRawGroups(List<String> rawEntries) {
        return rawEntries.stream()
                .filter(Objects::nonNull)
                .flatMap(entry -> Arrays.stream(entry.split(",")))
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .filter(g -> !g.startsWith("${") && !g.startsWith("#{"))
                .filter(g -> !TIER1_SYSTEM_GROUPS.contains(g))
                .distinct()
                .sorted()
                .collect(Collectors.toUnmodifiableList());
    }
}
