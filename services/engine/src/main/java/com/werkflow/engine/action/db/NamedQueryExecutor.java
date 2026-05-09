package com.werkflow.engine.action.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Executes named, parameterized queries against a tenant datasource.
 *
 * <p>This class enforces the security invariants of the database transport:
 * read-only mode rejection of DML, hard row caps, and query timeouts.
 * All three limits are applied at the JDBC layer so they hold regardless
 * of ORM or template library behaviour above them.</p>
 */
@Slf4j
@Component
public class NamedQueryExecutor {

    /** Default max rows when the query spec omits rowLimit. */
    public static final int DEFAULT_MAX_ROWS = 1000;

    /** Absolute hard cap — no query may return more rows than this regardless of spec. */
    public static final int HARD_CAP_MAX_ROWS = 10_000;

    /** Default query timeout in seconds when the query spec omits queryTimeoutSeconds. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** DML/DDL keywords whose presence in a read-only query is a configuration error. */
    private static final Pattern DML_PATTERN = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE|DROP|ALTER|CREATE|GRANT|REVOKE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // -------------------------------------------------------------------------
    // Public execution API
    // -------------------------------------------------------------------------

    /**
     * Executes a named query and returns all matching rows.
     *
     * @param dataSource  resolved tenant datasource
     * @param sql         parameterized SQL using {@code :paramName} placeholders
     * @param params      bound parameters — null treated as empty
     * @param maxRows     row limit from query spec; 0 uses default; hard-capped at HARD_CAP_MAX_ROWS
     * @param timeoutSecs query timeout; 0 uses default
     * @param readOnly    when true, connection.setReadOnly(true) is applied and DML is rejected
     * @return rows as column-name-to-value maps
     * @throws IllegalArgumentException if readOnly=true and SQL contains DML keywords
     */
    public List<Map<String, Object>> executeList(DataSource dataSource,
                                                 String sql,
                                                 Map<String, Object> params,
                                                 int maxRows,
                                                 int timeoutSecs,
                                                 boolean readOnly) {
        if (readOnly) {
            rejectDml(sql);
        }
        int effectiveMaxRows = resolveMaxRows(maxRows);
        int effectiveTimeout = timeoutSecs > 0 ? timeoutSecs : DEFAULT_TIMEOUT_SECONDS;

        log.debug("NamedQueryExecutor.executeList maxRows={} timeout={}s readOnly={}", effectiveMaxRows, effectiveTimeout, readOnly);

        NamedParameterJdbcTemplate jdbc = buildTemplate(dataSource, readOnly, effectiveMaxRows, effectiveTimeout);
        return jdbc.queryForList(sql, buildParamSource(params));
    }

    /**
     * Executes a named query and returns exactly one row.
     *
     * @throws org.springframework.dao.EmptyResultDataAccessException      if no rows match
     * @throws org.springframework.dao.IncorrectResultSizeDataAccessException if more than one row matches
     */
    public Map<String, Object> executeSingle(DataSource dataSource,
                                             String sql,
                                             Map<String, Object> params,
                                             int timeoutSecs,
                                             boolean readOnly) {
        if (readOnly) {
            rejectDml(sql);
        }
        int effectiveTimeout = timeoutSecs > 0 ? timeoutSecs : DEFAULT_TIMEOUT_SECONDS;

        log.debug("NamedQueryExecutor.executeSingle timeout={}s readOnly={}", effectiveTimeout, readOnly);

        // Fetch at most 2 rows so Spring can detect the >1 case cheaply
        NamedParameterJdbcTemplate jdbc = buildTemplate(dataSource, readOnly, 2, effectiveTimeout);
        return jdbc.queryForMap(sql, buildParamSource(params));
    }

    // -------------------------------------------------------------------------
    // Package-accessible for KeysetPaginator
    // -------------------------------------------------------------------------

    /**
     * Executes a single page of a keyset-paginated query.
     * Accumulation and cursor tracking are managed by the caller ({@link KeysetPaginator}).
     */
    List<Map<String, Object>> executePage(DataSource dataSource,
                                          String sql,
                                          Map<String, Object> params,
                                          int pageSize,
                                          int timeoutSecs,
                                          boolean readOnly) {
        return executeList(dataSource, sql, params, pageSize, timeoutSecs, readOnly);
    }

