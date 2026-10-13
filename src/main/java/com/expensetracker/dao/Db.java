package com.expensetracker.dao;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Week 12 — JDBC connection factory.
 * Credentials come ONLY from environment variables (never hardcoded):
 *   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
 *
 * Each DAO call opens its own Connection in a try-with-resources block and
 * closes it — simple and safe for this project's scale. (A production app
 * would use a connection pool; the trade-off is explained in the README.)
 */
public final class Db {

    private Db() {
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** JDBC URL built from the environment, e.g. jdbc:mysql://localhost:3306/expensetracker */
    public static String jdbcUrl() {
        String url = "jdbc:mysql://" + env("DB_HOST", "localhost") + ":"
                + env("DB_PORT", "3306") + "/" + env("DB_NAME", "expensetracker")
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        // Sandboxed environments: reach MySQL through a Unix socket instead
        // of TCP (see UnixSocketFactory). Unset DB_SOCKET for normal TCP.
        if (System.getenv("DB_SOCKET") != null && !System.getenv("DB_SOCKET").isBlank()) {
            url += "&socketFactory=com.expensetracker.dao.UnixSocketFactory";
        }
        return url;
    }

    /** Open a new JDBC connection. Caller MUST close it (use try-with-resources). */
    public static Connection getConnection() throws java.sql.SQLException {
        return DriverManager.getConnection(
                jdbcUrl(), env("DB_USER", "etuser"), env("DB_PASSWORD", "change-me"));
    }

    /**
     * Run several DAO calls inside ONE database transaction: either every
     * statement commits, or a failure rolls everything back.
     * DAOs expose overloads that take the shared Connection for this.
     */
    public static void inTransaction(TransactionWork work) throws SQLException {
        try (Connection conn = getConnection()) {
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false); // manual transaction control
            try {
                work.run(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw (e instanceof SQLException se) ? se : new SQLException(e);
            } finally {
                conn.setAutoCommit(auto);
            }
        }
    }

    /** A unit of work that shares one JDBC Connection (see inTransaction). */
    @FunctionalInterface
    public interface TransactionWork {
        void run(Connection conn) throws Exception;
    }

    /**
     * Create tables on first start by executing schema.sql from the classpath.
     * Safe to run every boot: every statement is CREATE TABLE IF NOT EXISTS.
     */
    public static void initSchema() {
        try (InputStream in = Db.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                System.out.println("[db] schema.sql not on classpath, skipping auto-init");
                return;
            }
            String sql = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            // Strip comment lines and split into individual statements.
            StringBuilder current = new StringBuilder();
            try (Connection c = getConnection(); Statement st = c.createStatement()) {
                for (String line : sql.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                        continue; // skip SQL comments / blank lines
                    }
                    current.append(line).append('\n');
                    if (trimmed.endsWith(";")) {
                        String stmt = current.toString().trim();
                        current.setLength(0);
                        String upper = stmt.toUpperCase();
                        // Only DDL is executed here; sample SELECTs in comments are skipped anyway.
                        if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")
                                || upper.startsWith("CREATE TABLE")) {
                            st.execute(stmt);
                        }
                    }
                }
                System.out.println("[db] schema initialised (tables ensured)");
            }
        } catch (Exception e) {
            // Don't kill the app: log loudly so misconfiguration is obvious.
            System.out.println("[db] WARNING: schema auto-init failed: " + e.getMessage());
        }
    }
}
