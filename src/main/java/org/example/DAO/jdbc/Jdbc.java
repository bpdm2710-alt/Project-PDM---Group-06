package org.example.DAO.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Tiny JDBC helpers shared by the DAOs. Package-private on purpose. */
final class Jdbc {

    private Jdbc() {
    }

    /** First column of the first row as int, or 0 when there is no row (ids start at 1, so 0 = not found). */
    static int scalar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    static int exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params, 0);
            return ps.executeUpdate();
        }
    }

    static int bind(PreparedStatement ps, Object[] params, int offset) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(offset + i + 1, params[i]);
        }
        return offset + params.length;
    }

    /**
     * Next id for a table whose key is NOT AUTO_INCREMENT in the submitted schema
     * (USER, COURSE, TOPIC, QUESTION, EXAMINATION, REGISTRATION). FOR UPDATE locks the end of the
     * key index until the transaction ends, so two concurrent inserts cannot get the same id.
     * {@code table}/{@code idColumn} are constants from the DAOs, never user input.
     */
    static int nextId(Connection c, String table, String idColumn) throws SQLException {
        return scalar(c, "SELECT COALESCE(MAX(" + idColumn + "), 0) + 1 FROM " + table + " FOR UPDATE");
    }

    /** "?,?,?" with n placeholders (n >= 1). */
    static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    static int bind(PreparedStatement ps, List<Object> params, int offset) throws SQLException {
        return bind(ps, params.toArray(), offset);
    }
}
