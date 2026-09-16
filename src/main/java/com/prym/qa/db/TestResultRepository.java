package com.prym.qa.db;

import com.prym.qa.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores every scenario execution in SQLite, so the dashboard can answer
 * "what passed, what failed, when, and where is the report".
 *
 * <p>SQLite was chosen because it needs no server, no service and no password:
 * the database is a file in the repo, so a reviewer can clone and run without
 * installing anything. The SQL is ordinary JDBC - pointing this at MySQL or
 * PostgreSQL is a URL and driver change, nothing more.
 */
public final class TestResultRepository {

    private TestResultRepository() {
    }

    private static Connection connect() throws SQLException {
        String url = Config.dbUrl();
        if (url.startsWith("jdbc:sqlite:")) {
            Path file = Path.of(url.substring("jdbc:sqlite:".length()));
            try {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
            } catch (Exception e) {
                throw new SQLException("Cannot create database directory", e);
            }
        }
        return DriverManager.getConnection(url);
    }

    public static void initSchema() {
        String runs = """
                CREATE TABLE IF NOT EXISTS test_run (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    started_at    TEXT    NOT NULL,
                    finished_at   TEXT,
                    environment   TEXT,
                    browser       TEXT,
                    total         INTEGER DEFAULT 0,
                    passed        INTEGER DEFAULT 0,
                    failed        INTEGER DEFAULT 0,
                    report_path   TEXT
                )""";
        String results = """
                CREATE TABLE IF NOT EXISTS test_result (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_id        INTEGER NOT NULL,
                    feature       TEXT,
                    scenario      TEXT    NOT NULL,
                    tags          TEXT,
                    status        TEXT    NOT NULL,
                    duration_ms   INTEGER,
                    error_message TEXT,
                    executed_at   TEXT    NOT NULL,
                    report_path   TEXT,
                    FOREIGN KEY (run_id) REFERENCES test_run(id)
                )""";
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(runs);
            s.execute(results);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialise the results schema", e);
        }
    }

    public static long startRun(String browser, String reportPath) {
        initSchema();
        String sql = "INSERT INTO test_run (started_at, environment, browser, report_path) VALUES (?,?,?,?)";
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, Config.environmentName());
            ps.setString(3, browser);
            ps.setString(4, reportPath);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open a test run row", e);
        }
    }

    public static void saveResult(long runId, String feature, String scenario, String tags,
                                  String status, long durationMs, String error, String reportPath) {
        String sql = """
                INSERT INTO test_result
                  (run_id, feature, scenario, tags, status, duration_ms, error_message, executed_at, report_path)
                VALUES (?,?,?,?,?,?,?,?,?)""";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, runId);
            ps.setString(2, feature);
            ps.setString(3, scenario);
            ps.setString(4, tags);
            ps.setString(5, status);
            ps.setLong(6, durationMs);
            ps.setString(7, error);
            ps.setString(8, Instant.now().toString());
            ps.setString(9, reportPath);
            ps.executeUpdate();
        } catch (SQLException e) {
            // A reporting failure must never mask a test result.
            System.err.println("[db] could not save result for '" + scenario + "': " + e.getMessage());
        }
    }

    public static void finishRun(long runId) {
        String sql = """
                UPDATE test_run SET
                  finished_at = ?,
                  total  = (SELECT COUNT(*) FROM test_result WHERE run_id = ?),
                  passed = (SELECT COUNT(*) FROM test_result WHERE run_id = ? AND status = 'PASSED'),
                  failed = (SELECT COUNT(*) FROM test_result WHERE run_id = ? AND status = 'FAILED')
                WHERE id = ?""";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setLong(2, runId);
            ps.setLong(3, runId);
            ps.setLong(4, runId);
            ps.setLong(5, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[db] could not close run " + runId + ": " + e.getMessage());
        }
    }

    /** One row per scenario execution, newest first - what the dashboard renders. */
    public static List<Row> recentResults(int limit) {
        String sql = """
                SELECT r.id, r.feature, r.scenario, r.tags, r.status, r.duration_ms,
                       r.error_message, r.executed_at, r.report_path,
                       r.run_id, t.environment, t.browser
                FROM test_result r
                JOIN test_run t ON t.id = r.run_id
                ORDER BY r.id DESC
                LIMIT ?""";
        List<Row> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(
                            rs.getLong("id"), rs.getString("feature"), rs.getString("scenario"),
                            rs.getString("tags"), rs.getString("status"), rs.getLong("duration_ms"),
                            rs.getString("error_message"), rs.getString("executed_at"),
                            rs.getString("report_path"), rs.getLong("run_id"),
                            rs.getString("environment"), rs.getString("browser")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read results", e);
        }
        return rows;
    }

    public static Summary summary() {
        String sql = """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status='PASSED' THEN 1 ELSE 0 END) AS passed,
                       SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed,
                       (SELECT COUNT(*) FROM test_run) AS runs
                FROM test_result""";
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (rs.next()) {
                return new Summary(rs.getInt("total"), rs.getInt("passed"),
                        rs.getInt("failed"), rs.getInt("runs"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not summarise results", e);
        }
        return new Summary(0, 0, 0, 0);
    }

    public record Row(long id, String feature, String scenario, String tags, String status,
                      long durationMs, String error, String executedAt, String reportPath,
                      long runId, String environment, String browser) {
    }

    public record Summary(int total, int passed, int failed, int runs) {
    }
}
