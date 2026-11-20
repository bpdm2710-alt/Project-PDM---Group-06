package org.example.DAO;

import org.example.MODEL.QuestionOption;
import java.sql.*; 
import java.util.*;

public class QuestionOptionDAO {
    private final Connection conn;
    public QuestionOptionDAO(Connection conn) { 
        this.conn = conn; }

    public List<QuestionOption> getOptionsByQuestion(int questionId) {
        List<QuestionOption> list = new ArrayList<>();
        String sql = "SELECT question_id, option_no, content, is_correct FROM question_option WHERE question_id = ? ORDER BY option_no";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new QuestionOption(rs.getInt("question_id"), rs.getInt("option_no"),
                            rs.getString("content"), rs.getBoolean("is_correct")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}