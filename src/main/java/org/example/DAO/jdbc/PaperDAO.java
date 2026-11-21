package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Writing a paper: PAPER, PAPER_QUESTION (layout) and EXAM_QUESTION_MARKS (marks per exam and question).
 * (Reading is in {@link ExamDAO}.)
 *
 * The service still talks in (examId, paperId); this class maps that pair to the surrogate PaperKey.
 * Marks live per EXAM, not per paper: two papers of the same exam give the same marks to a question.
 */
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

    /** PaperKey of (examId, paperId), or 0 when the paper does not exist. */
    public static int paperKey(Connection c, int examId, int paperId) throws SQLException {
        return Jdbc.scalar(c, "SELECT PaperKey FROM PAPER WHERE Exam_ID = ? AND PaperID = ?", examId, paperId);
    }

    /** Removes every question from the paper (the paper row stays). */
    public static void clear(Connection c, int examId, int paperId) throws SQLException {
        Jdbc.exec(c, "DELETE pq FROM PAPER_QUESTION pq JOIN PAPER p ON p.PaperKey = pq.PaperKey " +
                "WHERE p.Exam_ID = ? AND p.PaperID = ?", examId, paperId);
    }

    /**
     * Places the questions on the paper. EXAM_QUESTION_MARKS must hold the marks first (a trigger
     * insists), so marks are upserted before the layout rows; marks of questions that no paper of
     * the exam uses any more are removed afterwards.
     */
    public static void addQuestions(Connection c, int examId, int paperId, List<Item> items)
            throws SQLException {
        int key = paperKey(c, examId, paperId);
        if (key == 0) {
            throw new SQLException("Paper " + paperId + " of exam " + examId + " does not exist.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO EXAM_QUESTION_MARKS (Exam_ID, Quest_ID, Marks) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE Marks = VALUES(Marks)")) {
            for (Item i : items) {
                ps.setInt(1, examId);
                ps.setInt(2, i.questionId());
                ps.setBigDecimal(3, i.marks());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO PAPER_QUESTION (PaperKey, Quest_ID, QuestionOrder) VALUES (?, ?, ?)")) {
            for (Item i : items) {
                ps.setInt(1, key);
                ps.setInt(2, i.questionId());
                ps.setInt(3, i.order());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        deleteUnusedMarks(c, examId);
    }

    /** Drops EXAM_QUESTION_MARKS rows of the exam whose question is on none of its papers. */
    public static void deleteUnusedMarks(Connection c, int examId) throws SQLException {
        Jdbc.exec(c, "DELETE eqm FROM EXAM_QUESTION_MARKS eqm WHERE eqm.Exam_ID = ? AND NOT EXISTS (" +
                "SELECT 1 FROM PAPER_QUESTION pq JOIN PAPER p ON p.PaperKey = pq.PaperKey " +
                "WHERE p.Exam_ID = eqm.Exam_ID AND pq.Quest_ID = eqm.Quest_ID)", examId);
    }
}
