package org.example.DAO;

import org.example.MODEL.Examination;
import java.sql.*; import java.util.*;

public class ExaminationDAO {
    private final Connection conn;
    public ExaminationDAO(Connection conn) { this.conn = conn; }
    
    public List<Examination> getAllExaminations() {
        List<Examination> list = new ArrayList<>();
        String sql = "SELECT Exam_ID, Course_ID, Title, Exam_date, Duration AS durationMin, Pass_mark, Max_attempts, Status FROM examination";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Examination(rs.getInt("Exam_ID"), rs.getInt("Course_ID"), rs.getString("Title"),
                        rs.getTimestamp("Exam_date"), rs.getInt("durationMin"), rs.getDouble("Pass_mark"), 
                        rs.getInt("Max_attempts"), rs.getString("Status")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}