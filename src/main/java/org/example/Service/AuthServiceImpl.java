package org.example.Service;

import org.example.DAO.jdbc.UserDAO;
import org.example.util.DatabaseConnection;
import org.example.util.PasswordHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Login rules. The query is in {@link UserDAO}; the role comes from which subtype table
 * (STUDENT / INSTRUCTOR) holds the user.
 *
 * Passwords: USER.Password_hash holds a PBKDF2 hash ({@link PasswordHasher}). For a smooth
 * switch, a row that still holds an old non-hashed value (e.g. "sha256:stu_hash_101" from the old
 * seed) is compared as plain text ONCE; on success it is immediately replaced by a real hash.
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
                upgradeHashIfNeeded(conn, u.userId(), password, u.passwordHash());
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

    static boolean passwordMatches(String typed, String stored) {
        if (stored == null) {
            return false;
        }
        if (PasswordHasher.isHashed(stored)) {
            return PasswordHasher.verify(typed, stored);
        }
        // Legacy value (not hashed yet): plain comparison, then upgraded by upgradeHashIfNeeded.
        return MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    /** Re-hashes legacy/weak values after a successful login. A failure here never blocks the login. */
    private static void upgradeHashIfNeeded(Connection conn, int userId, String typed, String stored) {
        if (!PasswordHasher.needsRehash(stored)) {
            return;
        }
        try {
            UserDAO.updatePasswordHash(conn, userId, PasswordHasher.hash(typed));
        } catch (SQLException e) {
            System.err.println("[auth] could not upgrade password hash of user " + userId + ": " + e.getMessage());
        }
    }
}
