package org.example.DAO;

import org.example.MODEL.Score;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ScoreDAO {
    private static final String BASE_SELECT =
            "SELECT a.RegisID, a.Attempt_no, a.EndTime, e.Pass_mark, " +
            "COALESCE(SUM(eqm.Marks), 0) AS MaxScore, " +
            "COALESCE(SUM(ans.MarkAwarded), 0) AS TotalScore " +
            "FROM ATTEMPT a " +
            "JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
            "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
            // CONTAINS (20 tables) = PAPER_QUESTION (layout, by PaperKey) + EXAM_QUESTION_MARKS (marks per exam)
            "JOIN PAPER_QUESTION pq ON pq.PaperKey = a.PaperKey " +
            "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = e.Exam_ID AND eqm.Quest_ID = pq.Quest_ID " +
            "LEFT JOIN ANSWER ans " +
            "  ON ans.Quest_ID = pq.Quest_ID " +
            " AND ans.Attempt_no = a.Attempt_no " +
            " AND ans.RegisID = a.RegisID ";

    private final Connection conn;

    public ScoreDAO(Connection conn) {
        this.conn = conn;
    }

    public Score findByAttempt(int regisId, int attemptNo) throws SQLException {
        String sql = BASE_SELECT +
                "WHERE a.RegisID = ? AND a.Attempt_no = ? " +
                "AND a.EndTime IS NOT NULL " +
                "GROUP BY a.RegisID, a.Attempt_no, a.EndTime, e.Pass_mark";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Score> findByStudent(int userId) throws SQLException {
        String sql = BASE_SELECT +
                "WHERE r.User_ID = ? AND a.EndTime IS NOT NULL " +
                "GROUP BY a.RegisID, a.Attempt_no, a.EndTime, e.Pass_mark " +
                "ORDER BY a.EndTime, a.RegisID, a.Attempt_no";
        List<Score> scores = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scores.add(mapRow(rs));
                }
            }
        }
        return scores;
    }

    private Score mapRow(ResultSet rs) throws SQLException {
        return Score.calculate(
                rs.getInt("Attempt_no"),
                rs.getInt("RegisID"),
                rs.getBigDecimal("TotalScore"),
                rs.getBigDecimal("MaxScore"),
                rs.getBigDecimal("Pass_mark"),
                rs.getTimestamp("EndTime")
        );
    }
}
