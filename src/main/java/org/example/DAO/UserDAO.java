package org.example.DAO;

import org.example.MODEL.User;
import org.example.util.DatabaseConnection;
import org.example.util.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;

public class UserDAO {

    /**
     * Table names are UPPER CASE (`USER`, STUDENT, INSTRUCTOR) and the column is Password_hash.
     * The password is checked in Java, not in SQL: Password_hash holds a salted PBKDF2 hash, which
     * cannot be compared with "= ?". An old non-hashed value (e.g. 'sha256:stu_hash_101' from the
     * original seed) is still accepted by plain comparison.
     */
    public User authenticate(String email, String password) {
        String sql = "SELECT u.User_ID, u.FirstName, u.LastName, u.Email, u.Password_hash, " +
                "CASE WHEN s.User_ID IS NOT NULL THEN 'STUDENT' " +
                "     WHEN i.User_ID IS NOT NULL THEN 'INSTRUCTOR' ELSE 'UNKNOWN' END AS `Role` " +
                "FROM `USER` u " +
                "LEFT JOIN STUDENT s ON u.User_ID = s.User_ID " +
                "LEFT JOIN INSTRUCTOR i ON u.User_ID = i.User_ID " +
                "WHERE u.Email = ?";

        if (email == null || password == null) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && passwordMatches(password, rs.getString("Password_hash"))) {
                    return new User(
                            rs.getInt("User_ID"), rs.getString("FirstName"), rs.getString("LastName"),
                            rs.getString("Email"), rs.getString("Role")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean passwordMatches(String typed, String stored) {
        if (stored == null) {
            return false;
        }
        if (PasswordHasher.isHashed(stored)) {
            return PasswordHasher.verify(typed, stored);
        }
        return MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }
}