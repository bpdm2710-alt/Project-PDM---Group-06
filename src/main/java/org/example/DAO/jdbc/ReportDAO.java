package org.example.DAO.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The analytical queries of PDMProjectQuery.sql (project query requirements), one method each.
 * Table names are upper case; the views {@code v_attempt_scores} and {@code vw_AverageScoreByTopic}
 * come from 02_online_examination_full_submission.sql. Read-only, nothing here changes data.
 */
public final class ReportDAO {

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

    public record ExamAverageRow(int examId, java.sql.Timestamp date, int attempts, BigDecimal averagePercent) {
    }

    public record StudentCountRow(int userId, String name, int attempts) {
    }

    public record ExamSummaryRow(int examId, int attempts, BigDecimal averageScore, BigDecimal highestScore) {
    }

    public record TopicAverageRow(int topicId, String topic, BigDecimal averageScore) {
    }

    private ReportDAO() {
    }

    /** MCQ questions answered wrongly in more than {@code minErrorPercent} % of the attempts (default 70). */
    public static List<ErrorRateRow> questionsWithHighErrorRate(Connection c, double minErrorPercent)
            throws SQLException {
        String sql =
                "WITH AttemptQuestion AS (" +
                "  SELECT ST.Quest_ID, ST.RegisID, ST.Attempt_no, " +
                "         MAX(CASE WHEN O.Bool_Correct = 0 THEN 1 ELSE 0 END) AS IsIncorrect " +
                "  FROM SELECT_TABLE ST JOIN OPTIONS O ON ST.Option_no = O.Option_no AND ST.Quest_ID = O.Quest_ID " +
                "  GROUP BY ST.Quest_ID, ST.RegisID, ST.Attempt_no), " +
                "QuestionError AS (" +
                "  SELECT Quest_ID, COUNT(*) AS TotalAnswers, SUM(IsIncorrect) AS IncorrectAnswers, " +
                "         ROUND(SUM(IsIncorrect) * 100.0 / COUNT(*), 2) AS ErrorRate " +
                "  FROM AttemptQuestion GROUP BY Quest_ID) " +
                "SELECT Q.Quest_ID, Q.Content, QE.TotalAnswers, QE.IncorrectAnswers, QE.ErrorRate " +
                "FROM QuestionError QE JOIN QUESTION Q ON QE.Quest_ID = Q.Quest_ID " +
                "WHERE QE.ErrorRate > ? ORDER BY QE.ErrorRate DESC";
        List<ErrorRateRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, minErrorPercent);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ErrorRateRow(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                            rs.getBigDecimal(5)));
                }
            }
        }
        return out;
    }

    /** Multi-table JOIN: every question of every paper with its order and marks. */
    public static List<PaperMarkRow> examPaperMarks(Connection c) throws SQLException {
        List<PaperMarkRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT E.Exam_ID, P.PaperID, Q.Quest_ID, Q.Content, PQ.QuestionOrder, EQM.Marks " +
                "FROM EXAMINATION E JOIN PAPER P ON E.Exam_ID = P.Exam_ID " +
                "JOIN PAPER_QUESTION PQ ON P.PaperKey = PQ.PaperKey " +
                "JOIN EXAM_QUESTION_MARKS EQM ON E.Exam_ID = EQM.Exam_ID AND PQ.Quest_ID = EQM.Quest_ID " +
                "JOIN QUESTION Q ON PQ.Quest_ID = Q.Quest_ID " +
                "ORDER BY E.Exam_ID, P.PaperID, PQ.QuestionOrder");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new PaperMarkRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        rs.getInt(5), rs.getBigDecimal(6)));
            }
        }
        return out;
    }

    /** Students who never started an attempt. */
    public static List<StudentRow> studentsWithNoAttempts(Connection c) throws SQLException {
        List<StudentRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name " +
                "FROM STUDENT S JOIN `USER` U ON S.User_ID = U.User_ID " +
                "WHERE NOT EXISTS (SELECT 1 FROM REGISTRATION R JOIN ATTEMPT A ON R.RegisID = A.RegisID " +
                "                  WHERE R.User_ID = S.User_ID) ORDER BY S.User_ID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new StudentRow(rs.getInt(1), rs.getString(2)));
            }
        }
        return out;
    }

    /** Total awarded marks of every attempt of every student. */
    public static List<AttemptScoreRow> studentAttemptScores(Connection c) throws SQLException {
        List<AttemptScoreRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name, A.Attempt_no, R.Exam_ID, " +
                "       SUM(AN.MarkAwarded) AS Total_Score " +
                "FROM STUDENT S JOIN `USER` U ON S.User_ID = U.User_ID " +
                "JOIN REGISTRATION R ON S.User_ID = R.User_ID " +
                "JOIN ATTEMPT A ON R.RegisID = A.RegisID " +
                "JOIN ANSWER AN ON A.RegisID = AN.RegisID AND A.Attempt_no = AN.Attempt_no " +
                "WHERE A.RegisID IN (SELECT R2.RegisID FROM REGISTRATION R2 WHERE R2.User_ID = S.User_ID) " +
                "GROUP BY S.User_ID, U.FirstName, U.LastName, A.Attempt_no, R.Exam_ID " +
                "ORDER BY S.User_ID, A.Attempt_no");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new AttemptScoreRow(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        rs.getBigDecimal(5)));
            }
        }
        return out;
    }

    /**
     * Set operation "topic A but not topic B" (topics given by their text). Written with NOT IN
     * instead of EXCEPT so it also runs on MySQL versions older than 8.0.31.
     */
    public static List<QuestionRow> questionsInTopicButNotOther(Connection c, String topicA, String topicB)
            throws SQLException {
        List<QuestionRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Q.Quest_ID, Q.Content FROM QUESTION Q JOIN TOPIC T ON Q.Topic_ID = T.Topic_ID " +
                "WHERE T.Content = ? AND Q.Quest_ID NOT IN (" +
                "  SELECT Q2.Quest_ID FROM QUESTION Q2 JOIN TOPIC T2 ON Q2.Topic_ID = T2.Topic_ID " +
                "  WHERE T2.Content = ?) ORDER BY Q.Quest_ID")) {
            ps.setString(1, topicA);
            ps.setString(2, topicB);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QuestionRow(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return out;
    }

    /** Topics with at least {@code minQuestions} questions. */
    public static List<TopicCountRow> topicQuestionCounts(Connection c, int minQuestions) throws SQLException {
        List<TopicCountRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT T.Topic_ID, T.Content, COUNT(Q.Quest_ID) AS Number_of_Questions " +
                "FROM TOPIC T JOIN QUESTION Q ON Q.Topic_ID = T.Topic_ID " +
                "GROUP BY T.Topic_ID, T.Content HAVING COUNT(Q.Quest_ID) >= ? " +
                "ORDER BY Number_of_Questions DESC, T.Topic_ID")) {
            ps.setInt(1, minQuestions);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TopicCountRow(rs.getInt(1), rs.getString(2), rs.getInt(3)));
                }
            }
        }
        return out;
    }

    /** Exams whose average score (fully graded attempts only) reaches the pass mark. */
    public static List<ExamAverageRow> examsAveragingAtLeastPassMark(Connection c) throws SQLException {
        List<ExamAverageRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT E.Exam_ID, E.Exam_date, COUNT(*) AS Number_of_Attempts, " +
                "       ROUND(AVG(V.ScorePercent), 2) AS Average_Score_Percent " +
                "FROM EXAMINATION E JOIN v_attempt_scores V ON E.Exam_ID = V.Exam_ID " +
                "WHERE V.IsFullyGraded = 1 " +
                "GROUP BY E.Exam_ID, E.Exam_date, E.Pass_mark " +
                "HAVING AVG(V.ScorePercent) >= E.Pass_mark " +
                "ORDER BY Average_Score_Percent DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new ExamAverageRow(rs.getInt(1), rs.getTimestamp(2), rs.getInt(3), rs.getBigDecimal(4)));
            }
        }
        return out;
    }

    /** Students with at least {@code minAttempts} attempts. */
    public static List<StudentCountRow> studentAttemptStatistics(Connection c, int minAttempts)
            throws SQLException {
        List<StudentCountRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name, COUNT(*) AS Total_Attempts " +
                "FROM STUDENT S JOIN `USER` U ON U.User_ID = S.User_ID " +
                "JOIN REGISTRATION R ON R.User_ID = S.User_ID JOIN ATTEMPT A ON A.RegisID = R.RegisID " +
                "GROUP BY S.User_ID, U.FirstName, U.LastName HAVING COUNT(*) >= ? " +
                "ORDER BY Total_Attempts DESC, S.User_ID")) {
            ps.setInt(1, minAttempts);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StudentCountRow(rs.getInt(1), rs.getString(2), rs.getInt(3)));
                }
            }
        }
        return out;
    }

    /** Attempts, average and highest score per exam (fully graded attempts only). */
    public static List<ExamSummaryRow> examinationPerformanceSummary(Connection c) throws SQLException {
        List<ExamSummaryRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT E.Exam_ID, COUNT(*) AS Total_Attempts, ROUND(AVG(V.Total_Score), 2) AS Average_Score, " +
                "       MAX(V.Total_Score) AS Highest_Score " +
                "FROM EXAMINATION E JOIN v_attempt_scores V ON E.Exam_ID = V.Exam_ID " +
                "WHERE V.IsFullyGraded = 1 GROUP BY E.Exam_ID ORDER BY Average_Score DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new ExamSummaryRow(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
            }
        }
        return out;
    }

    /** Relational division: students who attempted every examination of the course. */
    public static List<StudentRow> studentsWhoAttemptedEveryExam(Connection c, int courseId) throws SQLException {
        List<StudentRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) FROM STUDENT S " +
                "JOIN `USER` U ON S.User_ID = U.User_ID " +
                "WHERE NOT EXISTS (SELECT 1 FROM EXAMINATION E WHERE E.Course_ID = ? AND NOT EXISTS (" +
                "  SELECT 1 FROM REGISTRATION R JOIN ATTEMPT A ON R.RegisID = A.RegisID " +
                "  WHERE R.User_ID = S.User_ID AND R.Exam_ID = E.Exam_ID)) ORDER BY S.User_ID")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StudentRow(rs.getInt(1), rs.getString(2)));
                }
            }
        }
        return out;
    }

    /** Average awarded mark per topic (same as the shared view vw_AverageScoreByTopic). */
    public static List<TopicAverageRow> averageScoreByTopic(Connection c) throws SQLException {
        List<TopicAverageRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT Topic_ID, Topic_Name, Average_Score FROM vw_AverageScoreByTopic ORDER BY Topic_ID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new TopicAverageRow(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3)));
            }
        }
        return out;
    }
}
