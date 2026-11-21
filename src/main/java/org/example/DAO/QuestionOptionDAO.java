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
        // Table OPTIONS(Option_no, Quest_ID, Op_Content, Bool_Correct); old question_option no longer exists.
        String sql = "SELECT Quest_ID, Option_no, Op_Content, Bool_Correct FROM OPTIONS WHERE Quest_ID = ? ORDER BY Option_no";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new QuestionOption(rs.getInt("Quest_ID"), rs.getInt("Option_no"),
                            rs.getString("Op_Content"), rs.getBoolean("Bool_Correct")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}