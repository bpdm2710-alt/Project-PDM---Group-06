package org.example.Service;

import org.example.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Small JDBC plumbing shared by the service implementations. */
final class Db {

    @FunctionalInterface
    interface Work<T> {
        T run(Connection conn) throws SQLException, ServiceException;
    }

    private Db() {
    }

    /** Opens a connection, runs the work, and turns SQL problems into a {@link ServiceException}. */
    static <T> T run(Work<T> work) throws ServiceException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return work.run(conn);
        } catch (SQLException e) {
            e.printStackTrace();
            throw translate(e);
        } catch (RuntimeException | LinkageError e) {
            e.printStackTrace();
            throw new ServiceException(ServiceException.Kind.UNAVAILABLE,
                    "Cannot read from the database. [" + ServiceException.rootCauseText(e) + "]", e);
        }
    }

    /** Runs the work in one transaction: commit on success, rollback on any failure. */
    /**
     * The current time from the DATABASE clock (SELECT NOW()), not from the user's PC. Every
     * connection runs with session time_zone = db.timezone (default +07:00, see DatabaseConnection),
     * so the value is in the same time zone as the stored Exam_date / StartTime values.
     * Exam windows, timers and registration deadlines are therefore the same for every student,
     * whatever the clock or time zone of their computer.
     */
    static LocalDateTime now(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT NOW()");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getTimestamp(1).toLocalDateTime().withNano(0);
        }
    }

    static <T> T tx(Connection conn, Work<T> work) throws SQLException, ServiceException {
        conn.setAutoCommit(false);
        try {
            T result = work.run(conn);
            conn.commit();
            return result;
        } catch (SQLException | ServiceException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
    }

    /**
     * Maps what the database itself refused (business-rule triggers, CHECK, UNIQUE and FOREIGN KEY
     * violations) to a message the user can read; anything else means the database is unavailable.
     */
    static ServiceException translate(SQLException e) {
        String state = e.getSQLState();
        int code = e.getErrorCode();
        if ("45000".equals(state)) {              // SIGNAL from a trigger / stored procedure
            return new ServiceException(ServiceException.Kind.CONFLICT, e.getMessage(), e);
        }
        if (code == 3819) {                        // CHECK constraint violated
            return new ServiceException(ServiceException.Kind.VALIDATION,
                    "A value is outside the allowed range. [" + ServiceException.rootCauseText(e) + "]", e);
        }
        if (code == 1062) {                        // duplicate key
            return new ServiceException(ServiceException.Kind.CONFLICT,
                    "That record already exists.", e);
        }
        if (code == 1451 || code == 1452) {        // foreign key
            return new ServiceException(ServiceException.Kind.CONFLICT,
                    "The data is linked to other records, so this change is not allowed.", e);
        }
        return new ServiceException(ServiceException.Kind.UNAVAILABLE,
                "Cannot read from the database. [" + ServiceException.rootCauseText(e) + "]", e);
    }
}
