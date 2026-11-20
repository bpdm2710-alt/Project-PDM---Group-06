package org.example.Service;

import org.example.DAO.jdbc.UserDAO;
import org.example.util.DatabaseConnection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Login rules. The query is in {@link UserDAO}; the role comes from which subtype table
 * (STUDENT / INSTRUCTOR) holds the user.
 *
 * NOTE: Password_hash_ currently holds the plain password (seed data does),
 * so the comparison is plain. When the team starts storing real hashes,
 * change only {@link #passwordMatches}.
 */
public class AuthServiceImpl implements AuthService {

    @Override
    public UserAccount login(String email, String password) throws ServiceException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Please enter both email and password.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            UserDAO.LoginRow u = UserDAO.findByEmail(conn, email.trim());
            if (u != null && passwordMatches(password, u.passwordHash())) {
                Role role = u.student() ? Role.STUDENT : u.instructor() ? Role.INSTRUCTOR : null;
                if (role != null) {
                    return new UserAccount(u.userId(), u.firstName(), u.lastName(), u.email(), role);
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
