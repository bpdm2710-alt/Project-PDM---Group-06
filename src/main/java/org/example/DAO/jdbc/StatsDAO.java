package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only queries behind the instructor Statistics tab. Everything is limited to PUBLISHED exams
 * of one course; "finished attempt" means EndTime IS NOT NULL. Choosing which attempt counts and
 * turning rows into percentages is the service's job.
 */
public final class StatsDAO {

    public record ExamTime(String status, Timestamp date, int duration) {
    }

    public record ExamColumn(int examId, String title) {
    }

    public record StudentRow(int userId, String firstName, String lastName) {
    }

    /** Total marks earned / available for one finished attempt. */
    public record AttemptTotal(int regisId, int attemptNo, int userId, int examId,
                               BigDecimal score, BigDecimal max) {
    }

    /** Marks earned on one question of one finished attempt (0 when unanswered). */
    public record QuestionMark(int regisId, int attemptNo, int questionId, String content, String topic,
                               BigDecimal marks, BigDecimal earned) {
    }

    private StatsDAO() {
    }

    /** Every exam of the course (any status), only the fields needed to work out "completed". */
    public static List<ExamTime> examTimes(Connection c, int courseId) throws SQLException {
        List<ExamTime> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Status, Exam_date, Duration FROM EXAMINATION WHERE Course_ID = ?")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ExamTime(rs.getString(1), rs.getTimestamp(2), rs.getInt(3)));
                }
            }
        }
        return out;
    }

    public static List<ExamColumn> publishedExams(Connection c, int courseId) throws SQLException {
        List<ExamColumn> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Exam_ID, Title FROM EXAMINATION WHERE Course_ID = ? AND Status = 'PUBLISHED' " +
                "ORDER BY (Exam_date IS NULL), Exam_date, Exam_ID")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ExamColumn(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return out;
    }

    /** Students registered for at least one published exam of the course. */
    public static List<StudentRow> registeredStudents(Connection c, int courseId) throws SQLException {
        List<StudentRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT u.User_ID, u.FirstName, u.LastName FROM REGISTRATION r " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN `USER` u ON u.User_ID = r.User_ID " +
                "WHERE e.Course_ID = ? AND e.Status = 'PUBLISHED' " +
                "ORDER BY u.LastName, u.FirstName, u.User_ID")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StudentRow(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return out;
    }

    /** One row per finished attempt, ordered by RegisID then Attempt_no. */
    public static List<AttemptTotal> attemptTotals(Connection c, int courseId) throws SQLException {
        List<AttemptTotal> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.RegisID, a.Attempt_no, r.User_ID, e.Exam_ID, " +
                "COALESCE(SUM(ct.Marks), 0) AS max_score, COALESCE(SUM(ans.MarkAwarded), 0) AS score " +
                "FROM ATTEMPT a " +
                "JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN PAPER_QUESTION pq ON pq.PaperKey = a.PaperKey " +
                "JOIN EXAM_QUESTION_MARKS ct ON ct.Exam_ID = e.Exam_ID AND ct.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN ANSWER ans ON ans.Quest_ID = pq.Quest_ID AND ans.Attempt_no = a.Attempt_no " +
                "  AND ans.RegisID = a.RegisID " +
                "WHERE e.Course_ID = ? AND e.Status = 'PUBLISHED' AND a.EndTime IS NOT NULL " +
                "GROUP BY a.RegisID, a.Attempt_no, r.User_ID, e.Exam_ID " +
                "ORDER BY a.RegisID, a.Attempt_no")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AttemptTotal(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                            rs.getBigDecimal("score"), rs.getBigDecimal("max_score")));
                }
            }
        }
        return out;
    }

    /** One row per (finished attempt, question). */
    public static List<QuestionMark> questionMarks(Connection c, int courseId) throws SQLException {
        List<QuestionMark> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.RegisID, a.Attempt_no, q.Quest_ID, q.Content, t.Content AS topic, ct.Marks, " +
                "COALESCE(ans.MarkAwarded, 0) AS earned " +
                "FROM ATTEMPT a " +
                "JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN PAPER_QUESTION pq ON pq.PaperKey = a.PaperKey " +
                "JOIN EXAM_QUESTION_MARKS ct ON ct.Exam_ID = e.Exam_ID AND ct.Quest_ID = pq.Quest_ID " +
                "JOIN QUESTION q ON q.Quest_ID = pq.Quest_ID " +
                "JOIN TOPIC t ON t.Topic_ID = q.Topic_ID " +
                "LEFT JOIN ANSWER ans ON ans.Quest_ID = pq.Quest_ID AND ans.Attempt_no = a.Attempt_no " +
                "  AND ans.RegisID = a.RegisID " +
                "WHERE e.Course_ID = ? AND e.Status = 'PUBLISHED' AND a.EndTime IS NOT NULL")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QuestionMark(rs.getInt(1), rs.getInt(2), rs.getInt("Quest_ID"),
                            rs.getString("Content"), rs.getString("topic"),
                            rs.getBigDecimal("Marks"), rs.getBigDecimal("earned")));
                }
            }
        }
        return out;
    }
}
