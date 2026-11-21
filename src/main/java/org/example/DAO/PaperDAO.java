package org.example.DAO;

import org.example.MODEL.Paper;
import org.example.MODEL.Question;
import java.sql.*; import java.util.*;

public class PaperDAO {
    private final Connection conn; private final QuestionDAO questionDAO;

    public PaperDAO(Connection conn) {
        this.conn = conn;
        this.questionDAO = new QuestionDAO(conn);
    }

    /** PAPER(PaperKey, PaperID, Exam_ID): paper_code = PaperID (paper number inside the exam). */
    public List<Paper> getPapersByExam(int examId) {
        List<Paper> list = new ArrayList<>();
        String sql = "SELECT PaperKey, PaperID, Exam_ID FROM PAPER WHERE Exam_ID = ? ORDER BY PaperID";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new Paper(rs.getInt("PaperKey"), rs.getInt("PaperID"), rs.getInt("Exam_ID")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Questions of one paper in paper order. The old paper_contains table is PAPER_QUESTION now.
     * Columns are aliased to the names QuestionDAO.mapRow reads (question_id, instructor_id, ...).
     */
    public List<Question> getQuestionsByPaper(int paperKey) {
        List<Question> list = new ArrayList<>();
        String sql = "SELECT q.Quest_ID AS question_id, q.User_ID AS instructor_id, q.Topic_ID AS topic_id, " +
                "q.Content AS content, q.Difficulty AS difficulty, " +
                "CASE WHEN m.Quest_ID IS NOT NULL THEN '" + Question.TYPE_MCQ + "' " +
                "     ELSE '" + Question.TYPE_SHORT_ANSWER + "' END AS question_type, " +
                "0 AS allow_multiple, s.Answer_key AS model_answer " +
                "FROM PAPER_QUESTION pq " +
                "JOIN QUESTION q ON q.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = q.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER s ON s.Quest_ID = q.Quest_ID " +
                "WHERE pq.PaperKey = ? ORDER BY pq.QuestionOrder";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(QuestionDAO.mapRow(rs));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        questionDAO.loadOptions(list);
        return list;
    }
}