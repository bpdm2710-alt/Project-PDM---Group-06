package org.example.DAO;

import org.example.MODEL.User;
import org.example.util.DatabaseConnection; // Import từ util
import java.sql.*;

public class UserDAO {
    public User authenticate(String email, String password) {
        String sql = "SELECT u.User_ID, u.FirstName, u.LastName, u.Email, " +
                     "CASE WHEN s.User_ID IS NOT NULL THEN 'STUDENT' " +
                     "     WHEN i.User_ID IS NOT NULL THEN 'INSTRUCTOR' ELSE 'UNKNOWN' END AS Role " +
                     "FROM `user` u " +
                     "LEFT JOIN `student` s ON u.User_ID = s.User_ID " +
                     "LEFT JOIN `instructor` i ON u.User_ID = i.User_ID " +
                     "WHERE u.Email = ? AND u.Password_hash_ = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, email);
            ps.setString(2, password);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
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
}