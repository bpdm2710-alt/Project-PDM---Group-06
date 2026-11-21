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

    public List<Paper> getPapersByExam(int examId) {
        List<Paper> list = new ArrayList<>();
        String sql = "SELECT PaperKey, PaperID AS paperId, Exam_ID FROM paper WHERE Exam_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Paper(rs.getInt("PaperKey"), rs.getInt("paperId"), rs.getInt("Exam_ID")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Question> getQuestionsByPaper(int paperKey) {
        List<Question> list = new ArrayList<>();
        String sql = QuestionDAO.SELECT_BASE + " JOIN paper_question pq ON q.Quest_ID = pq.Quest_ID WHERE pq.PaperKey = ? ORDER BY pq.QuestionOrder";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(QuestionDAO.mapRow(rs));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        questionDAO.loadOptions(list);
        return list;
    }
}