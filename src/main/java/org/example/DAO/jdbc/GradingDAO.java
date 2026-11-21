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
 * Manual grading of short answers (BR-42, same rule as sp_finalize_attempt): when an attempt is
 * submitted, a non-blank short answer that does not match SHORT_ANSWER.Answer_key keeps
 * ANSWER.MarkAwarded = NULL. Those rows are the instructor's "pending" list.
 */
public final class GradingDAO {

    /** One short answer waiting for a mark. */
    public record PendingRow(int regisId, int attemptNo, int questionId, int studentId, String studentName,
                             int examId, String examTitle, Timestamp submittedAt, String question,
                             String answerText, String answerKey, BigDecimal maxMarks) {
    }

    /** What is needed to validate one manual mark. {@code mark} is the current value (null = pending). */
    public record Target(int courseId, int examId, BigDecimal maxMarks, BigDecimal mark,
                         Timestamp endTime, boolean shortAnswer) {
    }

    private GradingDAO() {
    }

    private static final String PENDING_SQL =
            "SELECT an.RegisID, an.Attempt_no, an.Quest_ID, r.User_ID, " +
            "       CONCAT(u.FirstName, ' ', u.LastName) AS StudentName, e.Exam_ID, e.Title, a.EndTime, " +
            "       q.Content, an.AnswerText, sa.Answer_key, eqm.Marks " +
            "FROM ANSWER an " +
            "JOIN ATTEMPT a ON a.RegisID = an.RegisID AND a.Attempt_no = an.Attempt_no " +
            "JOIN REGISTRATION r ON r.RegisID = an.RegisID " +
            "JOIN `USER` u ON u.User_ID = r.User_ID " +
            "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
            "JOIN QUESTION q ON q.Quest_ID = an.Quest_ID " +
            "JOIN SHORT_ANSWER sa ON sa.Quest_ID = an.Quest_ID " +
            "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = e.Exam_ID AND eqm.Quest_ID = an.Quest_ID " +
            "WHERE e.Course_ID = ? AND a.EndTime IS NOT NULL AND an.MarkAwarded IS NULL " +
            "ORDER BY a.EndTime, an.RegisID, an.Attempt_no, an.Quest_ID";

    /** Submitted short answers of the course that still have no mark, oldest submission first. */
    public static List<PendingRow> pendingForCourse(Connection c, int courseId) throws SQLException {
        List<PendingRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(PENDING_SQL)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PendingRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                            rs.getString(5), rs.getInt(6), rs.getString(7), rs.getTimestamp(8),
                            rs.getString(9), rs.getString(10), rs.getString(11), rs.getBigDecimal(12)));
                }
            }
        }
        return out;
    }

    public static int countPendingForCourse(Connection c, int courseId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM ANSWER an " +
                "JOIN ATTEMPT a ON a.RegisID = an.RegisID AND a.Attempt_no = an.Attempt_no " +
                "JOIN REGISTRATION r ON r.RegisID = an.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE e.Course_ID = ? AND a.EndTime IS NOT NULL AND an.MarkAwarded IS NULL", courseId);
    }

    /** Locks the ANSWER row. @return null when it does not exist. */
    public static Target findForUpdate(Connection c, int regisId, int attemptNo, int questionId)
            throws SQLException {
        if (Jdbc.scalar(c, "SELECT Quest_ID FROM ANSWER " +
                "WHERE RegisID = ? AND Attempt_no = ? AND Quest_ID = ? FOR UPDATE",
                regisId, attemptNo, questionId) == 0) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT e.Course_ID, e.Exam_ID, eqm.Marks, an.MarkAwarded, a.EndTime, (sa.Quest_ID IS NOT NULL) " +
                "FROM ANSWER an " +
                "JOIN ATTEMPT a ON a.RegisID = an.RegisID AND a.Attempt_no = an.Attempt_no " +
                "JOIN REGISTRATION r ON r.RegisID = an.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = e.Exam_ID AND eqm.Quest_ID = an.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = an.Quest_ID " +
                "WHERE an.RegisID = ? AND an.Attempt_no = ? AND an.Quest_ID = ?")) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            ps.setInt(3, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Target(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getTimestamp(5), rs.getBoolean(6));
            }
        }
    }

    /** Writes the manual mark (the ANSWER trigger re-checks 0 <= mark <= EXAM_QUESTION_MARKS.Marks). */
    public static void setMark(Connection c, int regisId, int attemptNo, int questionId, BigDecimal mark)
            throws SQLException {
        Jdbc.exec(c, "UPDATE ANSWER SET MarkAwarded = ? WHERE RegisID = ? AND Attempt_no = ? AND Quest_ID = ?",
                mark, regisId, attemptNo, questionId);
    }
}
