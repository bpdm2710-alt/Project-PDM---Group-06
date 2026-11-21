package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Taking an exam: ATTEMPT, ANSWER and SELECT_TABLE, plus the score queries behind the Results screen.
 *
 * Conventions of the 21-table schema:
 *  - ATTEMPT.EndTime IS NULL = in progress, NOT NULL = submitted;
 *  - a paper is identified by its surrogate PaperKey;
 *  - MCQ answers: ANSWER row with AnswerText NULL + one SELECT_TABLE row holding the chosen Option_no;
 *    short answers: ANSWER.AnswerText;
 *  - ANSWER.MarkAwarded IS NULL = not graded yet; grading happens when the attempt is submitted;
 *  - marks per question come from EXAM_QUESTION_MARKS (per exam), the layout from PAPER_QUESTION.
 */
public final class AttemptDAO {

    public record RegInfo(int regisId, int ownerId, int examId, String title, String courseName,
                          Timestamp date, int duration, int maxAttempts, String status) {
    }

    public record InProgress(int attemptNo, Timestamp start, int paperKey) {
    }

    /** An unfinished attempt together with the length of its exam (used to find the expired ones). */
    public record OpenAttempt(int regisId, int attemptNo, Timestamp start, int duration) {
    }

    /** {@code end} is null while the attempt is in progress. */
    public record Row(int paperKey, int examId, Timestamp start, Timestamp end, int ownerId, int duration) {
        public boolean inProgress() {
            return end == null;
        }
    }

    public record QuestionRow(int questionId, int order, BigDecimal marks, String content, boolean mcq) {
    }

    public record OptionRow(int optionNo, String content) {
    }

    /** What the student saved for one question; exactly one of the two fields is set. */
    public record SavedAnswer(int questionId, Integer optionNo, String text) {
    }

    /** Everything needed to grade one question of an attempt (answered or not). */
    public record GradeRow(int questionId, BigDecimal marks, boolean mcq, String text, String answerKey,
                           Integer selectedOption, boolean selectedIsCorrect) {
    }

    /** Totals of one finished attempt; {@code ungraded} = questions whose mark is still NULL. */
    public record ResultRow(int regisId, int attemptNo, Timestamp end, int examId, String title,
                            String courseName, BigDecimal passMark, BigDecimal score, BigDecimal max,
                            int ungraded) {
    }

    private AttemptDAO() {
    }

    // ---- Registration / attempt rows --------------------------------------

