package com.framework.database;

import com.framework.config.FrameworkConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL database manager using HikariCP connection pool.
 *
 * Features:
 *  - Connection pool management (thread-safe)
 *  - Query execution with parameterized statements
 *  - Test data setup / teardown helpers
 *  - Row count, existence, and value assertion helpers
 *  - Transaction support
 *
 * Usage:
 *   PostgreSQLManager db = PostgreSQLManager.getInstance();
 *   List<Map<String,Object>> rows = db.query("SELECT * FROM users WHERE id = ?", userId);
 *   db.execute("DELETE FROM test_data WHERE session_id = ?", sessionId);
 */
@Slf4j
public class PostgreSQLManager {

    private static PostgreSQLManager instance;
    private static HikariDataSource dataSource;

    private PostgreSQLManager() {
        initPool();
    }

    public static synchronized PostgreSQLManager getInstance() {
        if (instance == null) instance = new PostgreSQLManager();
        return instance;
    }

    private void initPool() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s",
                FrameworkConfig.get("db.host", "localhost"),
                FrameworkConfig.get("db.port", "5432"),
                FrameworkConfig.get("db.name", "testdb")));
        cfg.setUsername(FrameworkConfig.get("db.user", "postgres"));
        cfg.setPassword(FrameworkConfig.get("db.password", ""));
        cfg.setMaximumPoolSize(FrameworkConfig.getInt("db.pool.size", 10));
        cfg.setConnectionTimeout(FrameworkConfig.getInt("db.connection.timeout", 30000));
        cfg.setPoolName("FrameworkPool");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(cfg);
        log.info("PostgreSQL pool initialised → {}", cfg.getJdbcUrl());
    }

    // ------------------------------------------------------------------ //
    //  Query execution                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Execute a SELECT query. Returns a list of row maps (column name → value).
     */
    public List<Map<String, Object>> query(String sql, Object... params) {
        log.debug("SQL QUERY: {}", sql);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("Query failed: {} | Error: {}", sql, e.getMessage());
            throw new RuntimeException("DB query failed", e);
        }
        log.debug("Returned {} row(s)", results.size());
        return results;
    }

    /**
     * Execute a single-value query (COUNT, MAX, etc.). Returns null if no result.
     */
    public Object querySingle(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        if (rows.isEmpty()) return null;
        return rows.get(0).values().iterator().next();
    }

    /**
     * Execute INSERT / UPDATE / DELETE. Returns the number of affected rows.
     */
    public int execute(String sql, Object... params) {
        log.debug("SQL EXECUTE: {}", sql);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            int affected = ps.executeUpdate();
            log.debug("{} row(s) affected", affected);
            return affected;
        } catch (SQLException e) {
            log.error("Execute failed: {} | Error: {}", sql, e.getMessage());
            throw new RuntimeException("DB execute failed", e);
        }
    }

    /**
     * Execute multiple statements in a single transaction.
     */
    public void executeTransaction(List<String> sqls, List<Object[]> paramsList) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (int i = 0; i < sqls.size(); i++) {
                    try (PreparedStatement ps = conn.prepareStatement(sqls.get(i))) {
                        if (paramsList != null && i < paramsList.size()) {
                            bindParams(ps, paramsList.get(i));
                        }
                        ps.executeUpdate();
                    }
                }
                conn.commit();
                log.debug("Transaction committed ({} statements)", sqls.size());
            } catch (SQLException e) {
                conn.rollback();
                log.error("Transaction rolled back: {}", e.getMessage());
                throw new RuntimeException("Transaction failed", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Connection failed", e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Test data helpers                                                   //
    // ------------------------------------------------------------------ //

    /** Returns the number of rows matching the WHERE clause. */
    public long countWhere(String table, String where, Object... params) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + where;
        Object result = querySingle(sql, params);
        return result == null ? 0 : ((Number) result).longValue();
    }

    /** Returns true if at least one row matches. */
    public boolean exists(String table, String where, Object... params) {
        return countWhere(table, where, params) > 0;
    }

    /** Fetch a single column value from the first matching row. */
    public Object getValue(String table, String column, String where, Object... params) {
        String sql = "SELECT " + column + " FROM " + table + " WHERE " + where + " LIMIT 1";
        return querySingle(sql, params);
    }

    /** Truncate a table (use in @AfterMethod for test data cleanup). */
    public void truncate(String table) {
        execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
        log.info("Truncated table: {}", table);
    }

    /** Delete rows matching a condition. */
    public int deleteWhere(String table, String where, Object... params) {
        return execute("DELETE FROM " + table + " WHERE " + where, params);
    }

    // ------------------------------------------------------------------ //
    //  Assertion helpers                                                   //
    // ------------------------------------------------------------------ //

    public void assertRowExists(String table, String where, Object... params) {
        if (!exists(table, where, params)) {
            throw new AssertionError("Expected row in '" + table + "' WHERE " + where + " but found none");
        }
    }

    public void assertRowCount(String table, String where, long expected, Object... params) {
        long actual = countWhere(table, where, params);
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected %d row(s) in '%s' WHERE %s but found %d", expected, table, where, actual));
        }
    }

    // ------------------------------------------------------------------ //

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection pool closed");
        }
    }

    private void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
