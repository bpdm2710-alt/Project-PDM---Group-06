package org.example.DAO;

import org.example.MODEL.Answer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ANSWER / SELECT_TABLE for the 21-table schema.
 *  - the old CONTAINS table is split into EXAM_QUESTION_MARKS (Marks per exam + question)
 *    and PAPER_QUESTION (layout: PaperKey + QuestionOrder);
 *  - ATTEMPT points to its paper through PaperKey (no more PaperID + Exam_ID);
 *  - MCQ: ANSWER.AnswerText must be NULL (trigger), the chosen option is stored in SELECT_TABLE.
 */
public class AnswerDAO {
    public static final String TYPE_MCQ = "MCQ";
    public static final String TYPE_SHORT_ANSWER = "SHORT_ANSWER";

    private final Connection conn;

    public AnswerDAO(Connection conn) {
        this.conn = conn;
    }

    public record PaperQuestion(int questId, BigDecimal marks, int questionOrder,
                                String type, String answerKey) {
    }

    /**
     * Inserts or updates one answer. For an MCQ (selectedOptionNo != null) AnswerText is stored as NULL
     * and the option goes to SELECT_TABLE (ANSWER row first, because SELECT_TABLE references it).
     */
    public void upsert(Answer answer) throws SQLException {
        boolean mcq = answer.getSelectedOptionNo() != null;
        String text = mcq ? null : answer.getAnswerText();
        String sql = "INSERT INTO ANSWER " +
                "(Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE AnswerText = ?, MarkAwarded = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, answer.getQuestId());
            ps.setInt(2, answer.getAttemptNo());
            ps.setInt(3, answer.getRegisId());
            ps.setString(4, text);
            ps.setBigDecimal(5, answer.getMarkAwarded());
            ps.setString(6, text);
            ps.setBigDecimal(7, answer.getMarkAwarded());
            ps.executeUpdate();
        }
        if (mcq) {
            String sel = "INSERT INTO SELECT_TABLE (Option_no, Quest_ID, Attempt_no, RegisID) " +
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE Option_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                ps.setInt(1, answer.getSelectedOptionNo());
                ps.setInt(2, answer.getQuestId());
                ps.setInt(3, answer.getAttemptNo());
                ps.setInt(4, answer.getRegisId());
                ps.setInt(5, answer.getSelectedOptionNo());
                ps.executeUpdate();
            }
        }
    }

    public Answer findByAttemptAndQuestion(int regisId, int attemptNo, int questId)
            throws SQLException {
        String sql = baseSelect() +
                " AND a.Quest_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attemptNo);
            ps.setInt(2, regisId);
            ps.setInt(3, questId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Answer> findByAttempt(int regisId, int attemptNo) throws SQLException {
        String sql = baseSelect() + " ORDER BY a.Quest_ID";
        List<Answer> answers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attemptNo);
            ps.setInt(2, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    answers.add(mapRow(rs));
                }
            }
        }
        return answers;
    }

    public int countByAttempt(int regisId, int attemptNo) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ANSWER " +
                "WHERE Attempt_no = ? AND RegisID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attemptNo);
            ps.setInt(2, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void updateMark(int regisId, int attemptNo, int questId,
                           BigDecimal mark) throws SQLException {
        String sql = "UPDATE ANSWER SET MarkAwarded = ? " +
                "WHERE Quest_ID = ? AND Attempt_no = ? AND RegisID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, mark);
            ps.setInt(2, questId);
            ps.setInt(3, attemptNo);
            ps.setInt(4, regisId);
            ps.executeUpdate();
        }
    }

    /** Questions of paper {@code paperId} of exam {@code examId}, in paper order. */
    public List<PaperQuestion> findPaperQuestions(int paperId, int examId)
            throws SQLException {
        String sql = "SELECT pq.Quest_ID, eqm.Marks, pq.QuestionOrder, " +
                "CASE WHEN m.Quest_ID IS NOT NULL THEN 'MCQ' " +
                "     WHEN sa.Quest_ID IS NOT NULL THEN 'SHORT_ANSWER' " +
                "     ELSE 'UNKNOWN' END AS QuestionType, " +
                "sa.Answer_key " +
                "FROM PAPER p " +
                "JOIN PAPER_QUESTION pq ON pq.PaperKey = p.PaperKey " +
                "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = p.Exam_ID AND eqm.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = pq.Quest_ID " +
                "WHERE p.PaperID = ? AND p.Exam_ID = ? " +
                "ORDER BY pq.QuestionOrder";
        List<PaperQuestion> questions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperId);
            ps.setInt(2, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapPaperQuestion(rs));
                }
            }
        }
        return questions;
    }

    /** One question of the paper assigned to the attempt, or null when it is not on that paper. */
    public PaperQuestion findPaperQuestion(int regisId, int attemptNo, int questId)
            throws SQLException {
        String sql = "SELECT pq.Quest_ID, eqm.Marks, pq.QuestionOrder, " +
                "CASE WHEN m.Quest_ID IS NOT NULL THEN 'MCQ' " +
                "     WHEN sa.Quest_ID IS NOT NULL THEN 'SHORT_ANSWER' " +
                "     ELSE 'UNKNOWN' END AS QuestionType, " +
                "sa.Answer_key " +
                "FROM ATTEMPT a " +
                "JOIN PAPER p ON p.PaperKey = a.PaperKey " +
                "JOIN PAPER_QUESTION pq ON pq.PaperKey = a.PaperKey " +
                "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = p.Exam_ID AND eqm.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = pq.Quest_ID " +
                "WHERE a.RegisID = ? AND a.Attempt_no = ? AND pq.Quest_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            ps.setInt(3, questId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapPaperQuestion(rs) : null;
            }
        }
    }

    public boolean optionExists(int questId, int optionNo) throws SQLException {
        String sql = "SELECT 1 FROM OPTIONS WHERE Quest_ID = ? AND Option_no = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questId);
            ps.setInt(2, optionNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isCorrectOption(int questId, int optionNo) throws SQLException {
        String sql = "SELECT Bool_Correct FROM OPTIONS " +
                "WHERE Quest_ID = ? AND Option_no = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questId);
            ps.setInt(2, optionNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /** The chosen MCQ option now comes from SELECT_TABLE (AnswerText is NULL for MCQ). */
    private String baseSelect() {
        return "SELECT a.Quest_ID, a.Attempt_no, a.RegisID, " +
                "a.AnswerText, a.MarkAwarded, st.Option_no AS SelectedOption " +
                "FROM ANSWER a " +
                "LEFT JOIN SELECT_TABLE st ON st.Quest_ID = a.Quest_ID " +
                "  AND st.Attempt_no = a.Attempt_no AND st.RegisID = a.RegisID " +
                "WHERE a.Attempt_no = ? AND a.RegisID = ?";
    }

    private Answer mapRow(ResultSet rs) throws SQLException {
        int option = rs.getInt("SelectedOption");
        Integer selectedOption = rs.wasNull() ? null : option;
        return new Answer(
                rs.getInt("Quest_ID"),
                rs.getInt("Attempt_no"),
                rs.getInt("RegisID"),
                rs.getString("AnswerText"),
                rs.getBigDecimal("MarkAwarded"),
                selectedOption
        );
    }

    private PaperQuestion mapPaperQuestion(ResultSet rs) throws SQLException {
        return new PaperQuestion(
                rs.getInt("Quest_ID"),
                rs.getBigDecimal("Marks"),
                rs.getInt("QuestionOrder"),
                rs.getString("QuestionType"),
                rs.getString("Answer_key")
        );
    }
}