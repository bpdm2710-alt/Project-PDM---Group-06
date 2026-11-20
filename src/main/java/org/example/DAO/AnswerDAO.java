package org.example.DAO;

import org.example.MODEL.Answer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    public void upsert(Answer answer) throws SQLException {
        String sql = "INSERT INTO ANSWER " +
                "(Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE AnswerText = ?, MarkAwarded = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, answer.getQuestId());
            ps.setInt(2, answer.getAttemptNo());
            ps.setInt(3, answer.getRegisId());
            ps.setString(4, answer.getAnswerText());
            ps.setBigDecimal(5, answer.getMarkAwarded());
            ps.setString(6, answer.getAnswerText());
            ps.setBigDecimal(7, answer.getMarkAwarded());
            ps.executeUpdate();
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

    public List<PaperQuestion> findPaperQuestions(int paperId, int examId)
            throws SQLException {
        String sql = "SELECT c.Quest_ID, c.Marks, c.QuestionOrder, " +
                "CASE WHEN m.Quest_ID IS NOT NULL THEN 'MCQ' " +
                "     WHEN sa.Quest_ID IS NOT NULL THEN 'SHORT_ANSWER' " +
                "     ELSE 'UNKNOWN' END AS QuestionType, " +
                "sa.Answer_key " +
                "FROM CONTAINS c " +
                "LEFT JOIN MCQ m ON m.Quest_ID = c.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = c.Quest_ID " +
                "WHERE c.PaperID = ? AND c.Exam_ID = ? " +
                "ORDER BY c.QuestionOrder";
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

    public PaperQuestion findPaperQuestion(int regisId, int attemptNo, int questId)
            throws SQLException {
        String sql = "SELECT c.Quest_ID, c.Marks, c.QuestionOrder, " +
                "CASE WHEN m.Quest_ID IS NOT NULL THEN 'MCQ' " +
                "     WHEN sa.Quest_ID IS NOT NULL THEN 'SHORT_ANSWER' " +
                "     ELSE 'UNKNOWN' END AS QuestionType, " +
                "sa.Answer_key " +
                "FROM ATTEMPT a " +
                "JOIN CONTAINS c " +
                "  ON c.PaperID = a.PaperID AND c.Exam_ID = a.Exam_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = c.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = c.Quest_ID " +
                "WHERE a.RegisID = ? AND a.Attempt_no = ? AND c.Quest_ID = ?";
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

    private String baseSelect() {
        return "SELECT a.Quest_ID, a.Attempt_no, a.RegisID, " +
                "a.AnswerText, a.MarkAwarded, " +
                "CASE WHEN m.Quest_ID IS NOT NULL THEN 1 ELSE 0 END AS IsMcq " +
                "FROM ANSWER a " +
                "LEFT JOIN MCQ m ON m.Quest_ID = a.Quest_ID " +
                "WHERE a.Attempt_no = ? AND a.RegisID = ?";
    }

    private Answer mapRow(ResultSet rs) throws SQLException {
        String text = rs.getString("AnswerText");
        Integer selectedOption = rs.getBoolean("IsMcq")
                ? parseOptionNo(text)
                : null;
        return new Answer(
                rs.getInt("Quest_ID"),
                rs.getInt("Attempt_no"),
                rs.getInt("RegisID"),
                text,
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

    private Integer parseOptionNo(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
