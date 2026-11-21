package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the student screens read before an exam starts: profile, course browsing,
 * exam cards and registration. Taking an exam is in {@link AttemptDAO}.
 */
public final class StudentDAO {

    public record Profile(String firstName, String lastName, int enrollmentYear) {
    }

    /** A course card; {@code semester} is the latest semester it is taught in (may be null). */
    public record CourseRow(int courseId, String name, int credits, String semester,
                            int students, int upcoming) {
    }

    /**
     * One exam as seen by one student. {@code regisId} is null when the student has not registered.
     * {@code questionCount}/{@code totalMarks} are those of the lowest-numbered paper.
     */
    public record ExamCardRow(Integer regisId, int examId, int courseId, String courseName, String title,
                              Timestamp date, int duration, BigDecimal passMark, int maxAttempts,
                              int used, int inProgress, int questionCount, BigDecimal totalMarks) {
    }

    public record ExamWindow(Timestamp date, int duration, String status) {
    }

    private StudentDAO() {
    }

    /** @return null when the id is not a student. */
    public static Profile findProfile(Connection c, int studentId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT u.FirstName, u.LastName, s.Enrollment_year " +
                "FROM `USER` u JOIN STUDENT s ON s.User_ID = u.User_ID WHERE u.User_ID = ?")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Profile(rs.getString(1), rs.getString(2), rs.getInt(3)) : null;
            }
        }
    }

    // ---- Courses ----------------------------------------------------------

    public static List<String> filterTopics(Connection c) throws SQLException {
        return strings(c, "SELECT DISTINCT t.Content FROM TOPIC t JOIN COVERS cv ON cv.Topic_ID = t.Topic_ID " +
                "ORDER BY t.Content");
    }

    public static List<String> filterSemesters(Connection c) throws SQLException {
        return strings(c, "SELECT DISTINCT Semester FROM TEACH ORDER BY Semester DESC");
    }

    private static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    /**
     * @param mine true = courses where the student registered for an exam, false = all the others
     * @param now  exams of the course that are PUBLISHED and dated after this count as "upcoming"
     */
    public static List<CourseRow> listCourses(Connection c, int studentId, boolean mine, String keyword,
                                              String topic, String semester, Timestamp now)
            throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT c.Course_ID, c.CName, c.Credits, " +
                "(SELECT MAX(t.Semester) FROM TEACH t WHERE t.Course_ID = c.Course_ID) AS semester, " +
                "(SELECT COUNT(DISTINCT r2.User_ID) FROM REGISTRATION r2 " +
                "   JOIN EXAMINATION e2 ON e2.Exam_ID = r2.Exam_ID WHERE e2.Course_ID = c.Course_ID) AS student_count, " +
                "(SELECT COUNT(*) FROM EXAMINATION e3 WHERE e3.Course_ID = c.Course_ID " +
                "   AND e3.Status = 'PUBLISHED' AND e3.Exam_date > ?) AS upcoming " +
                "FROM COURSE c WHERE ");
        params.add(now);
        sql.append(mine ? "EXISTS" : "NOT EXISTS")
           .append(" (SELECT 1 FROM REGISTRATION r JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                   "WHERE r.User_ID = ? AND e.Course_ID = c.Course_ID)");
        params.add(studentId);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND c.CName LIKE ?");
            params.add("%" + likeEscape(keyword.trim()) + "%");
        }
        if (topic != null && !topic.isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM COVERS cv JOIN TOPIC tp ON tp.Topic_ID = cv.Topic_ID " +
                    "WHERE cv.Course_ID = c.Course_ID AND tp.Content = ?)");
            params.add(topic.trim());
        }
        if (semester != null && !semester.isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM TEACH th WHERE th.Course_ID = c.Course_ID AND th.Semester = ?)");
            params.add(semester.trim());
        }
        sql.append(" ORDER BY c.CName, c.Course_ID");

        List<CourseRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            Jdbc.bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CourseRow(rs.getInt("Course_ID"), rs.getString("CName"), rs.getInt("Credits"),
                            rs.getString("semester"), rs.getInt("student_count"), rs.getInt("upcoming")));
                }
            }
        }
        return out;
    }

    /** Instructor names per course (ordered by name, no duplicates over semesters). */
    public static Map<Integer, List<String>> instructorNames(Connection c, List<Integer> courseIds)
            throws SQLException {
        Map<Integer, Set<String>> tmp = new LinkedHashMap<>();
        if (!courseIds.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT th.Course_ID, u.FirstName, u.LastName FROM TEACH th " +
                    "JOIN `USER` u ON u.User_ID = th.User_ID WHERE th.Course_ID IN (" +
                    Jdbc.placeholders(courseIds.size()) + ") ORDER BY u.LastName, u.FirstName")) {
                Jdbc.bind(ps, courseIds.toArray(), 0);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tmp.computeIfAbsent(rs.getInt(1), k -> new LinkedHashSet<>())
                                .add((rs.getString(2) + " " + rs.getString(3)).trim());
                    }
                }
            }
        }
        Map<Integer, List<String>> out = new HashMap<>();
        tmp.forEach((k, v) -> out.put(k, new ArrayList<>(v)));
        return out;
    }

    public static Map<Integer, List<String>> topicNames(Connection c, List<Integer> courseIds)
            throws SQLException {
        Map<Integer, List<String>> out = new HashMap<>();
        if (courseIds.isEmpty()) {
            return out;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT cv.Course_ID, tp.Content FROM COVERS cv JOIN TOPIC tp ON tp.Topic_ID = cv.Topic_ID " +
                "WHERE cv.Course_ID IN (" + Jdbc.placeholders(courseIds.size()) + ") ORDER BY tp.Content")) {
            Jdbc.bind(ps, courseIds.toArray(), 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
            }
        }
        return out;
    }

    // ---- Exams / registration ---------------------------------------------

    /**
     * PUBLISHED exams for one student. Filters are optional: a course, one exam, or only exams the
     * student registered for. Exams without a registration come back with {@code regisId == null}.
     */
    public static List<ExamCardRow> examCards(Connection c, int studentId, Integer courseId, Integer examId,
                                              boolean registeredOnly) throws SQLException {
        String paper = " FROM PAPER_QUESTION pq JOIN EXAM_QUESTION_MARKS eqm " +
                "ON eqm.Exam_ID = e.Exam_ID AND eqm.Quest_ID = pq.Quest_ID " +
                "WHERE pq.PaperKey = (SELECT p.PaperKey FROM PAPER p WHERE p.Exam_ID = e.Exam_ID " +
                "ORDER BY p.PaperID LIMIT 1)";
        StringBuilder sql = new StringBuilder(
                "SELECT r.RegisID, e.Exam_ID, e.Course_ID, c.CName, e.Title, e.Exam_date, e.Duration, " +
                "e.Pass_mark, e.Max_attempts, " +
                "(SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = r.RegisID) AS used, " +
                "(SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = r.RegisID AND a.EndTime IS NULL) AS in_progress, " +
                "(SELECT COUNT(*)" + paper + ") AS question_count, " +
                "(SELECT COALESCE(SUM(eqm.Marks), 0)" + paper + ") AS total_marks " +
                "FROM EXAMINATION e JOIN COURSE c ON c.Course_ID = e.Course_ID " +
                "LEFT JOIN REGISTRATION r ON r.Exam_ID = e.Exam_ID AND r.User_ID = ? " +
                "WHERE e.Status = 'PUBLISHED'");
        List<Object> params = new ArrayList<>();
        params.add(studentId);
        if (registeredOnly) {
            sql.append(" AND r.RegisID IS NOT NULL");
        }
        if (courseId != null) {
            sql.append(" AND e.Course_ID = ?");
            params.add(courseId);
        }
        if (examId != null) {
            sql.append(" AND e.Exam_ID = ?");
            params.add(examId);
        }
        sql.append(" ORDER BY e.Exam_date, e.Exam_ID");

        List<ExamCardRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            Jdbc.bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int regis = rs.getInt("RegisID");
                    Integer regisId = rs.wasNull() ? null : regis;
                    out.add(new ExamCardRow(regisId, rs.getInt("Exam_ID"), rs.getInt("Course_ID"),
                            rs.getString("CName"), rs.getString("Title"), rs.getTimestamp("Exam_date"),
                            rs.getInt("Duration"), rs.getBigDecimal("Pass_mark"), rs.getInt("Max_attempts"),
                            rs.getInt("used"), rs.getInt("in_progress"), rs.getInt("question_count"),
                            rs.getBigDecimal("total_marks")));
                }
            }
        }
        return out;
    }

    /** @return null when the exam does not exist. */
    public static ExamWindow findExamWindow(Connection c, int examId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Exam_date, Duration, Status FROM EXAMINATION WHERE Exam_ID = ?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ExamWindow(rs.getTimestamp(1), rs.getInt(2), rs.getString(3)) : null;
            }
        }
    }

    /**
     * Inserts the registration. A duplicate (User_ID, Exam_ID) surfaces as an SQLException with
     * error code 1062; a registration after Exam_date is refused by a trigger (SQLSTATE 45000).
     */
    public static int register(Connection c, int studentId, int examId, Timestamp now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO REGISTRATION (RegisDate, User_ID, Exam_ID) VALUES (?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, now);
            ps.setInt(2, studentId);
            ps.setInt(3, examId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
