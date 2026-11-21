package org.example.Service;

import org.example.DAO.jdbc.AttemptDAO;
import org.example.DAO.jdbc.StudentDAO;
import org.example.session.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Student use cases. This class holds the rules (exam window, attempts left, grading, who may do
 * what) and the transactions; every SQL statement lives in {@link StudentDAO} and {@link AttemptDAO}.
 *
 * Conventions (21-table schema):
 *  - exam is open for new attempts from Exam_date until Exam_date + Duration; each attempt has the
 *    full Duration from its own StartTime; registration closes at Exam_date (trigger rule);
 *  - an attempt is IN PROGRESS while ATTEMPT.EndTime IS NULL, submitted once EndTime is set;
 *  - MCQ is single-select (SELECT_TABLE holds the chosen option, ANSWER.AnswerText stays NULL);
 *    short answer gets full marks when it equals Answer_key (trim, ignore case), otherwise 0;
 *    grading happens when the attempt is submitted, unanswered questions get 0;
 *  - score = SUM(ANSWER.MarkAwarded), max = SUM(EXAM_QUESTION_MARKS.Marks) of the attempt's paper;
 *  - EXAMINATION.Pass_mark is a PERCENTAGE: passed = score / max * 100 >= Pass_mark;
 *  - the paper shown on a card / handed to a new attempt is the exam's lowest PaperID.
 */
public class StudentServiceImpl implements StudentService {

    /** Extra seconds accepted for a late save (network delay) after the deadline. */
    private static final int SAVE_GRACE_SECONDS = 20;
    private static final int MAX_ANSWER_CHARS = 5000;

    // ---- Overview ---------------------------------------------------------

