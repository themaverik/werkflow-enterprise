package com.werkflow.engine.action.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates pages from a keyset-paginated named query.
 *
 * <p>Keyset pagination is the only cursor strategy supported by the database
 * transport — offset-based pagination is intentionally absent because it degrades
 * to a full scan as the offset grows and produces inconsistent results on concurrent
 * inserts/deletes.</p>
 *
 * <p>The caller provides the initial cursor value (may be null for the first page)
 * and the column name whose last-row value advances the cursor on each page.
 * Iteration stops when a page returns fewer rows than {@code pageSize} or when
 * the total accumulated rows reach the hard cap from {@link NamedQueryExecutor#HARD_CAP_MAX_ROWS}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeysetPaginator {

    private final NamedQueryExecutor queryExecutor;

    /**
     * Fetches all rows via keyset iteration and returns them together with the
     * cursor value of the last row (or null if all rows were consumed).
     *
     * @param dataSource    resolved tenant datasource
     * @param sql           parameterized SQL; must accept a {@code :cursor} parameter
     *                      (null on first page, last row's cursor column value thereafter)
     * @param baseParams    static parameters bound from the BPMN operation input
     * @param cursorColumn  name of the column in the result set whose value acts as the cursor
     * @param initialCursor initial cursor value (null for the first page)
     * @param pageSize      rows per page; capped at NamedQueryExecutor.HARD_CAP_MAX_ROWS
     * @param timeoutSecs   per-page query timeout
     * @param readOnly      whether to enforce read-only connection mode
     * @return accumulated rows and the next cursor value (null = end of results)
     */
    public PageResult fetchAll(DataSource dataSource,
                               String sql,
                               Map<String, Object> baseParams,
                               String cursorColumn,
                               Object initialCursor,
                               int pageSize,
                               int timeoutSecs,
                               boolean readOnly) {
        int effectivePage = pageSize > 0 ? Math.min(pageSize, NamedQueryExecutor.HARD_CAP_MAX_ROWS) : 100;
        List<Map<String, Object>> accumulated = new ArrayList<>();
        Object cursor = initialCursor;
        int hardCap = NamedQueryExecutor.HARD_CAP_MAX_ROWS;

        while (true) {
            Map<String, Object> params = new HashMap<>(baseParams != null ? baseParams : Map.of());
            params.put("cursor", cursor);

            List<Map<String, Object>> page = queryExecutor.executePage(
                dataSource, sql, params, effectivePage, timeoutSecs, readOnly);

            accumulated.addAll(page);
            log.debug("KeysetPaginator: fetched {} rows (total {})", page.size(), accumulated.size());

            if (page.isEmpty() || page.size() < effectivePage || accumulated.size() >= hardCap) {
                // Last row's cursor value for the caller to resume later
                Object nextCursor = null;
                if (!accumulated.isEmpty() && page.size() == effectivePage) {
                    // There may be more pages, expose the cursor
                    nextCursor = accumulated.get(accumulated.size() - 1).get(cursorColumn);
                }
                // Trim to hard cap to honour the absolute limit
                List<Map<String, Object>> result = accumulated.size() > hardCap
                    ? accumulated.subList(0, hardCap)
                    : accumulated;
                return new PageResult(List.copyOf(result), nextCursor);
            }

            // Advance cursor from the last row of this page
            cursor = page.get(page.size() - 1).get(cursorColumn);
        }
    }
}
