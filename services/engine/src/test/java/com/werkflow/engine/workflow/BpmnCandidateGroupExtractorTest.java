package com.werkflow.engine.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BpmnCandidateGroupExtractor#filterRawGroups(List)}.
 *
 * Exercises the static filtering method in isolation — no Flowable runtime needed.
 */
class BpmnCandidateGroupExtractorTest {

    @Test
    @DisplayName("excludes EL dollar-brace expressions from results")
    void excludes_dollar_el_expressions() {
        List<String> raw = List.of("${assigneeGroup}", "MANAGER", "${dynamic}");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).containsExactly("MANAGER");
    }

    @Test
    @DisplayName("excludes EL hash-brace expressions from results")
    void excludes_hash_el_expressions() {
        List<String> raw = List.of("#{someBean.group}", "FINANCE_APPROVER");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).containsExactly("FINANCE_APPROVER");
    }

    @Test
    @DisplayName("splits comma-separated groups within a single entry")
    void splits_comma_separated_groups() {
        // Defensive: Flowable usually splits during parse, but handle the case where it doesn't.
        List<String> raw = List.of("MANAGER,FINANCE_APPROVER", "DOA_L1");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).containsExactly("DOA_L1", "FINANCE_APPROVER", "MANAGER");
    }

    @Test
    @DisplayName("filters out Tier-1 system groups: SUPER_ADMIN, ADMIN, WORKFLOW_DESIGNER")
    void filters_tier1_system_groups() {
        List<String> raw = List.of("SUPER_ADMIN", "ADMIN", "WORKFLOW_DESIGNER", "MANAGER", "DOA_L2");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result)
                .doesNotContain("SUPER_ADMIN", "ADMIN", "WORKFLOW_DESIGNER")
                .containsExactly("DOA_L2", "MANAGER");
    }

    @Test
    @DisplayName("deduplicates groups that appear across multiple user tasks")
    void deduplicates_repeated_groups() {
        List<String> raw = List.of("MANAGER", "DOA_L1", "MANAGER", "DOA_L1");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).containsExactly("DOA_L1", "MANAGER");
    }

    @Test
    @DisplayName("returns results sorted ascending")
    void results_are_sorted_ascending() {
        List<String> raw = List.of("MANAGER", "DOA_L1", "FINANCE_APPROVER");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).isSorted();
        assertThat(result).containsExactly("DOA_L1", "FINANCE_APPROVER", "MANAGER");
    }

    @Test
    @DisplayName("discards blank and whitespace-only entries")
    void discards_blank_entries() {
        List<String> raw = List.of("  ", "", "MANAGER", "  ");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).containsExactly("MANAGER");
    }

    @Test
    @DisplayName("returns empty list when all entries are EL expressions or system groups")
    void returns_empty_when_all_filtered() {
        List<String> raw = List.of("${group}", "ADMIN", "SUPER_ADMIN", "WORKFLOW_DESIGNER");

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("handles null entries within the raw list without throwing")
    void handles_null_entries_gracefully() {
        List<String> raw = new java.util.ArrayList<>();
        raw.add(null);
        raw.add("MANAGER");
        raw.add(null);

        List<String> result = BpmnCandidateGroupExtractor.filterRawGroups(raw);

        assertThat(result).containsExactly("MANAGER");
    }

    @Test
    @DisplayName("returns empty list for an empty input")
    void empty_input_returns_empty() {
        assertThat(BpmnCandidateGroupExtractor.filterRawGroups(List.of())).isEmpty();
    }
}