    @Override
    public StudentOverview getOverview(int studentId) throws ServiceException {
        return Db.run(conn -> {
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
        return Db.run(conn -> loadRegisteredExams(conn, studentId));
    }

    @Override
    public List<AttemptResult> listResults(int studentId) throws ServiceException {
        return Db.run(conn -> loadFinishedAttempts(conn, studentId));
    }

    // ---- Courses / registration -------------------------------------------

    @Override
    public CourseFilterOptions getCourseFilterOptions() throws ServiceException {
        return Db.run(conn -> new CourseFilterOptions(StudentDAO.filterTopics(conn),
                StudentDAO.filterSemesters(conn)));
    }

    @Override
    public List<CourseCard> listCourses(int studentId, CourseScope scope, CourseFilter filter)
            throws ServiceException {
        return Db.run(conn -> {
            CourseFilter f = filter == null ? CourseFilter.none() : filter;
            List<StudentDAO.CourseRow> rows = StudentDAO.listCourses(conn, studentId,
                    scope == CourseScope.MY, f.keyword(), f.topic(), f.semester(),
                    Timestamp.valueOf(LocalDateTime.now()));
            List<CourseCard> cards = new ArrayList<>();
            if (rows.isEmpty()) {
                return cards;
            }
            List<Integer> courseIds = rows.stream().map(StudentDAO.CourseRow::courseId).toList();
            Map<Integer, List<String>> instructors = StudentDAO.instructorNames(conn, courseIds);
            Map<Integer, List<String>> topics = StudentDAO.topicNames(conn, courseIds);
            for (StudentDAO.CourseRow r : rows) {
                cards.add(new CourseCard(r.courseId(), r.name(), r.credits(),
                        instructors.getOrDefault(r.courseId(), List.of()),
                        topics.getOrDefault(r.courseId(), List.of()),
                        r.semester(), r.students(), r.upcoming()));
            }
            return cards;
        });
    }

    @Override
    public List<ExamCard> listCourseExams(int studentId, int courseId) throws ServiceException {
        return Db.run(conn -> loadExamCards(conn, studentId, courseId, null, false));
    }

    @Override
    public ExamCard registerForExam(int studentId, int examId) throws ServiceException {
        return Db.run(conn -> {
            StudentDAO.ExamWindow w = StudentDAO.findExamWindow(conn, examId);
            if (w == null || !"PUBLISHED".equals(w.status())) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
            }
            checkOwner(studentId);
            LocalDateTime now = LocalDateTime.now();
            // The database rule is RegisDate <= Exam_date: registration closes when the exam starts.
            if (w.date() != null && now.isAfter(w.date().toLocalDateTime())) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Registration is closed: this exam has already started.");
            }
            loadProfile(conn, studentId); // NOT_FOUND when the id is not a student
            try {
                StudentDAO.register(conn, studentId, examId, Timestamp.valueOf(now.withNano(0)));
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

    @Override
    public AttemptSession startAttempt(int regisId) throws ServiceException {
        return Db.run(conn -> Db.tx(conn, c -> {
            AttemptDAO.RegInfo reg = loadReg(c, regisId);
            checkOwner(reg.ownerId());
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime date = toLdt(reg.date());

            // 1. Resume the unfinished attempt (if its time has not run out).
            AttemptDAO.InProgress open = AttemptDAO.findInProgress(c, regisId);
            if (open != null) {
                LocalDateTime start = open.start().toLocalDateTime();
                long left = secondsLeft(start, reg.duration(), now);
                if (left > 0) {
                    return buildSession(c, reg, new AttemptKey(regisId, open.attemptNo()), open.paperKey(), left);
                }
                // Time ran out while the student was away: close it with what was saved.
                gradeAndClose(c, regisId, open.attemptNo(), start.plusMinutes(reg.duration()));
            }

            // 2. Start a new attempt.
            if (AttemptDAO.countAttempts(c, regisId) >= reg.maxAttempts()) {
                throw new ServiceException(ServiceException.Kind.CONFLICT, "No attempts left for this exam.");
            }
            if (date != null && now.isBefore(date)) {
                throw new ServiceException(ServiceException.Kind.CONFLICT, "This exam is not open yet.");
            }
            if (date != null && now.isAfter(date.plusMinutes(reg.duration()))) {
                throw new ServiceException(ServiceException.Kind.CONFLICT, "This exam is already closed.");
            }
            Integer paperKey = AttemptDAO.firstPaperKey(c, reg.examId());
            if (paperKey == null) {
                throw new ServiceException(ServiceException.Kind.CONFLICT, "This exam has no paper yet.");
            }
            int nextNo = AttemptDAO.highestAttemptNo(c, regisId) + 1;
            AttemptDAO.insert(c, nextNo, regisId, paperKey, Timestamp.valueOf(now));
            // buildSession throws CONFLICT when the paper has no questions -> the insert is rolled back.
            return buildSession(c, reg, new AttemptKey(regisId, nextNo), paperKey, reg.duration() * 60L);
        }));
    }

    @Override
    public void saveAnswer(AttemptKey key, int questionId, AnswerInput input) throws ServiceException {
        Db.run(conn -> Db.tx(conn, c -> {
            AttemptDAO.Row at = loadAttempt(c, key, false);
            checkOwner(at.ownerId());
            if (!at.inProgress()) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This attempt has already been submitted.");
            }
            if (LocalDateTime.now().isAfter(at.start().toLocalDateTime().plusMinutes(at.duration())
                    .plusSeconds(SAVE_GRACE_SECONDS))) {
                throw new ServiceException(ServiceException.Kind.CONFLICT, "Time is up for this attempt.");
            }
            Boolean isMcq = AttemptDAO.questionKindOnPaper(c, at.paperKey(), questionId);
            if (isMcq == null) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND,
                        "This question is not part of the exam.");
            }

            AnswerInput in = input == null ? new AnswerInput(null, null) : input;
            if (isMcq) {
                if (in.selectedOptionNo() == null) {
                    AttemptDAO.clearAnswer(c, key.regisId(), key.attemptNo(), questionId);
                } else {
                    if (!AttemptDAO.optionExists(c, questionId, in.selectedOptionNo())) {
                        throw new ServiceException(ServiceException.Kind.VALIDATION,
                                "That option does not belong to this question.");
                    }
                    AttemptDAO.saveMcq(c, key.regisId(), key.attemptNo(), questionId, in.selectedOptionNo());
                }
            } else {
                String t = in.text();
                if (t == null || t.isBlank()) {
                    AttemptDAO.clearAnswer(c, key.regisId(), key.attemptNo(), questionId);
                } else {
                    if (t.length() > MAX_ANSWER_CHARS) {
                        throw new ServiceException(ServiceException.Kind.VALIDATION,
                                "Answer is too long (max " + MAX_ANSWER_CHARS + " characters).");
                    }
                    AttemptDAO.saveText(c, key.regisId(), key.attemptNo(), questionId, t);
                }
            }
            return null;
        }));
    }

    @Override
    public AttemptResult submitAttempt(AttemptKey key) throws ServiceException {
        return Db.run(conn -> Db.tx(conn, c -> {
            AttemptDAO.Row at = loadAttempt(c, key, true); // row lock: no double submit
            checkOwner(at.ownerId());
            if (at.inProgress()) {
                gradeAndClose(c, key.regisId(), key.attemptNo(), LocalDateTime.now().withNano(0));
            } // already submitted: just return the result again (safe to call twice)
            List<AttemptResult> r = toResults(AttemptDAO.finishedOne(c, key.regisId(), key.attemptNo()));
            if (r.isEmpty()) {
                throw new ServiceException(ServiceException.Kind.INTERNAL,
                        "Attempt has no questions, so no result can be shown.");
            }
            return r.get(0);
        }));
    }

    // ---- Loading ----------------------------------------------------------

    private static StudentProfile loadProfile(Connection conn, int studentId)
            throws SQLException, ServiceException {
        StudentDAO.Profile p = StudentDAO.findProfile(conn, studentId);
        if (p == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Student not found.");
        }
        return new StudentProfile(studentId, (p.firstName() + " " + p.lastName()).trim(), p.enrollmentYear());
    }

    /** Every exam the student registered for, soonest first. */
    private static List<ExamCard> loadRegisteredExams(Connection conn, int studentId) throws SQLException {
        return loadExamCards(conn, studentId, null, null, true);
    }

    private static List<ExamCard> loadExamCards(Connection conn, int studentId, Integer courseId,
                                                Integer examId, boolean registeredOnly) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        List<ExamCard> cards = new ArrayList<>();
        for (StudentDAO.ExamCardRow r : StudentDAO.examCards(conn, studentId, courseId, examId, registeredOnly)) {
            boolean registered = r.regisId() != null;
            LocalDateTime date = toLdt(r.date());
            ExamState state;
            if (!registered) {
                state = ExamState.NOT_REGISTERED;
            } else if (r.inProgress() > 0) {
                state = ExamState.IN_PROGRESS;
            } else if (r.used() >= r.maxAttempts() || (date != null && now.isAfter(date.plusMinutes(r.duration())))) {
                state = ExamState.COMPLETED;
            } else if (date != null && now.isBefore(date)) {
                state = ExamState.UPCOMING;
            } else {
                state = ExamState.OPEN;
            }
            cards.add(new ExamCard(r.examId(), r.courseId(), r.courseName(), r.title(), date, r.duration(),
                    r.questionCount(), r.totalMarks(), r.passMark(), r.maxAttempts(),
                    r.regisId(), registered ? r.used() : 0, state));
        }
        return cards;
    }

