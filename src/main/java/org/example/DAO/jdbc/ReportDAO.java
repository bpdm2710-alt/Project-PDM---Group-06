package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * The analytical queries of database/queries/PDMProjectQuery.sql, one SQL constant + one method each
 * (Q1..Q11, same numbering as the .sql file). Read-only.
 *
 * Rules shared by the score queries: only submitted attempts (ATTEMPT.EndTime IS NOT NULL) count, a
 * total is reported only for fully graded attempts (v_attempt_scores.IsFullyGraded = 1), and the
 * relational division only considers PUBLISHED exams. Q5 uses EXCEPT (MySQL 8.0.31+).
 *
 * Each query can be read two ways: a typed method (records below) or {@link #table} for display.
 */
public final class ReportDAO {

    // ---- SQL (keep identical to PDMProjectQuery.sql) -------------------------------------

    /** Q1, param: min error percent (file uses 70). */
    public static final String Q1_HIGH_ERROR_RATE =
            "WITH AttemptQuestion AS (" +
            "  SELECT ST.Quest_ID, ST.RegisID, ST.Attempt_no, " +
            "         MAX(CASE WHEN O.Bool_Correct = 0 THEN 1 ELSE 0 END) AS IsIncorrect " +
            "  FROM SELECT_TABLE ST " +
            "  JOIN OPTIONS O ON ST.Option_no = O.Option_no AND ST.Quest_ID = O.Quest_ID " +
            "  JOIN ATTEMPT A ON A.RegisID = ST.RegisID AND A.Attempt_no = ST.Attempt_no " +
            "  WHERE A.EndTime IS NOT NULL " +
            "  GROUP BY ST.Quest_ID, ST.RegisID, ST.Attempt_no), " +
            "QuestionError AS (" +
            "  SELECT Quest_ID, COUNT(*) AS TotalAnswers, SUM(IsIncorrect) AS IncorrectAnswers, " +
            "         ROUND(SUM(IsIncorrect) * 100.0 / COUNT(*), 2) AS ErrorRate " +
            "  FROM AttemptQuestion GROUP BY Quest_ID) " +
            "SELECT Q.Quest_ID, Q.Content AS Question_Content, QE.TotalAnswers, QE.IncorrectAnswers, QE.ErrorRate " +
            "FROM QuestionError QE JOIN QUESTION Q ON QE.Quest_ID = Q.Quest_ID " +
            "WHERE QE.ErrorRate > ? ORDER BY QE.ErrorRate DESC, Q.Quest_ID";

    /** Q2, no params. */
    public static final String Q2_EXAM_PAPER_MARKS =
            "SELECT E.Exam_ID, P.PaperID, Q.Quest_ID, Q.Content AS Question_Content, PQ.QuestionOrder, EQM.Marks " +
            "FROM EXAMINATION E JOIN PAPER P ON E.Exam_ID = P.Exam_ID " +
            "JOIN PAPER_QUESTION PQ ON P.PaperKey = PQ.PaperKey " +
            "JOIN EXAM_QUESTION_MARKS EQM ON E.Exam_ID = EQM.Exam_ID AND PQ.Quest_ID = EQM.Quest_ID " +
            "JOIN QUESTION Q ON PQ.Quest_ID = Q.Quest_ID " +
            "ORDER BY E.Exam_ID, P.PaperID, PQ.QuestionOrder";

    /** Q3, no params. */
    public static final String Q3_STUDENTS_NO_ATTEMPTS =
            "SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name " +
            "FROM STUDENT S JOIN `USER` U ON S.User_ID = U.User_ID " +
            "WHERE NOT EXISTS (SELECT 1 FROM REGISTRATION R JOIN ATTEMPT A ON R.RegisID = A.RegisID " +
            "                  WHERE R.User_ID = S.User_ID) " +
            "ORDER BY S.User_ID";

    /** Q4, no params. */
    public static final String Q4_ATTEMPT_SCORES =
            "SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name, A.Attempt_no, R.Exam_ID, " +
            "       SUM(AN.MarkAwarded) AS Total_Score " +
            "FROM STUDENT S JOIN `USER` U ON S.User_ID = U.User_ID " +
            "JOIN REGISTRATION R ON S.User_ID = R.User_ID " +
            "JOIN ATTEMPT A ON R.RegisID = A.RegisID " +
            "JOIN ANSWER AN ON A.RegisID = AN.RegisID AND A.Attempt_no = AN.Attempt_no " +
            "WHERE A.EndTime IS NOT NULL " +
            "  AND (A.RegisID, A.Attempt_no) IN (" +
            "      SELECT V.RegisID, V.Attempt_no FROM v_attempt_scores V WHERE V.IsFullyGraded = 1) " +
            "GROUP BY S.User_ID, U.FirstName, U.LastName, A.RegisID, A.Attempt_no, R.Exam_ID " +
            "ORDER BY S.User_ID, R.Exam_ID, A.Attempt_no";

    /** Q5, params: exam A, exam B. */
    public static final String Q5_IN_EXAM_A_NOT_B =
            "SELECT Q.Quest_ID, Q.Content FROM QUESTION Q " +
            "JOIN EXAM_QUESTION_MARKS EQM ON EQM.Quest_ID = Q.Quest_ID WHERE EQM.Exam_ID = ? " +
            "EXCEPT " +
            "SELECT Q.Quest_ID, Q.Content FROM QUESTION Q " +
            "JOIN EXAM_QUESTION_MARKS EQM ON EQM.Quest_ID = Q.Quest_ID WHERE EQM.Exam_ID = ? " +
            "ORDER BY Quest_ID";

    /** Q6, param: min question count (file uses 2). */
    public static final String Q6_TOPIC_QUESTION_COUNTS =
            "SELECT T.Topic_ID, T.Content AS Topic_Content, COUNT(Q.Quest_ID) AS Number_of_Questions " +
            "FROM TOPIC T JOIN QUESTION Q ON Q.Topic_ID = T.Topic_ID " +
            "GROUP BY T.Topic_ID, T.Content HAVING COUNT(Q.Quest_ID) >= ? " +
            "ORDER BY Number_of_Questions DESC, T.Topic_ID";

    /** Q7, no params. */
    public static final String Q7_EXAMS_AVERAGE_PASS =
            "SELECT E.Exam_ID, E.Exam_date, COUNT(*) AS Number_of_Attempts, " +
            "       ROUND(AVG(V.ScorePercent), 2) AS Average_Score_Percent " +
            "FROM EXAMINATION E JOIN v_attempt_scores V ON E.Exam_ID = V.Exam_ID " +
            "WHERE V.IsFullyGraded = 1 " +
            "GROUP BY E.Exam_ID, E.Exam_date, E.Pass_mark " +
            "HAVING AVG(V.ScorePercent) >= E.Pass_mark " +
            "ORDER BY Average_Score_Percent DESC, E.Exam_ID";

    /** Q8, param: min attempts (file uses 2). */
    public static final String Q8_STUDENTS_MANY_ATTEMPTS =
            "SELECT S.User_ID AS Student_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name, " +
            "       COUNT(*) AS Total_Attempts " +
            "FROM STUDENT S JOIN `USER` U ON U.User_ID = S.User_ID " +
            "JOIN REGISTRATION R ON R.User_ID = S.User_ID JOIN ATTEMPT A ON A.RegisID = R.RegisID " +
            "GROUP BY S.User_ID, U.FirstName, U.LastName HAVING COUNT(*) >= ? " +
            "ORDER BY Total_Attempts DESC, S.User_ID";

    /** Q9, no params. */
    public static final String Q9_EXAM_PERFORMANCE =
            "SELECT E.Exam_ID, COUNT(*) AS Total_Attempts, ROUND(AVG(V.Total_Score), 2) AS Average_Score, " +
            "       MAX(V.Total_Score) AS Highest_Score " +
            "FROM EXAMINATION E JOIN v_attempt_scores V ON E.Exam_ID = V.Exam_ID " +
            "WHERE V.IsFullyGraded = 1 GROUP BY E.Exam_ID ORDER BY Average_Score DESC, E.Exam_ID";

    /** Q10, param: course id. */
    public static final String Q10_STUDENTS_ALL_EXAMS =
            "SELECT S.User_ID, U.FirstName, U.LastName FROM STUDENT S " +
            "JOIN `USER` U ON S.User_ID = U.User_ID " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM EXAMINATION E WHERE E.Course_ID = ? AND E.Status = 'PUBLISHED' " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM REGISTRATION R JOIN ATTEMPT A ON R.RegisID = A.RegisID " +
            "    WHERE R.User_ID = S.User_ID AND R.Exam_ID = E.Exam_ID AND A.EndTime IS NOT NULL)) " +
            "ORDER BY S.User_ID";

    /**
     * Q11, no params. Written on the base tables (not on the view vw_AverageScoreByTopic) because the
     * submitted 02 schema does not create that view; PDMProjectQuery.sql Q12 creates it with the same SQL.
     */
    public static final String Q11_TOPIC_AVERAGES =
            "SELECT T.Topic_ID, T.Content AS Topic_Name, ROUND(AVG(AN.MarkAwarded), 2) AS Average_Score, " +
            "       ROUND(AVG(AN.MarkAwarded * 100.0 / EQM.Marks), 2) AS Average_Percent " +
            "FROM TOPIC T JOIN QUESTION Q ON T.Topic_ID = Q.Topic_ID " +
            "JOIN ANSWER AN ON Q.Quest_ID = AN.Quest_ID " +
            "JOIN ATTEMPT A ON A.RegisID = AN.RegisID AND A.Attempt_no = AN.Attempt_no " +
            "JOIN REGISTRATION R ON R.RegisID = A.RegisID " +
            "JOIN EXAM_QUESTION_MARKS EQM ON EQM.Exam_ID = R.Exam_ID AND EQM.Quest_ID = AN.Quest_ID " +
            "WHERE AN.MarkAwarded IS NOT NULL AND A.EndTime IS NOT NULL " +
            "GROUP BY T.Topic_ID, T.Content ORDER BY T.Topic_ID";

    // ---- Typed rows ---------------------------------------------------------------------------

    public record ErrorRateRow(int questionId, String content, int totalAnswers, int incorrectAnswers,
                               BigDecimal errorRate) {
    }

    public record PaperMarkRow(int examId, int paperId, int questionId, String content, int order,
                               BigDecimal marks) {
    }

    public record StudentRow(int userId, String name) {
    }

    public record AttemptScoreRow(int userId, String name, int attemptNo, int examId, BigDecimal totalScore) {
    }

    public record QuestionRow(int questionId, String content) {
    }

    public record TopicCountRow(int topicId, String topic, int questionCount) {
    }

    public record ExamAverageRow(int examId, Timestamp date, int attempts, BigDecimal averagePercent) {
    }

    public record StudentCountRow(int userId, String name, int attempts) {
    }

    public record ExamSummaryRow(int examId, int attempts, BigDecimal averageScore, BigDecimal highestScore) {
    }

    public record TopicAverageRow(int topicId, String topic, BigDecimal averageScore, BigDecimal averagePercent) {
    }

    /** Generic result for display: column labels + rows of raw JDBC values. */
    public record Table(List<String> columns, List<Object[]> rows) {
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private ReportDAO() {
    }

    // ---- Typed methods --------------------------------------------------------------------------

    /** Q1: MCQs answered wrongly in more than {@code minErrorPercent} % of submitted attempts. */
    public static List<ErrorRateRow> questionsWithHighErrorRate(Connection c, double minErrorPercent)
            throws SQLException {
        return list(c, Q1_HIGH_ERROR_RATE, rs -> new ErrorRateRow(rs.getInt(1), rs.getString(2), rs.getInt(3),
                rs.getInt(4), rs.getBigDecimal(5)), minErrorPercent);
    }

    /** Q2: every question of every paper with its order and marks. */
    public static List<PaperMarkRow> examPaperMarks(Connection c) throws SQLException {
        return list(c, Q2_EXAM_PAPER_MARKS, rs -> new PaperMarkRow(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                rs.getString(4), rs.getInt(5), rs.getBigDecimal(6)));
    }

    /** Q3: students who never started an attempt. */
    public static List<StudentRow> studentsWithNoAttempts(Connection c) throws SQLException {
        return list(c, Q3_STUDENTS_NO_ATTEMPTS, rs -> new StudentRow(rs.getInt(1), rs.getString(2)));
    }

    /** Q4: total score of every submitted, fully graded attempt. */
    public static List<AttemptScoreRow> studentAttemptScores(Connection c) throws SQLException {
        return list(c, Q4_ATTEMPT_SCORES, rs -> new AttemptScoreRow(rs.getInt(1), rs.getString(2), rs.getInt(3),
                rs.getInt(4), rs.getBigDecimal(5)));
    }

    /** Q5 (EXCEPT): questions used in exam A but not in exam B. */
    public static List<QuestionRow> questionsInExamButNotOther(Connection c, int examA, int examB)
            throws SQLException {
        return list(c, Q5_IN_EXAM_A_NOT_B, rs -> new QuestionRow(rs.getInt(1), rs.getString(2)), examA, examB);
    }

    /** Q6: topics with at least {@code minQuestions} questions. */
    public static List<TopicCountRow> topicQuestionCounts(Connection c, int minQuestions) throws SQLException {
        return list(c, Q6_TOPIC_QUESTION_COUNTS, rs -> new TopicCountRow(rs.getInt(1), rs.getString(2),
                rs.getInt(3)), minQuestions);
    }

    /** Q7: exams whose average score (%) reaches their pass mark. */
    public static List<ExamAverageRow> examsAveragingAtLeastPassMark(Connection c) throws SQLException {
        return list(c, Q7_EXAMS_AVERAGE_PASS, rs -> new ExamAverageRow(rs.getInt(1), rs.getTimestamp(2),
                rs.getInt(3), rs.getBigDecimal(4)));
    }

    /** Q8: students with at least {@code minAttempts} attempts. */
    public static List<StudentCountRow> studentAttemptStatistics(Connection c, int minAttempts)
            throws SQLException {
        return list(c, Q8_STUDENTS_MANY_ATTEMPTS, rs -> new StudentCountRow(rs.getInt(1), rs.getString(2),
                rs.getInt(3)), minAttempts);
    }

    /** Q9: attempts, average and highest score per exam (fully graded attempts). */
    public static List<ExamSummaryRow> examinationPerformanceSummary(Connection c) throws SQLException {
        return list(c, Q9_EXAM_PERFORMANCE, rs -> new ExamSummaryRow(rs.getInt(1), rs.getInt(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4)));
    }

    /** Q10 (division): students with a submitted attempt for every published exam of the course. */
    public static List<StudentRow> studentsWhoAttemptedEveryExam(Connection c, int courseId) throws SQLException {
        return list(c, Q10_STUDENTS_ALL_EXAMS,
                rs -> new StudentRow(rs.getInt(1), rs.getString(2) + " " + rs.getString(3)), courseId);
    }

    /** Q11: average mark and percent per topic (submitted attempts). */
    public static List<TopicAverageRow> averageScoreByTopic(Connection c) throws SQLException {
        return list(c, Q11_TOPIC_AVERAGES, rs -> new TopicAverageRow(rs.getInt(1), rs.getString(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4)));
    }

    // ---- Generic -------------------------------------------------------------------------------------

    /** Runs one of the Q* constants and returns its columns (labels as in the SQL) and raw values. */
    public static Table table(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> cols = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    cols.add(md.getColumnLabel(i));
                }
                List<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    Object[] r = new Object[cols.size()];
                    for (int i = 0; i < r.length; i++) {
                        r[i] = rs.getObject(i + 1);
                    }
                    rows.add(r);
                }
                return new Table(cols, rows);
            }
        }
    }

    private static <T> List<T> list(Connection c, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Jdbc.bind(ps, params, 0);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
            }
        }
        return out;
    }
}
