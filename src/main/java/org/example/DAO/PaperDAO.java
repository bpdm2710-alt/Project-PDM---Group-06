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
        String sql = "SELECT paper_key, paper_code, exam_id FROM paper WHERE exam_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new Paper(rs.getInt("paper_key"), rs.getInt("paper_code"), rs.getInt("exam_id")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Question> getQuestionsByPaper(int paperKey) {
        List<Question> list = new ArrayList<>();
        String sql = QuestionDAO.SELECT_BASE + " JOIN paper_contains pc ON q.question_id = pc.question_id WHERE pc.paper_key = ? ORDER BY pc.question_order";
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