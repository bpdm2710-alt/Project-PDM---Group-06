package org.example.Service;

import org.example.util.DatabaseConnection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Login against the USER table. The role comes from which subtype table
 * (STUDENT / INSTRUCTOR) holds the user.
 *
 * NOTE: Password_hash_ currently holds the plain password (seed data does),
 * so the comparison is plain. When the team starts storing real hashes,
 * change only {@link #passwordMatches}.
 */
public class AuthServiceImpl implements AuthService {

    private static final String SQL =
            "SELECT u.User_ID, u.FirstName, u.LastName, u.Email, u.Password_hash_, " +
            "       (s.User_ID IS NOT NULL) AS is_student, " +
            "       (i.User_ID IS NOT NULL) AS is_instructor " +
            "FROM `USER` u " +
            "LEFT JOIN STUDENT s ON s.User_ID = u.User_ID " +
            "LEFT JOIN INSTRUCTOR i ON i.User_ID = u.User_ID " +
            "WHERE LOWER(u.Email) = LOWER(?)";

    @Override
    public UserAccount login(String email, String password) throws ServiceException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Please enter both email and password.");
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && passwordMatches(password, rs.getString("Password_hash_"))) {
                    Role role = rs.getBoolean("is_student") ? Role.STUDENT
                            : rs.getBoolean("is_instructor") ? Role.INSTRUCTOR : null;
                    if (role != null) {
                        return new UserAccount(rs.getInt("User_ID"), rs.getString("FirstName"),
                                rs.getString("LastName"), rs.getString("Email"), role);
                    }
                }
            }
        } catch (SQLException | RuntimeException | LinkageError e) {
            // RuntimeException/LinkageError: application.properties missing or unreadable.
            e.printStackTrace();
            throw new ServiceException(ServiceException.Kind.UNAVAILABLE,
                    "Cannot reach the database. [" + ServiceException.rootCauseText(e) + "]", e);
        }
        // Same message for unknown email, wrong password or a user without a role.
        throw new ServiceException(ServiceException.Kind.UNAUTHORIZED, "Invalid email or password.");
    }

    private static boolean passwordMatches(String typed, String stored) {
        if (stored == null) {
            return false;
        }
        return MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }
}
