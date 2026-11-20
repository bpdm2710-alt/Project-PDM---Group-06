package org.example.Service;

import org.example.util.DatabaseConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Student data layer. Done so far: Overview, My exams, Results.
 * The other methods answer UNAVAILABLE until they are written.
 *
 * Conventions taken from the existing module-3 code and the real schema:
 *  - an attempt is IN PROGRESS while EndTime = StartTime, submitted when EndTime > StartTime
 *    (ATTEMPT.EndTime is NOT NULL);
 *  - score = SUM(ANSWER.MarkAwarded), max = SUM(CONTAINS.Marks) of the attempt's paper;
 *  - passed = score >= EXAMINATION.Pass_mark (raw marks, decision D11);
 *  - the paper shown on a card is the exam's lowest PaperID.
 */
public class StudentServiceImpl implements StudentService {

    // ---- Overview ---------------------------------------------------------

    @Override
    public StudentOverview getOverview(int studentId) throws ServiceException {
        return db(conn -> {
            StudentProfile profile = loadProfile(conn, studentId);
            List<ExamCard> exams = loadRegisteredExams(conn, studentId);
            List<AttemptResult> results = loadFinishedAttempts(conn, studentId);

            int courseCount = (int) exams.stream().map(ExamCard::courseId).distinct().count();
            List<ExamCard> upcoming = exams.stream()
                    .filter(e -> e.state() == ExamState.UPCOMING || e.state() == ExamState.OPEN
                            || e.state() == ExamState.IN_PROGRESS)
                    .toList();

            BigDecimal average = null;
            if (!results.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (AttemptResult r : results) {
                    sum = sum.add(r.percent());
                }
                average = sum.divide(BigDecimal.valueOf(results.size()), 1, RoundingMode.HALF_UP);
            }
            return new StudentOverview(profile, courseCount, upcoming.size(), average,
                    results.size(), upcoming.stream().limit(5).toList(),
                    results.isEmpty() ? null : results.get(0));
        });
    }

    // ---- My exams / Results ----------------------------------------------

    @Override
    public List<ExamCard> listMyExams(int studentId) throws ServiceException {
        return db(conn -> loadRegisteredExams(conn, studentId));
    }

    @Override
    public List<AttemptResult> listResults(int studentId) throws ServiceException {
        return db(conn -> loadFinishedAttempts(conn, studentId));
    }

    // ---- Not written yet --------------------------------------------------

    @Override
    public CourseFilterOptions getCourseFilterOptions() throws ServiceException {
        throw notYet("Courses");
    }

    @Override
    public List<CourseCard> listCourses(int studentId, CourseScope scope, CourseFilter filter)
            throws ServiceException {
        throw notYet("Courses");
    }

    @Override
    public List<ExamCard> listCourseExams(int studentId, int courseId) throws ServiceException {
        throw notYet("Course exams");
    }

    @Override
    public ExamCard registerForExam(int studentId, int examId) throws ServiceException {
        throw notYet("Exam registration");
    }

    @Override
    public AttemptSession startAttempt(int regisId) throws ServiceException {
        throw notYet("Taking an exam");
    }

    @Override
    public void saveAnswer(AttemptKey key, int questionId, AnswerInput input) throws ServiceException {
        throw notYet("Taking an exam");
    }

    @Override
    public AttemptResult submitAttempt(AttemptKey key) throws ServiceException {
        throw notYet("Submitting an exam");
    }

    // ---- Queries ----------------------------------------------------------

