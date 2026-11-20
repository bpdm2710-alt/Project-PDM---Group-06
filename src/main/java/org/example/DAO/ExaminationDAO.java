package org.example.DAO;

import org.example.MODEL.Examination;
import java.sql.*; import java.util.*;

public class ExaminationDAO {
    private final Connection conn;
    public ExaminationDAO(Connection conn) { 
        this.conn = conn; }
    
    public List<Examination> getAllExaminations() {
        List<Examination> list = new ArrayList<>();
        String sql = "SELECT exam_id, course_id, title, exam_date, duration_min, pass_mark, max_attempts FROM examination";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Examination(rs.getInt("exam_id"), rs.getInt("course_id"), rs.getString("title"),
                        rs.getTimestamp("exam_date"), rs.getInt("duration_min"), rs.getDouble("pass_mark"), rs.getInt("max_attempts")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}