    /** Submitted attempts of the student, newest first. */
    private static List<AttemptResult> loadFinishedAttempts(Connection conn, int studentId) throws SQLException {
        return toResults(AttemptDAO.finishedByStudent(conn, studentId));
    }

    private static List<AttemptResult> toResults(List<AttemptDAO.ResultRow> rows) {
        List<AttemptResult> out = new ArrayList<>();
        for (AttemptDAO.ResultRow r : rows) {
            BigDecimal score = r.score();
            BigDecimal max = r.max();
            BigDecimal percent = max.signum() == 0 ? BigDecimal.ZERO
                    : score.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
            BigDecimal exactPercent = max.signum() == 0 ? BigDecimal.ZERO
                    : score.multiply(BigDecimal.valueOf(100)).divide(max, 4, RoundingMode.HALF_UP);
            boolean fullyGraded = r.ungraded() == 0;
            out.add(new AttemptResult(new AttemptKey(r.regisId(), r.attemptNo()), r.examId(), r.title(),
                    r.courseName(), r.end().toLocalDateTime(), score, max, percent,
                    fullyGraded && exactPercent.compareTo(r.passMark()) >= 0, fullyGraded));
        }
        return out;
    }

    // ---- Exam-taking helpers ---------------------------------------------

    private static AttemptDAO.RegInfo loadReg(Connection conn, int regisId) throws SQLException, ServiceException {
        AttemptDAO.RegInfo reg = AttemptDAO.findRegistrationForUpdate(conn, regisId);
        if (reg == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Registration not found.");
        }
        if (!"PUBLISHED".equals(reg.status())) {
            throw new ServiceException(ServiceException.Kind.CONFLICT, "This exam is not available.");
        }
        return reg;
    }

