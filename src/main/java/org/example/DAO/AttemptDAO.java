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

/**
 * ATTEMPT for the 21-table schema (instance-style DAO used by StudentResultService).
 *  - ATTEMPT no longer stores PaperID + Exam_ID: it points to its paper with PaperKey, and
 *    PAPER(PaperKey, PaperID, Exam_ID) gives back PaperID / Exam_ID for the Attempt model;
 *  - EndTime IS NULL = in progress, NOT NULL = submitted (no more "EndTime = StartTime").
 * Not to be confused with org.example.DAO.jdbc.AttemptDAO (static DAO used by StudentServiceImpl).
 */
public class AttemptDAO {
    private final Connection conn;

    /** Columns read by mapRow: PaperID / Exam_ID come from PAPER through PaperKey. */
    private static final String SELECT_ATTEMPT =
            "SELECT a.Attempt_no, a.RegisID, p.PaperID, p.Exam_ID, a.StartTime, a.EndTime " +
                    "FROM ATTEMPT a JOIN PAPER p ON p.PaperKey = a.PaperKey ";

    public AttemptDAO(Connection conn) {
        this.conn = conn;
    }

    public record RegistrationInfo(int userId, int examId, int maxAttempts) {
    }

    /**
     * Inserts the attempt. The model carries (PaperID, Exam_ID); the matching PaperKey is looked up
     * in PAPER. Leave endTime null for an attempt that is still in progress.
     */
    public void insert(Attempt attempt) throws SQLException {
        Integer paperKey = findPaperKey(attempt.getPaperId(), attempt.getExamId());
        if (paperKey == null) {
            throw new SQLException("Paper " + attempt.getPaperId() + " of exam " + attempt.getExamId()
                    + " does not exist.");
        }
        String sql = "INSERT INTO ATTEMPT " +
                "(Attempt_no, RegisID, StartTime, EndTime, PaperKey) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempt.getAttemptNo());
            ps.setInt(2, attempt.getRegisId());
            ps.setTimestamp(3, attempt.getStartTime());
            ps.setTimestamp(4, attempt.getEndTime());
            ps.setInt(5, paperKey);
            ps.executeUpdate();
        }
    }

    public Attempt findById(int regisId, int attemptNo) throws SQLException {
        String sql = SELECT_ATTEMPT + "WHERE a.RegisID = ? AND a.Attempt_no = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Attempt> findByRegistration(int regisId) throws SQLException {
        String sql = SELECT_ATTEMPT + "WHERE a.RegisID = ? ORDER BY a.Attempt_no";
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
        String sql = SELECT_ATTEMPT +
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

    /** Locks the ATTEMPT row (only that row) until the transaction ends, then reads it. */
    public Attempt lockById(int regisId, int attemptNo) throws SQLException {
        String lock = "SELECT Attempt_no FROM ATTEMPT WHERE RegisID = ? AND Attempt_no = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(lock)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
            }
        }
        return findById(regisId, attemptNo);
    }

    /** Locks the REGISTRATION row (only that row) until the transaction ends, then reads it. */
    public RegistrationInfo lockRegistration(int regisId) throws SQLException {
        String lock = "SELECT RegisID FROM REGISTRATION WHERE RegisID = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(lock)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
            }
        }
        String sql = "SELECT r.User_ID, r.Exam_ID, e.Max_attempts " +
                "FROM REGISTRATION r " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE r.RegisID = ?";
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
        return findPaperKey(paperId, examId) != null;
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

    /**
     * Submits the attempt: sets EndTime only while it is still in progress (EndTime IS NULL).
     * EndTime is never earlier than StartTime (CHECK chk_attempt_time_order).
     * @return 1 when submitted now, 0 when it was already submitted (or does not exist).
     */
    public int markSubmitted(int regisId, int attemptNo, Timestamp endTime)
            throws SQLException {
        String sql = "UPDATE ATTEMPT SET EndTime = GREATEST(?, StartTime) " +
                "WHERE RegisID = ? AND Attempt_no = ? AND EndTime IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, endTime);
            ps.setInt(2, regisId);
            ps.setInt(3, attemptNo);
            return ps.executeUpdate();
        }
    }

    /** PaperKey of paper {@code paperId} of exam {@code examId}, or null when there is none. */
    private Integer findPaperKey(int paperId, int examId) throws SQLException {
        String sql = "SELECT PaperKey FROM PAPER WHERE PaperID = ? AND Exam_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperId);
            ps.setInt(2, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
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