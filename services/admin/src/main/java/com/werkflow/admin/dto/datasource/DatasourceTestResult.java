package com.werkflow.admin.dto.datasource;

/**
 * Result of a live connection test against a registered datasource.
 *
 * @param ok        true when a connection was established and SELECT 1 succeeded
 * @param message   success detail (e.g. DB version) or failure reason
 * @param latencyMs round-trip time for the test query in milliseconds
 */
public record DatasourceTestResult(boolean ok, String message, long latencyMs) {}
