package org.example.DAO;

import org.example.MODEL.QuestionOption;
import java.sql.*; 
import java.util.*;

public class QuestionOptionDAO {
    private final Connection conn;
    public QuestionOptionDAO(Connection conn) { this.conn = conn; }

    public List<QuestionOption> getOptionsByQuestion(int questionId) {
        List<QuestionOption> list = new ArrayList<>();
        String sql = "SELECT Quest_ID AS questionId, Option_no, Op_Content AS content, Bool_Correct AS isCorrect FROM options WHERE Quest_ID = ? ORDER BY Option_no";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new QuestionOption(rs.getInt("questionId"), rs.getInt("Option_no"),
                            rs.getString("content"), rs.getBoolean("isCorrect")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}