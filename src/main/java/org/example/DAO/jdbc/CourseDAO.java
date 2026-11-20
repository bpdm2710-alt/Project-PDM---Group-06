package org.example.DAO.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** COURSE, TEACH, COVERS and TOPIC: which instructor teaches what, and which topics a course covers. */
public final class CourseDAO {

    public record TeachingRow(int courseId, String name, int credits, String semester,
                              int students, int exams) {
    }

    public record TopicRow(int topicId, String name) {
    }

    private CourseDAO() {
    }

    public static boolean exists(Connection c, int courseId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM COURSE WHERE Course_ID = ?", courseId) > 0;
    }

    public static boolean teaches(Connection c, int instructorId, int courseId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM TEACH WHERE User_ID = ? AND Course_ID = ?",
                instructorId, courseId) > 0;
    }

    public static List<TeachingRow> listTeaching(Connection c, int instructorId) throws SQLException {
        String sql = "SELECT c.Course_ID, c.CName, c.Credits, t.Semester, " +
                "(SELECT COUNT(DISTINCT r.User_ID) FROM REGISTRATION r " +
                "   JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID WHERE e.Course_ID = c.Course_ID) AS students, " +
                "(SELECT COUNT(*) FROM EXAMINATION e2 WHERE e2.Course_ID = c.Course_ID) AS exams " +
                "FROM TEACH t JOIN COURSE c ON c.Course_ID = t.Course_ID " +
                "WHERE t.User_ID = ? ORDER BY c.CName, c.Course_ID";
        List<TeachingRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TeachingRow(rs.getInt("Course_ID"), rs.getString("CName"),
                            rs.getInt("Credits"), rs.getString("Semester"),
                            rs.getInt("students"), rs.getInt("exams")));
                }
            }
        }
        return out;
    }

    public static List<TopicRow> listTopics(Connection c, int courseId) throws SQLException {
        List<TopicRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT t.Topic_ID, t.Content FROM COVERS cv JOIN TOPIC t ON t.Topic_ID = cv.Topic_ID " +
                "WHERE cv.Course_ID = ? ORDER BY t.Content, t.Topic_ID")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TopicRow(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return out;
    }

    public static boolean topicExists(Connection c, int topicId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM TOPIC WHERE Topic_ID = ?", topicId) > 0;
    }

    /** True when the instructor teaches at least one course that covers the topic. */
    public static boolean teachesTopic(Connection c, int instructorId, int topicId) throws SQLException {
        return Jdbc.scalar(c,
                "SELECT COUNT(*) FROM COVERS cv JOIN TEACH t ON t.Course_ID = cv.Course_ID " +
                "WHERE cv.Topic_ID = ? AND t.User_ID = ?", topicId, instructorId) > 0;
    }

    /** Ids of every question whose topic the course covers. */
    public static Set<Integer> questionIds(Connection c, int courseId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT q.Quest_ID FROM QUESTION q JOIN COVERS cv ON cv.Topic_ID = q.Topic_ID " +
                "WHERE cv.Course_ID = ?")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }
}