    // -------------------------------------------------------------------------
    // DML guard — also used by ConnectorDefinitionValidator at registration time
    // -------------------------------------------------------------------------

    /**
     * Scans SQL for DML/DDL keywords using a word-boundary, case-insensitive regex.
     *
     * @throws IllegalArgumentException naming the offending keyword
     */
    public static void rejectDml(String sql) {
        if (sql == null || sql.isBlank()) return;
        var matcher = DML_PATTERN.matcher(sql);
        if (matcher.find()) {
            throw new IllegalArgumentException(
                "Query contains DML keyword '" + matcher.group().toUpperCase() +
                "' but connector is readOnly=true. " +
                "Set readOnly=false in the connector transport config if writes are intended.");
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private NamedParameterJdbcTemplate buildTemplate(DataSource dataSource,
                                                     boolean readOnly,
                                                     int maxRows,
                                                     int timeoutSecs) {
        DataSource wrapped = new LimitingDataSource(dataSource, readOnly, maxRows, timeoutSecs);
        return new NamedParameterJdbcTemplate(wrapped);
    }

    private static MapSqlParameterSource buildParamSource(Map<String, Object> params) {
        MapSqlParameterSource src = new MapSqlParameterSource();
        if (params != null) {
            params.forEach(src::addValue);
        }
        return src;
    }

    private static int resolveMaxRows(int requested) {
        if (requested <= 0) return DEFAULT_MAX_ROWS;
        return Math.min(requested, HARD_CAP_MAX_ROWS);
    }

    // -------------------------------------------------------------------------
    // DataSource decorator — enforces read-only and per-statement limits
    // -------------------------------------------------------------------------

    /**
     * Wraps a {@link DataSource} to apply read-only mode, max-rows, and timeout
     * limits on every connection/statement without touching the underlying pool
     * configuration.
     */
    private static final class LimitingDataSource extends DelegatingDataSource {

        private final boolean readOnly;
        private final int maxRows;
        private final int timeoutSecs;

        LimitingDataSource(DataSource delegate, boolean readOnly, int maxRows, int timeoutSecs) {
            super(delegate);
            this.readOnly = readOnly;
            this.maxRows = maxRows;
            this.timeoutSecs = timeoutSecs;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection conn = obtainTargetDataSource().getConnection();
            conn.setReadOnly(readOnly);
            return new LimitingConnection(conn, maxRows, timeoutSecs);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection conn = obtainTargetDataSource().getConnection(username, password);
            conn.setReadOnly(readOnly);
            return new LimitingConnection(conn, maxRows, timeoutSecs);
        }
    }

    /**
     * Connection decorator that applies max-row and timeout limits to every
     * created {@link Statement} and {@link PreparedStatement}.
     */
    private static final class LimitingConnection implements Connection {

        private final Connection delegate;
        private final int maxRows;
        private final int timeoutSecs;

        LimitingConnection(Connection delegate, int maxRows, int timeoutSecs) {
            this.delegate = delegate;
            this.maxRows = maxRows;
            this.timeoutSecs = timeoutSecs;
        }

        private Statement limit(Statement stmt) throws SQLException {
            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(timeoutSecs);
            return stmt;
        }

        @Override public Statement createStatement() throws SQLException { return limit(delegate.createStatement()); }
        @Override public Statement createStatement(int t, int c) throws SQLException { return limit(delegate.createStatement(t, c)); }
        @Override public Statement createStatement(int t, int c, int h) throws SQLException { return limit(delegate.createStatement(t, c, h)); }

        @Override public PreparedStatement prepareStatement(String sql) throws SQLException { return (PreparedStatement) limit(delegate.prepareStatement(sql)); }
        @Override public PreparedStatement prepareStatement(String sql, int t, int c) throws SQLException { return (PreparedStatement) limit(delegate.prepareStatement(sql, t, c)); }
        @Override public PreparedStatement prepareStatement(String sql, int t, int c, int h) throws SQLException { return (PreparedStatement) limit(delegate.prepareStatement(sql, t, c, h)); }
        @Override public PreparedStatement prepareStatement(String sql, int autoGenKeys) throws SQLException { return (PreparedStatement) limit(delegate.prepareStatement(sql, autoGenKeys)); }
        @Override public PreparedStatement prepareStatement(String sql, int[] colIdxs) throws SQLException { return (PreparedStatement) limit(delegate.prepareStatement(sql, colIdxs)); }
        @Override public PreparedStatement prepareStatement(String sql, String[] colNames) throws SQLException { return (PreparedStatement) limit(delegate.prepareStatement(sql, colNames)); }

        @Override public CallableStatement prepareCall(String sql) throws SQLException { return delegate.prepareCall(sql); }
        @Override public CallableStatement prepareCall(String sql, int t, int c) throws SQLException { return delegate.prepareCall(sql, t, c); }
        @Override public CallableStatement prepareCall(String sql, int t, int c, int h) throws SQLException { return delegate.prepareCall(sql, t, c, h); }

        @Override public String nativeSQL(String sql) throws SQLException { return delegate.nativeSQL(sql); }
        @Override public void setAutoCommit(boolean ac) throws SQLException { delegate.setAutoCommit(ac); }
        @Override public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
        @Override public void commit() throws SQLException { delegate.commit(); }
        @Override public void rollback() throws SQLException { delegate.rollback(); }
        @Override public void rollback(Savepoint sp) throws SQLException { delegate.rollback(sp); }
        @Override public void close() throws SQLException { delegate.close(); }
        @Override public boolean isClosed() throws SQLException { return delegate.isClosed(); }
        @Override public DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
        @Override public void setReadOnly(boolean ro) throws SQLException { delegate.setReadOnly(ro); }
        @Override public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
        @Override public void setCatalog(String c) throws SQLException { delegate.setCatalog(c); }
        @Override public String getCatalog() throws SQLException { return delegate.getCatalog(); }
        @Override public void setTransactionIsolation(int l) throws SQLException { delegate.setTransactionIsolation(l); }
        @Override public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
        @Override public SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        @Override public Map<String, Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
        @Override public void setTypeMap(Map<String, Class<?>> m) throws SQLException { delegate.setTypeMap(m); }
        @Override public void setHoldability(int h) throws SQLException { delegate.setHoldability(h); }
        @Override public int getHoldability() throws SQLException { return delegate.getHoldability(); }
        @Override public Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
        @Override public Savepoint setSavepoint(String n) throws SQLException { return delegate.setSavepoint(n); }
        @Override public void releaseSavepoint(Savepoint sp) throws SQLException { delegate.releaseSavepoint(sp); }
        @Override public Clob createClob() throws SQLException { return delegate.createClob(); }
        @Override public Blob createBlob() throws SQLException { return delegate.createBlob(); }
        @Override public NClob createNClob() throws SQLException { return delegate.createNClob(); }
        @Override public SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
        @Override public boolean isValid(int timeout) throws SQLException { return delegate.isValid(timeout); }
        @Override public void setClientInfo(String n, String v) throws SQLClientInfoException { delegate.setClientInfo(n, v); }
        @Override public void setClientInfo(Properties p) throws SQLClientInfoException { delegate.setClientInfo(p); }
        @Override public String getClientInfo(String n) throws SQLException { return delegate.getClientInfo(n); }
        @Override public Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
        @Override public Array createArrayOf(String t, Object[] e) throws SQLException { return delegate.createArrayOf(t, e); }
        @Override public Struct createStruct(String t, Object[] a) throws SQLException { return delegate.createStruct(t, a); }
        @Override public void setSchema(String s) throws SQLException { delegate.setSchema(s); }
        @Override public String getSchema() throws SQLException { return delegate.getSchema(); }
        @Override public void abort(Executor e) throws SQLException { delegate.abort(e); }
        @Override public void setNetworkTimeout(Executor e, int ms) throws SQLException { delegate.setNetworkTimeout(e, ms); }
        @Override public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    }
}
