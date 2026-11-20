package org.example.DAO;

import org.example.MODEL.Student;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StudentDAO {
    private final Connection conn;

    public StudentDAO(Connection conn) {
        this.conn = conn;
    }

    public Student findById(int userId) throws SQLException {
        String sql = "SELECT s.User_ID, s.Enrollment_year, " +
                "u.FirstName, u.LastName, u.Email " +
                "FROM STUDENT s " +
                "JOIN `USER` u ON u.User_ID = s.User_ID " +
                "WHERE s.User_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Student> findAll() throws SQLException {
        String sql = "SELECT s.User_ID, s.Enrollment_year, " +
                "u.FirstName, u.LastName, u.Email " +
                "FROM STUDENT s " +
                "JOIN `USER` u ON u.User_ID = s.User_ID " +
                "ORDER BY s.User_ID";
        List<Student> students = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                students.add(mapRow(rs));
            }
        }
        return students;
    }

    public Student findByEmail(String email) throws SQLException {
        String sql = "SELECT s.User_ID, s.Enrollment_year, " +
                "u.FirstName, u.LastName, u.Email " +
                "FROM STUDENT s " +
                "JOIN `USER` u ON u.User_ID = s.User_ID " +
                "WHERE u.Email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        return new Student(
                rs.getInt("User_ID"),
                rs.getInt("Enrollment_year"),
                rs.getString("FirstName"),
                rs.getString("LastName"),
                rs.getString("Email")
        );
    }
}
