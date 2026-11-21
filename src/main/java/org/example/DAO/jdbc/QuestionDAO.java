package org.example.DAO.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** QUESTION and its subtypes MCQ(+OPTIONS) / SHORT_ANSWER. */
public final class QuestionDAO {

    public record BankRow(int questionId, String content, int difficulty, String topic, boolean mcq) {
    }

    public record SearchResult(List<BankRow> rows, long total) {
    }

    public record Row(int questionId, int topicId, String content, int difficulty, boolean mcq,
                      String answerKey) {
    }

    public record OptionRow(String content, boolean correct) {
    }

    private QuestionDAO() {
    }

    /**
     * Questions whose topic the course covers.
     * @param mcq null = both types, true = MCQ only, false = short answer only
     */
    public static SearchResult search(Connection c, int courseId, String keyword, Integer topicId,
                                      Boolean mcq, Integer difficulty, int limit, long offset)
            throws SQLException {
        StringBuilder from = new StringBuilder(
                " FROM QUESTION q JOIN TOPIC t ON t.Topic_ID = q.Topic_ID " +
                "JOIN COVERS cv ON cv.Topic_ID = q.Topic_ID AND cv.Course_ID = ? " +
                "LEFT JOIN MCQ m ON m.Quest_ID = q.Quest_ID WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        params.add(courseId);
        if (keyword != null && !keyword.isBlank()) {
            from.append(" AND q.Content LIKE ? ESCAPE '\\\\'");
            params.add("%" + likeEscape(keyword.trim()) + "%");
        }
        if (topicId != null) {
            from.append(" AND q.Topic_ID = ?");
            params.add(topicId);
        }
        if (mcq != null) {
            from.append(mcq ? " AND m.Quest_ID IS NOT NULL" : " AND m.Quest_ID IS NULL");
        }
        if (difficulty != null) {
            from.append(" AND q.Difficulty = ?");
            params.add(difficulty);
        }

        long total;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*)" + from)) {
            Jdbc.bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getLong(1);
            }
        }
        List<BankRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT q.Quest_ID, q.Content, q.Difficulty, t.Content AS topic, " +
                "(m.Quest_ID IS NOT NULL) AS is_mcq" + from + " ORDER BY q.Quest_ID DESC LIMIT ? OFFSET ?")) {
            int next = Jdbc.bind(ps, params, 0);
            ps.setInt(next + 1, limit);
            ps.setLong(next + 2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BankRow(rs.getInt("Quest_ID"), rs.getString("Content"),
                            rs.getInt("Difficulty"), rs.getString("topic"), rs.getBoolean("is_mcq")));
                }
            }
        }
        return new SearchResult(rows, total);
    }

    /** @return null when the question does not exist. */
    public static Row find(Connection c, int questionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT q.Content, q.Difficulty, q.Topic_ID, (m.Quest_ID IS NOT NULL) AS is_mcq, sa.Answer_key " +
                "FROM QUESTION q LEFT JOIN MCQ m ON m.Quest_ID = q.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = q.Quest_ID WHERE q.Quest_ID = ?")) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Row(questionId, rs.getInt("Topic_ID"), rs.getString("Content"),
                        rs.getInt("Difficulty"), rs.getBoolean("is_mcq"), rs.getString("Answer_key"));
            }
        }
    }

    /** Options in display order. */
    public static List<OptionRow> options(Connection c, int questionId) throws SQLException {
        List<OptionRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Op_Content, Bool_Correct FROM OPTIONS WHERE Quest_ID = ? ORDER BY Option_no")) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OptionRow(rs.getString(1), rs.getInt(2) == 1));
                }
            }
        }
        return out;
    }

    public static Integer topicOf(Connection c, int questionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT Topic_ID FROM QUESTION WHERE Quest_ID = ?")) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public static boolean exists(Connection c, int questionId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM QUESTION WHERE Quest_ID = ?", questionId) > 0;
    }

    /** Locks the question row. @return false when it does not exist. */
    public static boolean lock(Connection c, int questionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Quest_ID FROM QUESTION WHERE Quest_ID = ? FOR UPDATE")) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static int insert(Connection c, String content, int difficulty, int topicId, int instructorId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO QUESTION (Content, Difficulty, Topic_ID, User_ID) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, content);
            ps.setInt(2, difficulty);
            ps.setInt(3, topicId);
            ps.setInt(4, instructorId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The question was inserted but no id was returned.");
                }
                return keys.getInt(1);
            }
        }
    }

    public static void update(Connection c, int questionId, String content, int difficulty, int topicId)
            throws SQLException {
        Jdbc.exec(c, "UPDATE QUESTION SET Content = ?, Difficulty = ?, Topic_ID = ? WHERE Quest_ID = ?",
                content, difficulty, topicId, questionId);
    }

    /** MCQ row + its options numbered 1..n in the given order. */
    public static void insertMcq(Connection c, int questionId, List<OptionRow> options) throws SQLException {
        Jdbc.exec(c, "INSERT INTO MCQ (Quest_ID) VALUES (?)", questionId);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO OPTIONS (Option_no, Quest_ID, Op_Content, Bool_Correct) VALUES (?, ?, ?, ?)")) {
            int no = 1;
            for (OptionRow o : options) {
                ps.setInt(1, no++);
                ps.setInt(2, questionId);
                ps.setString(3, o.content());
                ps.setInt(4, o.correct() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static void insertShortAnswer(Connection c, int questionId, String answerKey) throws SQLException {
        Jdbc.exec(c, "INSERT INTO SHORT_ANSWER (Quest_ID, Answer_key) VALUES (?, ?)", questionId, answerKey);
    }

    /** Removes the MCQ/options/short-answer rows (not the QUESTION row). */
    public static void deleteSubtype(Connection c, int questionId) throws SQLException {
        Jdbc.exec(c, "DELETE FROM SELECT_TABLE WHERE Quest_ID = ?", questionId);
        Jdbc.exec(c, "DELETE FROM OPTIONS WHERE Quest_ID = ?", questionId);
        Jdbc.exec(c, "DELETE FROM MCQ WHERE Quest_ID = ?", questionId);
        Jdbc.exec(c, "DELETE FROM SHORT_ANSWER WHERE Quest_ID = ?", questionId);
    }

    public static void delete(Connection c, int questionId) throws SQLException {
        Jdbc.exec(c, "DELETE FROM QUESTION WHERE Quest_ID = ?", questionId);
    }

    public static int answerCount(Connection c, int questionId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM ANSWER WHERE Quest_ID = ?", questionId);
    }

    /** On how many papers of PUBLISHED exams the question sits. */
    public static int publishedExamUseCount(Connection c, int questionId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM PAPER_QUESTION pq JOIN PAPER p ON p.PaperKey = pq.PaperKey " +
                "JOIN EXAMINATION e ON e.Exam_ID = p.Exam_ID " +
                "WHERE pq.Quest_ID = ? AND e.Status = 'PUBLISHED'", questionId);
    }

    /** On how many papers (any exam, any status) the question currently sits. */
    public static int paperUseCount(Connection c, int questionId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM PAPER_QUESTION WHERE Quest_ID = ?", questionId);
    }

    /** Attempts made on papers that contain the question (their wording/topic is frozen by a trigger). */
    public static int attemptedCount(Connection c, int questionId) throws SQLException {
        return Jdbc.scalar(c, "SELECT COUNT(*) FROM PAPER_QUESTION pq JOIN ATTEMPT a ON a.PaperKey = pq.PaperKey " +
                "WHERE pq.Quest_ID = ?", questionId);
    }

    /** Takes the question off every paper and drops its marks rows (only legal while nobody attempted). */
    public static void removeFromAllPapers(Connection c, int questionId) throws SQLException {
        Jdbc.exec(c, "DELETE FROM PAPER_QUESTION WHERE Quest_ID = ?", questionId);
        Jdbc.exec(c, "DELETE FROM EXAM_QUESTION_MARKS WHERE Quest_ID = ?", questionId);
    }

    private static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
