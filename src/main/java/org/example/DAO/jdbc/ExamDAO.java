package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** EXAMINATION plus the read-only views of its papers (PAPER / CONTAINS). */
public final class ExamDAO {

    /** One line of the exams list. {@code questionCount}/{@code totalMarks} are for the lowest-numbered paper. */
    public record ListRow(int examId, String title, Timestamp date, int duration, String status,
                          int questionCount, BigDecimal totalMarks) {
    }

    public record Head(int examId, int courseId, String title, Timestamp date, int duration,
                       BigDecimal passMark, int maxAttempts, String status) {
    }

    public record PaperQuestionRow(int paperId, int questionId, int order, String content,
                                   boolean mcq, BigDecimal marks) {
    }

    private static final String FIRST_PAPER =
            " FROM CONTAINS ct WHERE ct.Exam_ID = e.Exam_ID AND ct.PaperID = " +
            "(SELECT MIN(p.PaperID) FROM PAPER p WHERE p.Exam_ID = e.Exam_ID)";

    private ExamDAO() {
    }

    public static List<ListRow> list(Connection c, int courseId) throws SQLException {
        String sql = "SELECT e.Exam_ID, e.Title, e.Exam_date, e.Duration, e.Status, " +
                "(SELECT COUNT(*)" + FIRST_PAPER + ") AS question_count, " +
                "(SELECT COALESCE(SUM(ct.Marks), 0)" + FIRST_PAPER + ") AS total_marks " +
                "FROM EXAMINATION e WHERE e.Course_ID = ? " +
                "ORDER BY (e.Exam_date IS NULL), e.Exam_date DESC, e.Exam_ID DESC";
        List<ListRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ListRow(rs.getInt("Exam_ID"), rs.getString("Title"),
                            rs.getTimestamp("Exam_date"), rs.getInt("Duration"), rs.getString("Status"),
                            rs.getInt("question_count"), rs.getBigDecimal("total_marks")));
                }
            }
        }
        return out;
    }

    /** @return null when the exam does not exist. */
    public static Head find(Connection c, int examId) throws SQLException {
        return head(c, examId, false);
    }

    /** Same as {@link #find} but locks the row until the transaction ends. */
    public static Head findForUpdate(Connection c, int examId) throws SQLException {
        return head(c, examId, true);
    }

    private static Head head(Connection c, int examId, boolean lock) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Exam_ID, Course_ID, Title, Exam_date, Duration, Pass_mark, Max_attempts, Status " +
                "FROM EXAMINATION WHERE Exam_ID = ?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Head(rs.getInt("Exam_ID"), rs.getInt("Course_ID"), rs.getString("Title"),
                        rs.getTimestamp("Exam_date"), rs.getInt("Duration"), rs.getBigDecimal("Pass_mark"),
                        rs.getInt("Max_attempts"), rs.getString("Status"));
            }
        }
    }

    /** New exams always start as DRAFT. */
    public static int insert(Connection c, String title, Timestamp date, int duration,
                             BigDecimal passMark, int maxAttempts, int courseId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO EXAMINATION (Title, Exam_date, Duration, Pass_mark, Max_attempts, " +
                "Course_ID, Status) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setTimestamp(2, date);
            ps.setInt(3, duration);
            ps.setBigDecimal(4, passMark);
            ps.setInt(5, maxAttempts);
            ps.setInt(6, courseId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The exam was inserted but no id was returned.");
                }
                return keys.getInt(1);
            }
        }
    }

    public static void update(Connection c, int examId, String title, Timestamp date, int duration,
                              BigDecimal passMark, int maxAttempts) throws SQLException {
        Jdbc.exec(c, "UPDATE EXAMINATION SET Title = ?, Exam_date = ?, Duration = ?, Pass_mark = ?, " +
                "Max_attempts = ? WHERE Exam_ID = ?", title, date, duration, passMark, maxAttempts, examId);
    }

    /** @param status DRAFT or PUBLISHED */
    public static void setStatus(Connection c, int examId, String status) throws SQLException {
        Jdbc.exec(c, "UPDATE EXAMINATION SET Status = ? WHERE Exam_ID = ?", status, examId);
    }

    /** Removes the exam with its paper(s) and registrations, in foreign-key-safe order. */
    public static void deleteWithPapers(Connection c, int examId) throws SQLException {
        Jdbc.exec(c, "DELETE FROM CONTAINS WHERE Exam_ID = ?", examId);
        Jdbc.exec(c, "DELETE FROM PAPER WHERE Exam_ID = ?", examId);
        Jdbc.exec(c, "DELETE FROM REGISTRATION WHERE Exam_ID = ?", examId);
        Jdbc.exec(c, "DELETE FROM EXAMINATION WHERE Exam_ID = ?", examId);
    }

    public static int countAttempts(Connection c, int examId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM ATTEMPT WHERE Exam_ID = ?", examId);
    }

    public static int mostAttemptsByOneStudent(Connection c, int examId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COALESCE(MAX(n), 0) FROM " +
                "(SELECT COUNT(*) AS n FROM ATTEMPT WHERE Exam_ID = ? GROUP BY RegisID) x", examId);
    }

    /** Questions in the exam's lowest-numbered paper (the one students get). */
    public static int countPaperQuestions(Connection c, int examId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM CONTAINS ct WHERE ct.Exam_ID = ? AND ct.PaperID = " +
                "(SELECT MIN(p.PaperID) FROM PAPER p WHERE p.Exam_ID = ct.Exam_ID)", examId);
    }

    public static List<Integer> paperIds(Connection c, int examId) throws SQLException {
        List<Integer> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT PaperID FROM PAPER WHERE Exam_ID = ? ORDER BY PaperID")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        }
        return out;
    }

    public static List<PaperQuestionRow> paperQuestions(Connection c, int examId) throws SQLException {
        List<PaperQuestionRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ct.PaperID, ct.Quest_ID, ct.QuestionOrder, ct.Marks, q.Content, " +
                "(m.Quest_ID IS NOT NULL) AS is_mcq " +
                "FROM CONTAINS ct JOIN QUESTION q ON q.Quest_ID = ct.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = ct.Quest_ID " +
                "WHERE ct.Exam_ID = ? ORDER BY ct.PaperID, ct.QuestionOrder, ct.Quest_ID")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PaperQuestionRow(rs.getInt("PaperID"), rs.getInt("Quest_ID"),
                            rs.getInt("QuestionOrder"), rs.getString("Content"), rs.getBoolean("is_mcq"),
                            rs.getBigDecimal("Marks")));
                }
            }
        }
        return out;
    }
}