    private StudentProfile loadProfile(Connection conn, int studentId) throws SQLException, ServiceException {
        String sql = "SELECT u.FirstName, u.LastName, s.Enrollment_year " +
                "FROM `USER` u JOIN STUDENT s ON s.User_ID = u.User_ID WHERE u.User_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Student not found.");
                }
                return new StudentProfile(studentId,
                        (rs.getString("FirstName") + " " + rs.getString("LastName")).trim(),
                        rs.getInt("Enrollment_year"));
            }
        }
    }

    /** Every exam the student registered for, soonest first. */
    private List<ExamCard> loadRegisteredExams(Connection conn, int studentId) throws SQLException {
        String paperQuestions = " FROM CONTAINS ct WHERE ct.Exam_ID = e.Exam_ID AND ct.PaperID = " +
                "(SELECT MIN(p.PaperID) FROM PAPER p WHERE p.Exam_ID = e.Exam_ID)";
        String sql = "SELECT r.RegisID, e.Exam_ID, e.Course_ID, c.CName, e.Title, e.Exam_date, " +
                "e.Duration, e.Pass_mark, e.Max_attempts, " +
                "(SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = r.RegisID) AS used, " +
                "(SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = r.RegisID " +
                "   AND a.EndTime = a.StartTime) AS in_progress, " +
                "(SELECT COUNT(*)" + paperQuestions + ") AS question_count, " +
                "(SELECT COALESCE(SUM(ct.Marks), 0)" + paperQuestions + ") AS total_marks " +
                "FROM REGISTRATION r " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN COURSE c ON c.Course_ID = e.Course_ID " +
                "WHERE r.User_ID = ? ORDER BY e.Exam_date, r.RegisID";
        LocalDateTime now = LocalDateTime.now();
        List<ExamCard> cards = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("Exam_date");
                    LocalDateTime date = ts == null ? null : ts.toLocalDateTime();
                    int duration = rs.getInt("Duration");
                    int used = rs.getInt("used");
                    int max = rs.getInt("Max_attempts");
                    ExamState state;
                    if (rs.getInt("in_progress") > 0) {
                        state = ExamState.IN_PROGRESS;
                    } else if (used >= max || (date != null && now.isAfter(date.plusMinutes(duration)))) {
                        state = ExamState.COMPLETED;
                    } else if (date != null && now.isBefore(date)) {
                        state = ExamState.UPCOMING;
                    } else {
                        state = ExamState.OPEN;
                    }
                    cards.add(new ExamCard(rs.getInt("Exam_ID"), rs.getInt("Course_ID"),
                            rs.getString("CName"), rs.getString("Title"), date, duration,
                            rs.getInt("question_count"), rs.getBigDecimal("total_marks"),
                            rs.getBigDecimal("Pass_mark"), max, rs.getInt("RegisID"), used, state));
                }
            }
        }
        return cards;
    }

    /** Submitted attempts of the student, newest first. */
    private List<AttemptResult> loadFinishedAttempts(Connection conn, int studentId) throws SQLException {
        String sql = "SELECT a.RegisID, a.Attempt_no, a.EndTime, e.Exam_ID, e.Title, c.CName, e.Pass_mark, " +
                "COALESCE(SUM(ct.Marks), 0) AS max_score, " +
                "COALESCE(SUM(ans.MarkAwarded), 0) AS score " +
                "FROM ATTEMPT a " +
                "JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN COURSE c ON c.Course_ID = e.Course_ID " +
                "JOIN CONTAINS ct ON ct.PaperID = a.PaperID AND ct.Exam_ID = a.Exam_ID " +
                "LEFT JOIN ANSWER ans ON ans.Quest_ID = ct.Quest_ID " +
                "  AND ans.Attempt_no = a.Attempt_no AND ans.RegisID = a.RegisID " +
                "WHERE r.User_ID = ? AND a.EndTime > a.StartTime " +
                "GROUP BY a.RegisID, a.Attempt_no, a.EndTime, e.Exam_ID, e.Title, c.CName, e.Pass_mark " +
                "ORDER BY a.EndTime DESC, a.RegisID DESC, a.Attempt_no DESC";
        List<AttemptResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal score = rs.getBigDecimal("score");
                    BigDecimal max = rs.getBigDecimal("max_score");
                    BigDecimal percent = max.signum() == 0 ? BigDecimal.ZERO
                            : score.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
                    // MarkAwarded is NOT NULL, so ungraded answers cannot be told apart yet.
                    results.add(new AttemptResult(
                            new AttemptKey(rs.getInt("RegisID"), rs.getInt("Attempt_no")),
                            rs.getInt("Exam_ID"), rs.getString("Title"), rs.getString("CName"),
                            rs.getTimestamp("EndTime").toLocalDateTime(), score, max, percent,
                            score.compareTo(rs.getBigDecimal("Pass_mark")) >= 0, true));
                }
            }
        }
        return results;
    }

    // ---- Plumbing ---------------------------------------------------------

    @FunctionalInterface
    private interface DbWork<T> {
        T run(Connection conn) throws SQLException, ServiceException;
    }

    private static <T> T db(DbWork<T> work) throws ServiceException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return work.run(conn);
        } catch (SQLException | RuntimeException | LinkageError e) {
            e.printStackTrace();
            throw new ServiceException(ServiceException.Kind.UNAVAILABLE,
                    "Cannot read from the database. [" + ServiceException.rootCauseText(e) + "]", e);
        }
    }

    private static ServiceException notYet(String what) {
        return new ServiceException(ServiceException.Kind.UNAVAILABLE,
                what + " is not connected to the database yet.");
    }
}
