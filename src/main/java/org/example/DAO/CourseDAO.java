package org.example.DAO;
import org.example.MODEL.Course;
import java.sql.*; import java.util.*;

public class CourseDAO {
    private final Connection conn;
    public CourseDAO(Connection conn) { this.conn = conn; }

    public List<Course> getAllCourses() {
        List<Course> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT Course_ID, CName AS courseName, Credits FROM course");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Course(rs.getInt("Course_ID"), rs.getString("courseName"), rs.getInt("Credits")));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}
