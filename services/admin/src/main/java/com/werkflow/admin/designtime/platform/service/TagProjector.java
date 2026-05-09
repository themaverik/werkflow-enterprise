package com.werkflow.admin.designtime.platform.service;

import com.werkflow.admin.designtime.platform.dto.TagEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Aggregates distinct tags from artifact tables in the engine database for autocomplete.
 *
 * Note: artifact tables (process_draft, form_schemas) live in the engine schema.
 * This service queries them via a cross-schema JDBC connection to the engine's DB.
 * In v1, the engine and admin schemas share the same Postgres instance, so cross-schema
 * queries are safe. If schemas are separated later, replace with an engine API call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagProjector {

    private final JdbcTemplate jdbcTemplate;

    private static final int DEFAULT_LIMIT = 50;

    /**
     * Returns top tags by usage count across all tenant artifacts.
     * Prefix filter is optional (used for typeahead).
     */
    public List<TagEntry> listTags(String tenantId, String prefix, int limit) {
        try {
            boolean hasPrefix = prefix != null && !prefix.isBlank();
            int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;

            // Cross-schema query: engine uses the 'flowable' schema (see SPRING_DATASOURCE_SCHEMA).
            // Both tables received tags TEXT[] column in engine Flyway V5__artifact_metadata.sql.
            // If schemas are separated in future, replace with an engine API call.
            String sql = """
                    SELECT tag, count(*) AS usage_count
                    FROM (
                        SELECT unnest(tags) AS tag FROM flowable.process_draft
                        UNION ALL
                        SELECT unnest(tags) AS tag FROM flowable.form_schemas
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
                        prefixPattern, effectiveLimit);
            }
            return jdbcTemplate.query(sql,
                    (rs, row) -> new TagEntry(rs.getString("tag"), rs.getLong("usage_count")),
                    effectiveLimit);
        } catch (Exception e) {
            log.warn("TagProjector: could not aggregate tags — {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