    /** Locks the registration row (only that row). @return null when it does not exist. */
    public static RegInfo findRegistrationForUpdate(Connection c, int regisId) throws SQLException {
        if (Jdbc.scalar(c, "SELECT RegisID FROM REGISTRATION WHERE RegisID = ? FOR UPDATE", regisId) == 0) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.User_ID, e.Exam_ID, e.Title, e.Exam_date, e.Duration, e.Max_attempts, co.CName, e.Status " +
                "FROM REGISTRATION r JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN COURSE co ON co.Course_ID = e.Course_ID WHERE r.RegisID = ?")) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new RegInfo(regisId, rs.getInt("User_ID"), rs.getInt("Exam_ID"), rs.getString("Title"),
                        rs.getString("CName"), rs.getTimestamp("Exam_date"), rs.getInt("Duration"),
                        rs.getInt("Max_attempts"), rs.getString("Status"));
            }
        }
    }

    /** The newest unfinished attempt of the registration, or null. */
    public static InProgress findInProgress(Connection c, int regisId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Attempt_no, StartTime, PaperKey FROM ATTEMPT " +
                "WHERE RegisID = ? AND EndTime IS NULL ORDER BY Attempt_no DESC LIMIT 1")) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new InProgress(rs.getInt(1), rs.getTimestamp(2), rs.getInt(3)) : null;
            }
        }
    }

    /**
     * Every unfinished attempt (EndTime IS NULL) of the student, oldest first. The caller decides
     * which of them have run out of time (StartTime + Duration).
     */
    public static List<OpenAttempt> openAttemptsOfStudent(Connection c, int studentId) throws SQLException {
        List<OpenAttempt> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.RegisID, a.Attempt_no, a.StartTime, e.Duration " +
                "FROM ATTEMPT a JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE r.User_ID = ? AND a.EndTime IS NULL ORDER BY a.StartTime, a.RegisID, a.Attempt_no")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OpenAttempt(rs.getInt(1), rs.getInt(2), rs.getTimestamp(3), rs.getInt(4)));
                }
            }
        }
        return out;
    }

    public static int countAttempts(Connection c, int regisId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM ATTEMPT WHERE RegisID = ?", regisId);
    }

    public static int highestAttemptNo(Connection c, int regisId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COALESCE(MAX(Attempt_no), 0) FROM ATTEMPT WHERE RegisID = ?", regisId);
    }

    /**
     * Chooses the paper for a NEW attempt when the exam has several papers:
     *  1. only papers that have at least one question (an empty paper cannot be assigned);
     *  2. among them, the papers this registration has used the fewest times (a retake gets a
     *     different paper while one is left);
     *  3. ties are broken by rotating over the exam: the k-th attempt of the exam (all students)
     *     starts looking at paper number k mod n, so neighbours usually get different papers.
     * Marks stay per exam (EXAM_QUESTION_MARKS), so every paper is graded with the same marks per question.
     * @return null when the exam has no paper with questions.
     */
    public static Integer pickPaperKey(Connection c, int examId, int regisId) throws SQLException {
        List<int[]> papers = new ArrayList<>(); // {PaperKey, times used by this registration}
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.PaperKey, (SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = ? AND a.PaperKey = p.PaperKey) " +
                "FROM PAPER p WHERE p.Exam_ID = ? " +
                "AND EXISTS (SELECT 1 FROM PAPER_QUESTION pq WHERE pq.PaperKey = p.PaperKey) " +
                "ORDER BY p.PaperID")) {
            ps.setInt(1, regisId);
            ps.setInt(2, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    papers.add(new int[]{rs.getInt(1), rs.getInt(2)});
                }
            }
        }
        if (papers.isEmpty()) {
            return null;
        }
        int n = papers.size();
        int start = Jdbc.scalar(c, "SELECT COUNT(*) FROM ATTEMPT a JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "WHERE r.Exam_ID = ?", examId) % n;
        int bestUse = Integer.MAX_VALUE;
        for (int[] p : papers) {
            bestUse = Math.min(bestUse, p[1]);
        }
        for (int i = 0; i < n; i++) {
            int[] p = papers.get((start + i) % n);
            if (p[1] == bestUse) {
                return p[0];
            }
        }
        return papers.get(start)[0];
    }

    /** Starts an attempt (EndTime stays NULL). Triggers re-check the attempt cap and the paper. */
    public static void insert(Connection c, int attemptNo, int regisId, int paperKey, Timestamp start)
            throws SQLException {
        Jdbc.exec(c, "INSERT INTO ATTEMPT (Attempt_no, RegisID, StartTime, EndTime, PaperKey) " +
                "VALUES (?, ?, ?, NULL, ?)", attemptNo, regisId, start, paperKey);
    }

    /**
     * @param lock true = lock the ATTEMPT row (only that row) until the transaction ends
     * @return null when the attempt does not exist.
     */
    public static Row find(Connection c, int regisId, int attemptNo, boolean lock) throws SQLException {
        if (lock && Jdbc.scalar(c, "SELECT Attempt_no FROM ATTEMPT " +
                "WHERE RegisID = ? AND Attempt_no = ? FOR UPDATE", regisId, attemptNo) == 0) {
            return null;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.PaperKey, r.Exam_ID, a.StartTime, a.EndTime, r.User_ID, e.Duration " +
                "FROM ATTEMPT a JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE a.RegisID = ? AND a.Attempt_no = ?")) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Row(rs.getInt(1), rs.getInt(2), rs.getTimestamp(3), rs.getTimestamp(4),
                        rs.getInt(5), rs.getInt(6));
            }
        }
    }

    /** Sets EndTime (never earlier than StartTime) if the attempt is still open. */
    public static void close(Connection c, int regisId, int attemptNo, Timestamp end) throws SQLException {
        Jdbc.exec(c, "UPDATE ATTEMPT SET EndTime = GREATEST(?, StartTime) " +
                "WHERE RegisID = ? AND Attempt_no = ? AND EndTime IS NULL", end, regisId, attemptNo);
    }

    // ---- Paper content shown to the student -------------------------------

    public static List<QuestionRow> paperQuestions(Connection c, int paperKey, int examId) throws SQLException {
        List<QuestionRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT pq.Quest_ID, pq.QuestionOrder, eqm.Marks, q.Content, (m.Quest_ID IS NOT NULL) AS is_mcq " +
                "FROM PAPER_QUESTION pq " +
                "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = ? AND eqm.Quest_ID = pq.Quest_ID " +
                "JOIN QUESTION q ON q.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = pq.Quest_ID " +
                "WHERE pq.PaperKey = ? ORDER BY pq.QuestionOrder, pq.Quest_ID")) {
            ps.setInt(1, examId);
            ps.setInt(2, paperKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QuestionRow(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3),
                            rs.getString(4), rs.getBoolean(5)));
                }
            }
        }
        return out;
    }

    /** Options of the given MCQs in display order. Never selects Bool_Correct. */
    public static Map<Integer, List<OptionRow>> options(Connection c, List<Integer> mcqIds) throws SQLException {
        Map<Integer, List<OptionRow>> out = new HashMap<>();
        if (mcqIds.isEmpty()) {
            return out;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Quest_ID, Option_no, Op_Content FROM OPTIONS WHERE Quest_ID IN (" +
                Jdbc.placeholders(mcqIds.size()) + ") ORDER BY Quest_ID, Option_no")) {
            Jdbc.bind(ps, mcqIds.toArray(), 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new OptionRow(rs.getInt(2), rs.getString(3)));
                }
            }
        }
        return out;
    }

    // ---- Saving answers ----------------------------------------------------

    /** @return null when the question is not on the paper, else true for MCQ / false for short answer. */
    public static Boolean questionKindOnPaper(Connection c, int paperKey, int questionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT (m.Quest_ID IS NOT NULL) FROM PAPER_QUESTION pq " +
                "LEFT JOIN MCQ m ON m.Quest_ID = pq.Quest_ID WHERE pq.PaperKey = ? AND pq.Quest_ID = ?")) {
            ps.setInt(1, paperKey);
            ps.setInt(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBoolean(1) : null;
            }
        }
    }

    public static boolean optionExists(Connection c, int questionId, int optionNo) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM OPTIONS WHERE Quest_ID = ? AND Option_no = ?",
                questionId, optionNo) > 0;
    }

    /** Idempotent: records (or changes) the chosen option of a single-select MCQ. */
    public static void saveMcq(Connection c, int regisId, int attemptNo, int questionId, int optionNo)
            throws SQLException {
        Jdbc.exec(c, "INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) " +
                "VALUES (?, ?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE AnswerText = NULL", questionId, attemptNo, regisId);
        Jdbc.exec(c, "INSERT INTO SELECT_TABLE (Option_no, Quest_ID, Attempt_no, RegisID) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE Option_no = ?", optionNo, questionId, attemptNo, regisId, optionNo);
    }

    /** Idempotent: records (or replaces) a short answer; its mark stays NULL until submission. */
    public static void saveText(Connection c, int regisId, int attemptNo, int questionId, String text)
            throws SQLException {
        Jdbc.exec(c, "INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) " +
                "VALUES (?, ?, ?, ?, NULL) ON DUPLICATE KEY UPDATE AnswerText = ?",
                questionId, attemptNo, regisId, text, text);
    }

    /** The student emptied the answer: forget it (selection first, then the answer row). */
    public static void clearAnswer(Connection c, int regisId, int attemptNo, int questionId) throws SQLException {
        Jdbc.exec(c, "DELETE FROM SELECT_TABLE WHERE Quest_ID = ? AND Attempt_no = ? AND RegisID = ?",
                questionId, attemptNo, regisId);
        Jdbc.exec(c, "DELETE FROM ANSWER WHERE Quest_ID = ? AND Attempt_no = ? AND RegisID = ?",
                questionId, attemptNo, regisId);
    }

    public static List<SavedAnswer> savedAnswers(Connection c, int regisId, int attemptNo) throws SQLException {
        List<SavedAnswer> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT an.Quest_ID, st.Option_no, an.AnswerText FROM ANSWER an " +
                "LEFT JOIN SELECT_TABLE st ON st.Quest_ID = an.Quest_ID AND st.Attempt_no = an.Attempt_no " +
                "  AND st.RegisID = an.RegisID WHERE an.RegisID = ? AND an.Attempt_no = ?")) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int opt = rs.getInt(2);
                    boolean noOption = rs.wasNull(); // must be read right after the column it refers to
                    out.add(new SavedAnswer(rs.getInt(1), noOption ? null : opt, rs.getString(3)));
                }
            }
        }
        return out;
    }

    // ---- Grading -------------------------------------------------------------

    /** One row per question of the attempt's paper, including questions left unanswered. */
    public static List<GradeRow> gradingRows(Connection c, int regisId, int attemptNo) throws SQLException {
        List<GradeRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT pq.Quest_ID, eqm.Marks, (m.Quest_ID IS NOT NULL) AS is_mcq, an.AnswerText, sa.Answer_key, " +
                "st.Option_no, COALESCE(o.Bool_Correct, 0) AS is_correct " +
                "FROM ATTEMPT a JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN PAPER_QUESTION pq ON pq.PaperKey = a.PaperKey " +
                "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = r.Exam_ID AND eqm.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = pq.Quest_ID " +
                "LEFT JOIN ANSWER an ON an.Quest_ID = pq.Quest_ID AND an.Attempt_no = a.Attempt_no " +
                "  AND an.RegisID = a.RegisID " +
                "LEFT JOIN SELECT_TABLE st ON st.Quest_ID = pq.Quest_ID AND st.Attempt_no = a.Attempt_no " +
                "  AND st.RegisID = a.RegisID " +
                "LEFT JOIN OPTIONS o ON o.Quest_ID = st.Quest_ID AND o.Option_no = st.Option_no " +
                "WHERE a.RegisID = ? AND a.Attempt_no = ?")) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int opt = rs.getInt(6);
                    boolean noOption = rs.wasNull();
                    out.add(new GradeRow(rs.getInt(1), rs.getBigDecimal(2), rs.getBoolean(3), rs.getString(4),
                            rs.getString(5), noOption ? null : opt, rs.getInt(7) == 1));
                }
            }
        }
        return out;
    }

    /**
     * Writes the final mark of each question. Missing ANSWER rows (unanswered questions) are
     * created with AnswerText NULL; existing rows keep their text.
     */
    public static void writeMarks(Connection c, int regisId, int attemptNo, Map<Integer, BigDecimal> marks)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) " +
                "VALUES (?, ?, ?, NULL, ?) ON DUPLICATE KEY UPDATE MarkAwarded = ?")) {
            for (Map.Entry<Integer, BigDecimal> e : marks.entrySet()) {
                ps.setInt(1, e.getKey());
                ps.setInt(2, attemptNo);
                ps.setInt(3, regisId);
                ps.setBigDecimal(4, e.getValue());
                ps.setBigDecimal(5, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---- Results --------------------------------------------------------------

    private static final String RESULT_SELECT =
            "SELECT a.RegisID, a.Attempt_no, a.EndTime, e.Exam_ID, e.Title, c.CName, e.Pass_mark, " +
            "COALESCE(SUM(eqm.Marks), 0) AS max_score, COALESCE(SUM(ans.MarkAwarded), 0) AS score, " +
            "COALESCE(SUM(CASE WHEN ans.MarkAwarded IS NULL THEN 1 ELSE 0 END), 0) AS ungraded " +
            "FROM ATTEMPT a " +
            "JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
            "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
            "JOIN COURSE c ON c.Course_ID = e.Course_ID " +
            "JOIN PAPER_QUESTION pq ON pq.PaperKey = a.PaperKey " +
            "JOIN EXAM_QUESTION_MARKS eqm ON eqm.Exam_ID = e.Exam_ID AND eqm.Quest_ID = pq.Quest_ID " +
            "LEFT JOIN ANSWER ans ON ans.Quest_ID = pq.Quest_ID AND ans.Attempt_no = a.Attempt_no " +
            "  AND ans.RegisID = a.RegisID ";
    private static final String RESULT_TAIL =
            " GROUP BY a.RegisID, a.Attempt_no, a.EndTime, e.Exam_ID, e.Title, c.CName, e.Pass_mark " +
            "ORDER BY a.EndTime DESC, a.RegisID DESC, a.Attempt_no DESC";

    /** Submitted attempts of the student, newest first. */
    public static List<ResultRow> finishedByStudent(Connection c, int studentId) throws SQLException {
        return results(c, "WHERE r.User_ID = ? AND a.EndTime IS NOT NULL", studentId);
    }

    /** The result of one attempt (empty when it is unfinished or its paper has no questions). */
    public static List<ResultRow> finishedOne(Connection c, int regisId, int attemptNo) throws SQLException {
        return results(c, "WHERE a.RegisID = ? AND a.Attempt_no = ? AND a.EndTime IS NOT NULL", regisId, attemptNo);
    }

    private static List<ResultRow> results(Connection c, String where, Object... params) throws SQLException {
        List<ResultRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(RESULT_SELECT + where + RESULT_TAIL)) {
            Jdbc.bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ResultRow(rs.getInt("RegisID"), rs.getInt("Attempt_no"), rs.getTimestamp("EndTime"),
                            rs.getInt("Exam_ID"), rs.getString("Title"), rs.getString("CName"),
                            rs.getBigDecimal("Pass_mark"), rs.getBigDecimal("score"), rs.getBigDecimal("max_score"),
                            rs.getInt("ungraded")));
                }
            }
        }
        return out;
    }
}
