package org.example.Service;

import org.example.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

/** Small JDBC plumbing shared by the service implementations. */
final class Db {

    @FunctionalInterface
    interface Work<T> {
        T run(Connection conn) throws SQLException, ServiceException;
    }

    private Db() {
    }

    /** Opens a connection, runs the work, and turns SQL problems into UNAVAILABLE. */
    static <T> T run(Work<T> work) throws ServiceException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return work.run(conn);
        } catch (SQLException | RuntimeException | LinkageError e) {
            e.printStackTrace();
            throw new ServiceException(ServiceException.Kind.UNAVAILABLE,
                    "Cannot read from the database. [" + ServiceException.rootCauseText(e) + "]", e);
        }
    }

    /** Runs the work in one transaction: commit on success, rollback on any failure. */
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
}
