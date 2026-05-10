package com.werkflow.engine.action.db;

import java.util.List;
import java.util.Map;

/**
 * Holds the accumulated rows from a keyset-paginated query together with the
 * cursor value for the next page (null when no more pages exist).
 *
 * @param rows       all rows fetched across all pages
 * @param nextCursor value to use as cursor for the next page; null means end-of-results
 */
public record PageResult(List<Map<String, Object>> rows, Object nextCursor) {}
