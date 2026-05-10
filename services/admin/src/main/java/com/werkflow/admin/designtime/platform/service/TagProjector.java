package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.designtime.platform.dto.TagEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Aggregates distinct tags from all taggable artifact tables for autocomplete.
 *
 * <p>Tag vocabulary is unified across ADR-010 artifacts (process_draft, form_schemas,
 * dmn_definition) and connector definitions (connector_definition_v2) per the PSS spec:
 * "Tags are unified across all taggable entities — connector definitions AND ADR-010
 * artifacts share one tag vocabulary per tenant."
 *
 * <p>Cross-schema queries are valid because the engine schema (flowable.*) and the
 * admin schema (admin_service.*) share the same Postgres instance in v1. If schemas
 * are separated in a future milestone, replace the JDBC query with engine/admin API calls.
 *
 * <p>Connector tags are stored inside {@code definition_json} as a JSON array at
 * {@code metadata.tags}. They are extracted with {@code jsonb_array_elements_text}.
 * Rows where {@code metadata->'tags'} is absent or not an array are safely skipped
 * by the {@code jsonb_typeof} guard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagProjector {

    private final JdbcTemplate jdbcTemplate;

    private static final int DEFAULT_LIMIT = 50;

    /**
     * Returns the top tags by usage count across all taggable tenant artifacts:
     * BPMN process drafts, form schemas, DMN definitions, and connector definitions.
     * An optional prefix enables typeahead filtering.
     *
     * @param tenantId      the tenant scope (passed to connector_definition_v2 filter)
     * @param prefix        optional typeahead prefix (case-insensitive)
     * @param limit         maximum number of tags to return; falls back to {@value DEFAULT_LIMIT}
     * @return deduplicated tags ordered by descending usage count
     */
    public List<TagEntry> listTags(String tenantId, String prefix, int limit) {
        try {
            boolean hasPrefix = prefix != null && !prefix.isBlank();
            int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;

            // Unified tag query across four artifact sources:
            //   1. flowable.process_draft          — tags TEXT[] (engine V5)
            //   2. flowable.form_schemas            — tags TEXT[] (engine V5)
            //   3. flowable.dmn_definition          — tags TEXT[] (engine V9)
            //   4. admin_service.connector_definition_v2 — tags inside definition_json JSONB
            //
            // Connector tenant scoping: connector_definition_v2.tenant_id = tenantId.
            // ADR-010 artifact tables have no tenant_id column in v1 (global scope per engine);
            // they are included unfiltered to match current TagProjector behaviour.
            String sql = """
                    SELECT tag, count(*) AS usage_count
                    FROM (
                        SELECT unnest(tags) AS tag
                          FROM flowable.process_draft
                        UNION ALL
                        SELECT unnest(tags) AS tag
                          FROM flowable.form_schemas
                        UNION ALL
                        SELECT unnest(tags) AS tag
                          FROM flowable.dmn_definition
                        UNION ALL
                        SELECT jsonb_array_elements_text(definition_json->'metadata'->'tags') AS tag
                          FROM admin_service.connector_definition_v2
                         WHERE tenant_id = ?
                           AND jsonb_typeof(definition_json->'metadata'->'tags') = 'array'
                    ) t
                    WHERE tag IS NOT NULL AND tag <> ''
                    """ + (hasPrefix ? "AND lower(tag) LIKE lower(?)\n" : "") + """
                    GROUP BY tag
                    ORDER BY usage_count DESC, tag ASC
                    LIMIT ?
                    """;

            if (hasPrefix) {
                String prefixPattern = prefix.trim() + "%";
                return jdbcTemplate.query(sql,
                        (rs, row) -> new TagEntry(rs.getString("tag"), rs.getLong("usage_count")),
                        tenantId, prefixPattern, effectiveLimit);
            }
            return jdbcTemplate.query(sql,
                    (rs, row) -> new TagEntry(rs.getString("tag"), rs.getLong("usage_count")),
                    tenantId, effectiveLimit);
        } catch (Exception e) {
            log.warn("TagProjector: could not aggregate tags — {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
