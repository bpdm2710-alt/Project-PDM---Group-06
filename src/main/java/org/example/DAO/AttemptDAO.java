package org.example.DAO;

import org.example.MODEL.Attempt;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AttemptDAO {
    private final Connection conn;

    public AttemptDAO(Connection conn) {
        this.conn = conn;
    }

    public record RegistrationInfo(int userId, int examId, int maxAttempts) {
    }

    public void insert(Attempt attempt) throws SQLException {
        String sql = "INSERT INTO ATTEMPT " +
                "(Attempt_no, RegisID, PaperID, Exam_ID, StartTime, EndTime) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempt.getAttemptNo());
            ps.setInt(2, attempt.getRegisId());
            ps.setInt(3, attempt.getPaperId());
            ps.setInt(4, attempt.getExamId());
            ps.setTimestamp(5, attempt.getStartTime());
            ps.setTimestamp(6, attempt.getEndTime());
            ps.executeUpdate();
        }
    }

    public Attempt findById(int regisId, int attemptNo) throws SQLException {
        String sql = "SELECT Attempt_no, RegisID, PaperID, Exam_ID, StartTime, EndTime " +
                "FROM ATTEMPT WHERE RegisID = ? AND Attempt_no = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Attempt> findByRegistration(int regisId) throws SQLException {
        String sql = "SELECT Attempt_no, RegisID, PaperID, Exam_ID, StartTime, EndTime " +
                "FROM ATTEMPT WHERE RegisID = ? ORDER BY Attempt_no";
        List<Attempt> attempts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    attempts.add(mapRow(rs));
                }
            }
        }
        return attempts;
    }

    public List<Attempt> findByStudent(int userId) throws SQLException {
        String sql = "SELECT a.Attempt_no, a.RegisID, a.PaperID, a.Exam_ID, " +
                "a.StartTime, a.EndTime " +
                "FROM ATTEMPT a " +
                "JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "WHERE r.User_ID = ? " +
                "ORDER BY a.RegisID, a.Attempt_no";
        List<Attempt> attempts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    attempts.add(mapRow(rs));
                }
            }
        }
        return attempts;
    }

    public int countByRegistration(int regisId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ATTEMPT WHERE RegisID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int nextAttemptNo(int regisId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(Attempt_no), 0) + 1 " +
                "FROM ATTEMPT WHERE RegisID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    public Attempt lockById(int regisId, int attemptNo) throws SQLException {
        String sql = "SELECT Attempt_no, RegisID, PaperID, Exam_ID, StartTime, EndTime " +
                "FROM ATTEMPT WHERE RegisID = ? AND Attempt_no = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public RegistrationInfo lockRegistration(int regisId) throws SQLException {
        String sql = "SELECT r.User_ID, r.Exam_ID, e.Max_attempts " +
                "FROM REGISTRATION r " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE r.RegisID = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new RegistrationInfo(
                        rs.getInt("User_ID"),
                        rs.getInt("Exam_ID"),
                        rs.getInt("Max_attempts")
                );
            }
        }
    }

    public boolean paperBelongsToExam(int paperId, int examId) throws SQLException {
        String sql = "SELECT 1 FROM PAPER WHERE PaperID = ? AND Exam_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperId);
            ps.setInt(2, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public BigDecimal findPassMark(int regisId) throws SQLException {
        String sql = "SELECT e.Pass_mark " +
                "FROM REGISTRATION r " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE r.RegisID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("Pass_mark") : null;
            }
        }
    }

    public int markSubmitted(int regisId, int attemptNo, Timestamp endTime)
            throws SQLException {
        String sql = "UPDATE ATTEMPT SET EndTime = ? " +
                "WHERE RegisID = ? AND Attempt_no = ? AND EndTime = StartTime";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, endTime);
            ps.setInt(2, regisId);
            ps.setInt(3, attemptNo);
            return ps.executeUpdate();
        }
    }

    private Attempt mapRow(ResultSet rs) throws SQLException {
        return new Attempt(
                rs.getInt("Attempt_no"),
                rs.getInt("RegisID"),
                rs.getInt("PaperID"),
                rs.getInt("Exam_ID"),
                rs.getTimestamp("StartTime"),
                rs.getTimestamp("EndTime")
        );
    }
}