    private static AttemptDAO.Row loadAttempt(Connection conn, AttemptKey key, boolean lock)
            throws SQLException, ServiceException {
        AttemptDAO.Row row = AttemptDAO.find(conn, key.regisId(), key.attemptNo(), lock);
        if (row == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Attempt not found.");
        }
        return row;
    }

    /** Questions of the paper (without the correct flags) plus the answers already saved. */
    private static AttemptSession buildSession(Connection conn, AttemptDAO.RegInfo reg, AttemptKey key,
                                               int paperKey, long secondsRemaining)
            throws SQLException, ServiceException {
        List<AttemptDAO.QuestionRow> rows = AttemptDAO.paperQuestions(conn, paperKey, reg.examId());
        if (rows.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.CONFLICT, "This exam has no questions yet.");
        }
        List<Integer> mcqIds = new ArrayList<>();
        for (AttemptDAO.QuestionRow r : rows) {
            if (r.mcq()) {
                mcqIds.add(r.questionId());
            }
        }
        Map<Integer, List<AttemptDAO.OptionRow>> options = AttemptDAO.options(conn, mcqIds);

        Map<Integer, QuestionType> types = new HashMap<>();
        List<ExamQuestion> questions = new ArrayList<>();
        for (AttemptDAO.QuestionRow r : rows) {
            QuestionType type = r.mcq() ? QuestionType.MCQ : QuestionType.SHORT_ANSWER;
            types.put(r.questionId(), type);
            List<ExamOption> opts = new ArrayList<>();
            if (r.mcq()) {
                for (AttemptDAO.OptionRow o : options.getOrDefault(r.questionId(), List.of())) {
                    opts.add(new ExamOption(o.optionNo(), o.content()));
                }
            }
            questions.add(new ExamQuestion(r.questionId(), r.order(), type, r.content(), r.marks(), opts));
        }

        Map<Integer, AnswerInput> saved = new LinkedHashMap<>();
        for (AttemptDAO.SavedAnswer a : AttemptDAO.savedAnswers(conn, key.regisId(), key.attemptNo())) {
            QuestionType type = types.get(a.questionId());
            if (type == QuestionType.MCQ && a.optionNo() != null) {
                saved.put(a.questionId(), new AnswerInput(a.optionNo(), null));
            } else if (type == QuestionType.SHORT_ANSWER && a.text() != null) {
                saved.put(a.questionId(), new AnswerInput(null, a.text()));
            }
        }
        return new AttemptSession(key, reg.title(), reg.courseName(), reg.maxAttempts(),
                secondsRemaining, questions, saved);
    }

    /**
     * Grades every question of the attempt (unanswered = 0) and marks the attempt as submitted.
     * MCQ: full marks when the chosen option is the correct one; short answer: see class comment.
     */
    private static void gradeAndClose(Connection conn, int regisId, int attemptNo, LocalDateTime endTime)
            throws SQLException {
        Map<Integer, BigDecimal> marks = new LinkedHashMap<>();
        for (AttemptDAO.GradeRow r : AttemptDAO.gradingRows(conn, regisId, attemptNo)) {
            boolean ok = r.mcq() ? r.selectedOption() != null && r.selectedIsCorrect()
                    : isCorrectText(r.text(), r.answerKey());
            marks.put(r.questionId(), ok ? r.marks() : BigDecimal.ZERO);
        }
        AttemptDAO.writeMarks(conn, regisId, attemptNo, marks);
        AttemptDAO.close(conn, regisId, attemptNo, Timestamp.valueOf(endTime));
    }

    static boolean isCorrectOption(String answerText, Set<Integer> correctOptions) {
        if (answerText == null || correctOptions == null) {
            return false;
        }
        try {
            return correctOptions.contains(Integer.valueOf(answerText.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isCorrectText(String answer, String key) {
        return answer != null && key != null && answer.trim().equalsIgnoreCase(key.trim());
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
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
}
