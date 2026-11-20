package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** Writing a paper: PAPER and CONTAINS. (Reading is in {@link ExamDAO}.) */
public final class PaperDAO {

    /** One question of a paper; {@code order} is already the final 1..n position. */
    public record Item(int questionId, int order, BigDecimal marks) {
    }

    private PaperDAO() {
    }

    /** The next free PaperID of an exam (1 for the first paper). */
    public static int nextPaperId(Connection c, int examId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COALESCE(MAX(PaperID), 0) + 1 FROM PAPER WHERE Exam_ID = ?", examId);
    }

    public static void insert(Connection c, int examId, int paperId) throws SQLException {
        Jdbc.exec(c, "INSERT INTO PAPER (PaperID, Exam_ID) VALUES (?, ?)", paperId, examId);
    }

    public static boolean exists(Connection c, int examId, int paperId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM PAPER WHERE Exam_ID = ? AND PaperID = ?",
                examId, paperId) > 0;
    }

    /** Removes every question from the paper (the paper row stays). */
    public static void clear(Connection c, int examId, int paperId) throws SQLException {
        Jdbc.exec(c, "DELETE FROM CONTAINS WHERE Exam_ID = ? AND PaperID = ?", examId, paperId);
    }

    public static void addQuestions(Connection c, int examId, int paperId, List<Item> items)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO CONTAINS (Quest_ID, PaperID, Exam_ID, QuestionOrder, Marks) VALUES (?, ?, ?, ?, ?)")) {
            for (Item i : items) {
                ps.setInt(1, i.questionId());
                ps.setInt(2, paperId);
                ps.setInt(3, examId);
                ps.setInt(4, i.order());
                ps.setBigDecimal(5, i.marks());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
