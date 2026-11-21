package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs the demo SQL files that are packed in the jar (folder /sql, copied from database/demo) through
 * the app's own connection, so the demo data can be set up or removed without MySQL Workbench.
 *
 * The files are plain scripts (no DELIMITER blocks): every statement ends with ';' at the end of a
 * line and lines that start with '--' are comments. USE / START TRANSACTION / COMMIT lines are skipped:
 * the connection is already on the schema of db.url, and the whole file runs in ONE transaction that
 * is committed at the end or rolled back on the first error (nothing half-done is left behind).
 */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    /** Runs /sql/{@code name} from the jar. Prints the rows of SELECT statements to {@code out}. */
    public static void runResource(String name, PrintStream out) throws SQLException, IOException {
        String sql;
        try (InputStream in = SqlScriptRunner.class.getResourceAsStream("/sql/" + name)) {
            if (in == null) {
                throw new IOException("SQL file not packed in the app: sql/" + name);
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        run(sql, out);
    }

    public static void run(String script, PrintStream out) throws SQLException {
        List<String> statements = split(script);
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            int n = 0;
            try (Statement st = c.createStatement()) {
                for (String sql : statements) {
                    n++;
                    if (st.execute(sql)) {
                        try (ResultSet rs = st.getResultSet()) {
                            print(rs, out);
                        }
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                String head = statements.get(n - 1).replaceAll("\\s+", " ");
                throw new SQLException("Statement " + n + " failed, nothing was changed (rolled back): "
                        + e.getMessage() + "\n  -> " + (head.length() > 160 ? head.substring(0, 160) + " ..." : head), e);
            } finally {
                c.setAutoCommit(true);
            }
            out.println("OK: " + statements.size() + " statements, committed.");
        }
    }

    static List<String> split(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : script.replace("\r", "").split("\n")) {
            String t = line.trim();
            if (cur.length() == 0 && (t.isEmpty() || t.startsWith("--"))) {
                continue;
            }
            if (t.startsWith("--")) {
                continue; // comment line inside a statement
            }
            cur.append(line).append('\n');
            if (t.endsWith(";")) {
                String sql = cur.toString().trim();
                sql = sql.substring(0, sql.length() - 1).trim();
                cur.setLength(0);
                String up = sql.toUpperCase(Locale.ROOT);
                if (up.startsWith("USE ") || up.equals("START TRANSACTION") || up.equals("COMMIT")) {
                    continue;
                }
                result.add(sql);
            }
        }
        if (!cur.toString().isBlank()) {
            result.add(cur.toString().trim());
        }
        return result;
    }

    private static void print(ResultSet rs, PrintStream out) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        List<String[]> rows = new ArrayList<>();
        String[] head = new String[cols];
        for (int i = 0; i < cols; i++) {
            head[i] = md.getColumnLabel(i + 1);
        }
        rows.add(head);
        while (rs.next()) {
            String[] r = new String[cols];
            for (int i = 0; i < cols; i++) {
                String v = rs.getString(i + 1);
                r[i] = v == null ? "NULL" : v;
            }
            rows.add(r);
        }
        int[] w = new int[cols];
        for (String[] r : rows) {
            for (int i = 0; i < cols; i++) {
                w[i] = Math.min(40, Math.max(w[i], r[i].length()));
            }
        }
        for (String[] r : rows) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < cols; i++) {
                String v = r[i].length() > 40 ? r[i].substring(0, 37) + "..." : r[i];
                b.append(String.format("%-" + w[i] + "s", v)).append(i + 1 < cols ? " | " : "");
            }
            out.println(b);
        }
        out.println("(" + (rows.size() - 1) + " rows)");
    }
}
