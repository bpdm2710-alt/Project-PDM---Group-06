package org.example.DAO.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** USER + the subtype tables STUDENT / INSTRUCTOR. */
public final class UserDAO {

    public record LoginRow(int userId, String firstName, String lastName, String email,
                           String passwordHash, boolean student, boolean instructor) {
    }

    private UserDAO() {
    }

    /** @return null when no user has that email (case-insensitive). */
    public static LoginRow findByEmail(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT u.User_ID, u.FirstName, u.LastName, u.Email, u.Password_hash, " +
                "       (s.User_ID IS NOT NULL) AS is_student, " +
                "       (i.User_ID IS NOT NULL) AS is_instructor " +
                "FROM `USER` u " +
                "LEFT JOIN STUDENT s ON s.User_ID = u.User_ID " +
                "LEFT JOIN INSTRUCTOR i ON i.User_ID = u.User_ID " +
                "WHERE LOWER(u.Email) = LOWER(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new LoginRow(rs.getInt("User_ID"), rs.getString("FirstName"),
                        rs.getString("LastName"), rs.getString("Email"), rs.getString("Password_hash"),
                        rs.getBoolean("is_student"), rs.getBoolean("is_instructor"));
            }
        }
    }

    public static boolean isInstructor(Connection c, int userId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM INSTRUCTOR WHERE User_ID = ?", userId) > 0;
    }
}
