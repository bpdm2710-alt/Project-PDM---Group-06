package org.example.Service;

import org.example.session.Session;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Student data layer. Done: Overview, My exams, Results, Courses, Course exams, Registration.
 * Taking an exam: startAttempt (resume or new), saveAnswer (idempotent upsert), submitAttempt (auto-grade).
 *  - exam is open for new attempts from Exam_date until Exam_date + Duration; each attempt has
 *    the full Duration from its own StartTime;
 *  - MCQ is single-select: AnswerText = chosen Option_no, full marks when it is a Bool_Correct option;
 *    short answer: full marks when it equals Answer_key (trim, ignore case); otherwise 0.
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

    // ---- Courses / registration -------------------------------------------

    @Override
    public CourseFilterOptions getCourseFilterOptions() throws ServiceException {
        return db(conn -> {
            List<String> topics = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT t.Content FROM TOPIC t JOIN COVERS cv ON cv.Topic_ID = t.Topic_ID " +
                            "ORDER BY t.Content");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    topics.add(rs.getString(1));
                }
            }
            List<String> semesters = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT Semester FROM TEACH ORDER BY Semester DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    semesters.add(rs.getString(1));
                }
            }
            return new CourseFilterOptions(topics, semesters);
        });
    }

    @Override
    public List<CourseCard> listCourses(int studentId, CourseScope scope, CourseFilter filter)
            throws ServiceException {
        return db(conn -> {
            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT c.Course_ID, c.CName, c.Credits, " +
                    "(SELECT MAX(t.Semester) FROM TEACH t WHERE t.Course_ID = c.Course_ID) AS semester, " +
                    "(SELECT COUNT(DISTINCT r2.User_ID) FROM REGISTRATION r2 " +
                    "   JOIN EXAMINATION e2 ON e2.Exam_ID = r2.Exam_ID " +
                    "   WHERE e2.Course_ID = c.Course_ID) AS student_count, " +
                    "(SELECT COUNT(*) FROM EXAMINATION e3 WHERE e3.Course_ID = c.Course_ID " +
                    "   AND e3.Status = 'PUBLISHED' AND e3.Exam_date > ?) AS upcoming " +
                    "FROM COURSE c WHERE ");
            params.add(Timestamp.valueOf(LocalDateTime.now()));
            sql.append(scope == CourseScope.MY ? "EXISTS" : "NOT EXISTS")
               .append(" (SELECT 1 FROM REGISTRATION r JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                       "WHERE r.User_ID = ? AND e.Course_ID = c.Course_ID)");
            params.add(studentId);
            CourseFilter f = filter == null ? CourseFilter.none() : filter;
            if (notBlank(f.keyword())) {
                sql.append(" AND c.CName LIKE ?");
                params.add("%" + escapeLike(f.keyword().trim()) + "%");
            }
            if (notBlank(f.topic())) {
                sql.append(" AND EXISTS (SELECT 1 FROM COVERS cv JOIN TOPIC tp ON tp.Topic_ID = cv.Topic_ID " +
                        "WHERE cv.Course_ID = c.Course_ID AND tp.Content = ?)");
                params.add(f.topic().trim());
            }
            if (notBlank(f.semester())) {
                sql.append(" AND EXISTS (SELECT 1 FROM TEACH th WHERE th.Course_ID = c.Course_ID " +
                        "AND th.Semester = ?)");
                params.add(f.semester().trim());
            }
            sql.append(" ORDER BY c.CName, c.Course_ID");

            List<int[]> ids = new ArrayList<>();            // courseId, credits, students, upcoming
            List<String[]> texts = new ArrayList<>();       // name, semester
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(new int[]{rs.getInt("Course_ID"), rs.getInt("Credits"),
                                rs.getInt("student_count"), rs.getInt("upcoming")});
                        texts.add(new String[]{rs.getString("CName"), rs.getString("semester")});
                    }
                }
            }
            if (ids.isEmpty()) {
                return new ArrayList<CourseCard>();
            }
            List<Integer> courseIds = ids.stream().map(a -> a[0]).toList();
            Map<Integer, List<String>> instructors = loadInstructors(conn, courseIds);
            Map<Integer, List<String>> topics = loadTopics(conn, courseIds);

            List<CourseCard> cards = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                int[] a = ids.get(i);
                cards.add(new CourseCard(a[0], texts.get(i)[0], a[1],
                        instructors.getOrDefault(a[0], List.of()),
                        topics.getOrDefault(a[0], List.of()),
                        texts.get(i)[1], a[2], a[3]));
            }
            return cards;
        });
    }

    @Override
    public List<ExamCard> listCourseExams(int studentId, int courseId) throws ServiceException {
        return db(conn -> loadExamCards(conn, studentId, courseId, null, false));
    }

    @Override
    public ExamCard registerForExam(int studentId, int examId) throws ServiceException {
        return db(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT Exam_date, Duration, Status FROM EXAMINATION WHERE Exam_ID = ?")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
                    }
                    if (!"PUBLISHED".equals(rs.getString("Status"))) {
                        throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
                    }
                    Timestamp ts = rs.getTimestamp("Exam_date");
                    if (ts != null && LocalDateTime.now()
                            .isAfter(ts.toLocalDateTime().plusMinutes(rs.getInt("Duration")))) {
                        throw new ServiceException(ServiceException.Kind.CONFLICT,
                                "This exam is already closed.");
                    }
                }
            }
            loadProfile(conn, studentId); // NOT_FOUND when the id is not a student
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO REGISTRATION (RegisDate, User_ID, Exam_ID) VALUES (?, ?, ?)")) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ps.setInt(2, studentId);
                ps.setInt(3, examId);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062) { // duplicate (User_ID, Exam_ID)
                    throw new ServiceException(ServiceException.Kind.CONFLICT,
                            "You are already registered for this exam.");
                }
                throw e;
            }
            return loadExamCards(conn, studentId, null, examId, true).get(0);
        });
    }

    // ---- Taking an exam ---------------------------------------------------

    /** Extra seconds accepted for a late save (network delay) after the deadline. */
    private static final int SAVE_GRACE_SECONDS = 20;
    private static final int MAX_ANSWER_CHARS = 5000;

    @Override
    public AttemptSession startAttempt(int regisId) throws ServiceException {
        return db(conn -> tx(conn, c -> {
            RegInfo reg = loadRegInfo(c, regisId);
            checkOwner(reg.ownerId());
            LocalDateTime now = LocalDateTime.now().withNano(0);

            // 1. Resume the unfinished attempt (if its time has not run out).
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Attempt_no, StartTime, PaperID FROM ATTEMPT " +
                    "WHERE RegisID = ? AND EndTime = StartTime ORDER BY Attempt_no DESC LIMIT 1")) {
                ps.setInt(1, regisId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int no = rs.getInt("Attempt_no");
                        LocalDateTime start = rs.getTimestamp("StartTime").toLocalDateTime();
                        long left = secondsLeft(start, reg.duration(), now);
                        if (left > 0) {
                            return buildSession(c, reg, new AttemptKey(regisId, no),
                                    rs.getInt("PaperID"), left);
                        }
                        // Time ran out while the student was away: close it with what was saved.
                        gradeAndClose(c, regisId, no, start.plusMinutes(reg.duration()));
                    }
                }
            }

            // 2. Start a new attempt.
            int used = 0;
            int nextNo = 1;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), COALESCE(MAX(Attempt_no), 0) FROM ATTEMPT WHERE RegisID = ?")) {
                ps.setInt(1, regisId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        used = rs.getInt(1);
                        nextNo = rs.getInt(2) + 1;
                    }
                }
            }
            if (used >= reg.maxAttempts()) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "No attempts left for this exam.");
            }
            if (reg.date() != null && now.isBefore(reg.date())) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This exam is not open yet.");
            }
            if (reg.date() != null && now.isAfter(reg.date().plusMinutes(reg.duration()))) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This exam is already closed.");
            }
            Integer paperId = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT MIN(PaperID) FROM PAPER WHERE Exam_ID = ?")) {
                ps.setInt(1, reg.examId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int v = rs.getInt(1);
                        paperId = rs.wasNull() ? null : v;
                    }
                }
            }
            if (paperId == null) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This exam has no paper yet.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ATTEMPT (Attempt_no, RegisID, PaperID, Exam_ID, StartTime, EndTime) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                Timestamp t = Timestamp.valueOf(now);
                ps.setInt(1, nextNo);
                ps.setInt(2, regisId);
                ps.setInt(3, paperId);
                ps.setInt(4, reg.examId());
                ps.setTimestamp(5, t);
                ps.setTimestamp(6, t); // EndTime = StartTime means "in progress"
                ps.executeUpdate();
            }
            // buildSession throws CONFLICT when the paper has no questions -> insert is rolled back.
            return buildSession(c, reg, new AttemptKey(regisId, nextNo), paperId, reg.duration() * 60L);
        }));
    }

    @Override
    public void saveAnswer(AttemptKey key, int questionId, AnswerInput input) throws ServiceException {
        db(conn -> {
            AttemptRow at = loadAttempt(conn, key, false);
            checkOwner(at.ownerId());
            if (!at.inProgress()) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This attempt has already been submitted.");
            }
            if (LocalDateTime.now().isAfter(
                    at.start().plusMinutes(at.duration()).plusSeconds(SAVE_GRACE_SECONDS))) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Time is up for this attempt.");
            }
            Boolean isMcq = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT (m.Quest_ID IS NOT NULL) FROM CONTAINS ct " +
                    "LEFT JOIN MCQ m ON m.Quest_ID = ct.Quest_ID " +
                    "WHERE ct.Exam_ID = ? AND ct.PaperID = ? AND ct.Quest_ID = ?")) {
                ps.setInt(1, at.examId());
                ps.setInt(2, at.paperId());
                ps.setInt(3, questionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        isMcq = rs.getBoolean(1);
                    }
                }
            }
            if (isMcq == null) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND,
                        "This question is not part of the exam.");
            }

            AnswerInput in = input == null ? new AnswerInput(null, null) : input;
            String value; // null = no answer (row removed)
            if (isMcq) {
                value = null;
                if (in.selectedOptionNo() != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM OPTIONS WHERE Quest_ID = ? AND Option_no = ?")) {
                        ps.setInt(1, questionId);
                        ps.setInt(2, in.selectedOptionNo());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new ServiceException(ServiceException.Kind.VALIDATION,
                                        "That option does not belong to this question.");
                            }
                        }
                    }
                    value = String.valueOf(in.selectedOptionNo());
                }
            } else {
                String t = in.text();
                value = (t == null || t.isBlank()) ? null : t;
                if (value != null && value.length() > MAX_ANSWER_CHARS) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Answer is too long (max " + MAX_ANSWER_CHARS + " characters).");
                }
            }

            if (value == null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM ANSWER WHERE Quest_ID = ? AND Attempt_no = ? AND RegisID = ?")) {
                    ps.setInt(1, questionId);
                    ps.setInt(2, key.attemptNo());
                    ps.setInt(3, key.regisId());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) " +
                        "VALUES (?, ?, ?, ?, 0) ON DUPLICATE KEY UPDATE AnswerText = ?")) {
                    ps.setInt(1, questionId);
                    ps.setInt(2, key.attemptNo());
                    ps.setInt(3, key.regisId());
                    ps.setString(4, value);
                    ps.setString(5, value);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public AttemptResult submitAttempt(AttemptKey key) throws ServiceException {
        return db(conn -> tx(conn, c -> {
            AttemptRow at = loadAttempt(c, key, true); // row lock: no double submit
            checkOwner(at.ownerId());
            if (at.inProgress()) {
                gradeAndClose(c, key.regisId(), key.attemptNo(), LocalDateTime.now().withNano(0));
            } // already submitted: just return the result again (safe to call twice)
            List<AttemptResult> r = queryAttemptResults(c,
                    "a.RegisID = ? AND a.Attempt_no = ?", key.regisId(), key.attemptNo());
            if (r.isEmpty()) {
                throw new ServiceException(ServiceException.Kind.INTERNAL,
                        "Attempt has no questions, so no result can be shown.");
            }
            return r.get(0);
        }));
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
        return loadExamCards(conn, studentId, null, null, true);
    }

    /**
     * Exam cards for one student. Filters are optional: a course, a single exam, or only the
     * exams the student registered for. Exams without a registration come back NOT_REGISTERED.
     */
    private List<ExamCard> loadExamCards(Connection conn, int studentId, Integer courseId,
                                         Integer examId, boolean registeredOnly) throws SQLException {
        String paperQuestions = " FROM CONTAINS ct WHERE ct.Exam_ID = e.Exam_ID AND ct.PaperID = " +
                "(SELECT MIN(p.PaperID) FROM PAPER p WHERE p.Exam_ID = e.Exam_ID)";
        StringBuilder sql = new StringBuilder(
                "SELECT r.RegisID, e.Exam_ID, e.Course_ID, c.CName, e.Title, e.Exam_date, " +
                "e.Duration, e.Pass_mark, e.Max_attempts, " +
                "(SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = r.RegisID) AS used, " +
                "(SELECT COUNT(*) FROM ATTEMPT a WHERE a.RegisID = r.RegisID " +
                "   AND a.EndTime = a.StartTime) AS in_progress, " +
                "(SELECT COUNT(*)" + paperQuestions + ") AS question_count, " +
                "(SELECT COALESCE(SUM(ct.Marks), 0)" + paperQuestions + ") AS total_marks " +
                "FROM EXAMINATION e " +
                "JOIN COURSE c ON c.Course_ID = e.Course_ID " +
                "LEFT JOIN REGISTRATION r ON r.Exam_ID = e.Exam_ID AND r.User_ID = ? " +
                "WHERE e.Status = 'PUBLISHED'");
        List<Integer> params = new ArrayList<>();
        params.add(studentId);
        if (registeredOnly) {
            sql.append(" AND r.RegisID IS NOT NULL");
        }
        if (courseId != null) {
            sql.append(" AND e.Course_ID = ?");
            params.add(courseId);
        }
        if (examId != null) {
            sql.append(" AND e.Exam_ID = ?");
            params.add(examId);
        }
        sql.append(" ORDER BY e.Exam_date, e.Exam_ID");

        LocalDateTime now = LocalDateTime.now();
        List<ExamCard> cards = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setInt(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int regisId = rs.getInt("RegisID");
                    boolean registered = !rs.wasNull();
                    Timestamp ts = rs.getTimestamp("Exam_date");
                    LocalDateTime date = ts == null ? null : ts.toLocalDateTime();
                    int duration = rs.getInt("Duration");
                    int used = rs.getInt("used");
                    int max = rs.getInt("Max_attempts");
                    ExamState state;
                    if (!registered) {
                        state = ExamState.NOT_REGISTERED;
                    } else if (rs.getInt("in_progress") > 0) {
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
                            rs.getBigDecimal("Pass_mark"), max,
                            registered ? regisId : null, registered ? used : 0, state));
                }
            }
        }
        return cards;
    }

    private Map<Integer, List<String>> loadInstructors(Connection conn, List<Integer> courseIds)
            throws SQLException {
        String sql = "SELECT th.Course_ID, u.FirstName, u.LastName FROM TEACH th " +
                "JOIN `USER` u ON u.User_ID = th.User_ID WHERE th.Course_ID IN (" +
                placeholders(courseIds.size()) + ") ORDER BY u.LastName, u.FirstName";
        Map<Integer, Set<String>> tmp = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < courseIds.size(); i++) {
                ps.setInt(i + 1, courseIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tmp.computeIfAbsent(rs.getInt(1), k -> new LinkedHashSet<>())
                            .add((rs.getString(2) + " " + rs.getString(3)).trim());
                }
            }
        }
        Map<Integer, List<String>> out = new HashMap<>();
        tmp.forEach((k, v) -> out.put(k, new ArrayList<>(v)));
        return out;
    }

    private Map<Integer, List<String>> loadTopics(Connection conn, List<Integer> courseIds)
            throws SQLException {
        String sql = "SELECT cv.Course_ID, tp.Content FROM COVERS cv " +
                "JOIN TOPIC tp ON tp.Topic_ID = cv.Topic_ID WHERE cv.Course_ID IN (" +
                placeholders(courseIds.size()) + ") ORDER BY tp.Content";
        Map<Integer, List<String>> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < courseIds.size(); i++) {
                ps.setInt(i + 1, courseIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
            }
        }
        return out;
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Escapes LIKE wildcards so a keyword is matched literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Submitted attempts of the student, newest first. */
    private List<AttemptResult> loadFinishedAttempts(Connection conn, int studentId) throws SQLException {
        return queryAttemptResults(conn, "r.User_ID = ? AND a.EndTime > a.StartTime", studentId);
    }

    /** Result rows (score per attempt) for the given WHERE condition on ATTEMPT a / REGISTRATION r. */
    private List<AttemptResult> queryAttemptResults(Connection conn, String where, Object... params)
            throws SQLException {
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
                "WHERE " + where + " " +
                "GROUP BY a.RegisID, a.Attempt_no, a.EndTime, e.Exam_ID, e.Title, c.CName, e.Pass_mark " +
                "ORDER BY a.EndTime DESC, a.RegisID DESC, a.Attempt_no DESC";
        List<AttemptResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
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

    // ---- Exam-taking helpers ---------------------------------------------

    private record RegInfo(int regisId, int ownerId, int examId, String title, String courseName,
                           LocalDateTime date, int duration, int maxAttempts) {
    }

    private record AttemptRow(int paperId, int examId, LocalDateTime start, LocalDateTime end,
                              int ownerId, int duration) {
        boolean inProgress() {
            return end.equals(start);
        }
    }

    private RegInfo loadRegInfo(Connection conn, int regisId) throws SQLException, ServiceException {
        String sql = "SELECT r.User_ID, e.Exam_ID, e.Title, e.Exam_date, e.Duration, e.Max_attempts, co.CName, e.Status " +
                "FROM REGISTRATION r JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "JOIN COURSE co ON co.Course_ID = e.Course_ID WHERE r.RegisID = ? FOR UPDATE OF r";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Registration not found.");
                }
                if (!"PUBLISHED".equals(rs.getString("Status"))) {
                    throw new ServiceException(ServiceException.Kind.CONFLICT,
                            "This exam is not available.");
                }
                Timestamp ts = rs.getTimestamp("Exam_date");
                return new RegInfo(regisId, rs.getInt("User_ID"), rs.getInt("Exam_ID"),
                        rs.getString("Title"), rs.getString("CName"),
                        ts == null ? null : ts.toLocalDateTime(),
                        rs.getInt("Duration"), rs.getInt("Max_attempts"));
            }
        }
    }

    private AttemptRow loadAttempt(Connection conn, AttemptKey key, boolean lock)
            throws SQLException, ServiceException {
        String sql = "SELECT a.PaperID, a.Exam_ID, a.StartTime, a.EndTime, r.User_ID, e.Duration " +
                "FROM ATTEMPT a JOIN REGISTRATION r ON r.RegisID = a.RegisID " +
                "JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID " +
                "WHERE a.RegisID = ? AND a.Attempt_no = ?" + (lock ? " FOR UPDATE OF a" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, key.regisId());
            ps.setInt(2, key.attemptNo());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Attempt not found.");
                }
                return new AttemptRow(rs.getInt("PaperID"), rs.getInt("Exam_ID"),
                        rs.getTimestamp("StartTime").toLocalDateTime(),
                        rs.getTimestamp("EndTime").toLocalDateTime(),
                        rs.getInt("User_ID"), rs.getInt("Duration"));
            }
        }
    }

    /** Questions of the paper (without the correct flags) plus the answers already saved. */
    private AttemptSession buildSession(Connection conn, RegInfo reg, AttemptKey key, int paperId,
                                        long secondsRemaining) throws SQLException, ServiceException {
        List<ExamQuestion> questions = new ArrayList<>();
        Map<Integer, QuestionType> types = new HashMap<>();
        List<int[]> order = new ArrayList<>();
        String sql = "SELECT ct.Quest_ID, ct.QuestionOrder, ct.Marks, q.Content, " +
                "(m.Quest_ID IS NOT NULL) AS is_mcq " +
                "FROM CONTAINS ct JOIN QUESTION q ON q.Quest_ID = ct.Quest_ID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = ct.Quest_ID " +
                "WHERE ct.Exam_ID = ? AND ct.PaperID = ? ORDER BY ct.QuestionOrder, ct.Quest_ID";
        List<Object[]> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reg.examId());
            ps.setInt(2, paperId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getInt("Quest_ID"), rs.getInt("QuestionOrder"),
                            rs.getBigDecimal("Marks"), rs.getString("Content"), rs.getBoolean("is_mcq")});
                }
            }
        }
        if (rows.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.CONFLICT,
                    "This exam has no questions yet.");
        }
        List<Integer> mcqIds = new ArrayList<>();
        for (Object[] r : rows) {
            if ((Boolean) r[4]) {
                mcqIds.add((Integer) r[0]);
            }
        }
        Map<Integer, List<ExamOption>> options = new HashMap<>();
        if (!mcqIds.isEmpty()) {
            String osql = "SELECT Quest_ID, Option_no, Op_Content FROM OPTIONS WHERE Quest_ID IN (" +
                    placeholders(mcqIds.size()) + ") ORDER BY Quest_ID, Option_no"; // never Bool_Correct
            try (PreparedStatement ps = conn.prepareStatement(osql)) {
                for (int i = 0; i < mcqIds.size(); i++) {
                    ps.setInt(i + 1, mcqIds.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        options.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                                .add(new ExamOption(rs.getInt(2), rs.getString(3)));
                    }
                }
            }
        }
        for (Object[] r : rows) {
            int qid = (Integer) r[0];
            boolean mcq = (Boolean) r[4];
            QuestionType type = mcq ? QuestionType.MCQ : QuestionType.SHORT_ANSWER;
            types.put(qid, type);
            questions.add(new ExamQuestion(qid, (Integer) r[1], type, (String) r[3], (BigDecimal) r[2],
                    mcq ? options.getOrDefault(qid, List.of()) : List.of()));
        }

        Map<Integer, AnswerInput> saved = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Quest_ID, AnswerText FROM ANSWER WHERE RegisID = ? AND Attempt_no = ?")) {
            ps.setInt(1, key.regisId());
            ps.setInt(2, key.attemptNo());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QuestionType type = types.get(rs.getInt(1));
                    String text = rs.getString(2);
                    if (type == QuestionType.MCQ) {
                        Integer no = parseOptionNo(text);
                        if (no != null) {
                            saved.put(rs.getInt(1), new AnswerInput(no, null));
                        }
                    } else if (type != null && text != null) {
                        saved.put(rs.getInt(1), new AnswerInput(null, text));
                    }
                }
            }
        }
        return new AttemptSession(key, reg.title(), reg.courseName(), reg.maxAttempts(),
                secondsRemaining, questions, saved);
    }

    /** Grades every saved answer of the attempt and marks the attempt as submitted. */
    private void gradeAndClose(Connection conn, int regisId, int attemptNo, LocalDateTime endTime)
            throws SQLException {
        String sql = "SELECT ct.Quest_ID, ct.Marks, (m.Quest_ID IS NOT NULL) AS is_mcq, " +
                "ans.AnswerText, sa.Answer_key " +
                "FROM ATTEMPT a " +
                "JOIN CONTAINS ct ON ct.PaperID = a.PaperID AND ct.Exam_ID = a.Exam_ID " +
                "JOIN ANSWER ans ON ans.Quest_ID = ct.Quest_ID AND ans.Attempt_no = a.Attempt_no " +
                "  AND ans.RegisID = a.RegisID " +
                "LEFT JOIN MCQ m ON m.Quest_ID = ct.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = ct.Quest_ID " +
                "WHERE a.RegisID = ? AND a.Attempt_no = ?";
        List<Object[]> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regisId);
            ps.setInt(2, attemptNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getInt("Quest_ID"), rs.getBigDecimal("Marks"),
                            rs.getBoolean("is_mcq"), rs.getString("AnswerText"), rs.getString("Answer_key")});
                }
            }
        }

        Map<Integer, Set<Integer>> correct = new HashMap<>();
        List<Integer> mcqIds = new ArrayList<>();
        for (Object[] r : rows) {
            if ((Boolean) r[2]) {
                mcqIds.add((Integer) r[0]);
            }
        }
        if (!mcqIds.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT Quest_ID, Option_no FROM OPTIONS WHERE Bool_Correct = 1 AND Quest_ID IN (" +
                            placeholders(mcqIds.size()) + ")")) {
                for (int i = 0; i < mcqIds.size(); i++) {
                    ps.setInt(i + 1, mcqIds.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        correct.computeIfAbsent(rs.getInt(1), k -> new LinkedHashSet<>()).add(rs.getInt(2));
                    }
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ANSWER SET MarkAwarded = ? WHERE Quest_ID = ? AND Attempt_no = ? AND RegisID = ?")) {
            for (Object[] r : rows) {
                int qid = (Integer) r[0];
                BigDecimal marks = (BigDecimal) r[1];
                boolean ok = (Boolean) r[2]
                        ? isCorrectOption((String) r[3], correct.get(qid))
                        : isCorrectText((String) r[3], (String) r[4]);
                ps.setBigDecimal(1, ok ? marks : BigDecimal.ZERO);
                ps.setInt(2, qid);
                ps.setInt(3, attemptNo);
                ps.setInt(4, regisId);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // EndTime must end up > StartTime, otherwise the attempt would still look "in progress".
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ATTEMPT SET EndTime = GREATEST(?, DATE_ADD(StartTime, INTERVAL 1 SECOND)) " +
                "WHERE RegisID = ? AND Attempt_no = ? AND EndTime = StartTime")) {
            ps.setTimestamp(1, Timestamp.valueOf(endTime));
            ps.setInt(2, regisId);
            ps.setInt(3, attemptNo);
            ps.executeUpdate();
        }
    }

    static boolean isCorrectOption(String answerText, Set<Integer> correctOptions) {
        Integer no = parseOptionNo(answerText);
        return no != null && correctOptions != null && correctOptions.contains(no);
    }

    static boolean isCorrectText(String answer, String key) {
        return answer != null && key != null && answer.trim().equalsIgnoreCase(key.trim());
    }

    private static Integer parseOptionNo(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long secondsLeft(LocalDateTime start, int durationMin, LocalDateTime now) {
        return Math.max(0, java.time.Duration.between(now, start.plusMinutes(durationMin)).getSeconds());
    }

    /** A student may only touch their own registration (when someone is logged in). */
    private static void checkOwner(int ownerId) throws ServiceException {
        if (Session.isLoggedIn() && Session.user().userId() != ownerId) {
            throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                    "This exam belongs to another student.");
        }
    }

    private static <T> T tx(Connection conn, DbWork<T> work) throws SQLException, ServiceException {
        conn.setAutoCommit(false);
        try {
            T result = work.run(conn);
            conn.commit();
            return result;
        } catch (SQLException | ServiceException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
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
}
