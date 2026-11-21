package org.example.Service;

import org.example.DAO.jdbc.CourseDAO;
import org.example.DAO.jdbc.ExamDAO;
import org.example.DAO.jdbc.PaperDAO;
import org.example.DAO.jdbc.QuestionDAO;
import org.example.DAO.jdbc.StatsDAO;
import org.example.DAO.jdbc.UserDAO;
import org.example.session.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Instructor use cases. This class holds the rules (validation, who may do what, which attempt
 * counts, status handling) and the transactions; every SQL statement lives in the DAOs under
 * {@code org.example.DAO.jdbc}.
 *
 * Conventions:
 *  - EXAMINATION.Status is DRAFT or PUBLISHED; COMPLETED is derived (published and the exam
 *    window Exam_date + Duration has passed);
 *  - Exam_date NULL = "Not scheduled";
 *  - a paper is identified by (PaperID, Exam_ID); the UI's "paperKey" is the PaperID;
 *  - when somebody is logged in, an instructor can only touch courses he teaches;
 *  - D09: per student and exam, the best finished attempt counts in the statistics.
 */
public class InstructorServiceImpl implements InstructorService {

    private static final int MAX_PAPER_QUESTIONS = 200;
    private static final int MAX_QUESTION_CHARS = 5000;
    private static final int MAX_OPTIONS = 10;
    private static final int HARDEST_LIMIT = 5;
    // EXAM_QUESTION_MARKS.Marks is DECIMAL(5,2)
    private static final BigDecimal MAX_MARKS = new BigDecimal("999.99");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // ---- My courses -------------------------------------------------------

    @Override
    public List<TeachingCourse> listTeachingCourses(int instructorId) throws ServiceException {
        return Db.run(conn -> {
            if (Session.isLoggedIn() && Session.user().userId() != instructorId) {
                throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                        "You can only see your own courses.");
            }
            List<TeachingCourse> out = new ArrayList<>();
            for (CourseDAO.TeachingRow r : CourseDAO.listTeaching(conn, instructorId)) {
                out.add(new TeachingCourse(r.courseId(), r.name(), r.credits(), r.semester(),
                        r.students(), r.exams()));
            }
            return out;
        });
    }

    // ---- Exams tab --------------------------------------------------------

    @Override
    public List<ExamRow> listExams(int courseId) throws ServiceException {
        return Db.run(conn -> {
            checkTeaches(conn, courseId);
            LocalDateTime now = LocalDateTime.now();
            List<ExamRow> out = new ArrayList<>();
            for (ExamDAO.ListRow r : ExamDAO.list(conn, courseId)) {
                LocalDateTime date = toLdt(r.date());
                out.add(new ExamRow(r.examId(), r.title(), date, r.questionCount(), r.totalMarks(),
                        status(r.status(), date, r.duration(), now)));
            }
            return out;
        });
    }

    @Override
    public ExamDetail getExam(int examId) throws ServiceException {
        return Db.run(conn -> {
            ExamDAO.Head h = requireExam(conn, examId);
            checkTeaches(conn, h.courseId());
            LocalDateTime date = toLdt(h.date());

            Map<Integer, List<PaperQuestion>> byPaper = new LinkedHashMap<>();
            for (Integer paperId : ExamDAO.paperIds(conn, examId)) {
                byPaper.put(paperId, new ArrayList<>());
            }
            for (ExamDAO.PaperQuestionRow q : ExamDAO.paperQuestions(conn, examId)) {
                byPaper.computeIfAbsent(q.paperId(), k -> new ArrayList<>())
                        .add(new PaperQuestion(q.questionId(), q.order(), q.content(),
                                q.mcq() ? QuestionType.MCQ : QuestionType.SHORT_ANSWER, q.marks()));
            }
            List<PaperDetail> papers = new ArrayList<>();
            byPaper.forEach((paperId, qs) -> {
                BigDecimal total = BigDecimal.ZERO;
                for (PaperQuestion q : qs) {
                    total = total.add(q.marks());
                }
                papers.add(new PaperDetail(paperId, paperId, qs, total));
            });
            return new ExamDetail(examId, h.courseId(), h.title(), date, h.duration(), h.passMark(),
                    h.maxAttempts(), status(h.status(), date, h.duration(), LocalDateTime.now()), papers);
        });
    }

    @Override
    public int saveExam(ExamDraft d) throws ServiceException {
        if (d == null) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Nothing to save.");
        }
        String title = d.title() == null ? "" : d.title().trim();
        if (title.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Please enter an exam title.");
        }
        if (title.length() > 200) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "The title is too long (max 200 characters).");
        }
        if (d.durationMin() < 1 || d.durationMin() > 1440) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Duration must be between 1 and 1440 minutes.");
        }
        if (d.maxAttempts() < 1 || d.maxAttempts() > 20) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Maximum attempts must be between 1 and 20.");
        }
        // EXAMINATION.Pass_mark is a percentage of the paper's total marks.
        if (d.passMark() == null || d.passMark().signum() < 0 || d.passMark().compareTo(HUNDRED) > 0) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Pass mark is a percentage: enter a number from 0 to 100.");
        }
        Timestamp date = d.examDate() == null ? null : Timestamp.valueOf(d.examDate().withNano(0));

        return Db.run(conn -> Db.tx(conn, c -> {
            if (!CourseDAO.exists(c, d.courseId())) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Course not found.");
            }
            checkTeaches(c, d.courseId());

            if (d.examId() == null) {
                return ExamDAO.insert(c, title, date, d.durationMin(), d.passMark(), d.maxAttempts(),
                        d.courseId());
            }

            int examId = d.examId();
            ExamDAO.Head old = lockedExam(c, examId);
            if (old.courseId() != d.courseId()) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "An exam cannot be moved to another course.");
            }
            if (date == null && "PUBLISHED".equals(old.status())) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "A published exam needs a date. Unpublish it first to clear the date.");
            }
            if (ExamDAO.countAttempts(c, examId) > 0) {
                if (old.duration() != d.durationMin() || old.passMark().compareTo(d.passMark()) != 0) {
                    throw new ServiceException(ServiceException.Kind.CONFLICT,
                            "Duration and pass mark cannot change after students have attempted the exam.");
                }
                if (d.maxAttempts() < ExamDAO.mostAttemptsByOneStudent(c, examId)) {
                    throw new ServiceException(ServiceException.Kind.CONFLICT,
                            "Maximum attempts is lower than what a student has already used.");
                }
            }
            ExamDAO.update(c, examId, title, date, d.durationMin(), d.passMark(), d.maxAttempts());
            return examId;
        }));
    }

    @Override
    public void deleteExam(int examId) throws ServiceException {
        Db.run(conn -> Db.tx(conn, c -> {
            checkTeaches(c, requireExam(c, examId).courseId());
            if (ExamDAO.countAttempts(c, examId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted this exam, so it cannot be deleted.");
            }
            // Registrations without attempts are removed with the exam.
            ExamDAO.deleteWithPapers(c, examId);
            return null;
        }));
    }

    @Override
    public void setExamStatus(int examId, ExamStatus status) throws ServiceException {
        if (status == null || status == ExamStatus.COMPLETED) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Only Draft or Published can be set. Completed is worked out from the date.");
        }
        Db.run(conn -> Db.tx(conn, c -> {
            ExamDAO.Head h = lockedExam(c, examId);
            checkTeaches(c, h.courseId());
            if (status == ExamStatus.PUBLISHED) {
                if (h.date() == null) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Set a date for the exam before publishing it.");
                }
                if (ExamDAO.countPaperQuestions(c, examId) == 0) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Build a paper with at least one question before publishing.");
                }
            } else if (ExamDAO.countAttempts(c, examId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted this exam, so it cannot go back to draft.");
            }
            ExamDAO.setStatus(c, examId, status == ExamStatus.PUBLISHED ? "PUBLISHED" : "DRAFT");
            return null;
        }));
    }

    // ---- Build your paper -------------------------------------------------

    @Override
    public int savePaper(PaperDraft d) throws ServiceException {
        if (d == null || d.questions() == null || d.questions().isEmpty()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Add at least one question to the paper.");
        }
        if (d.questions().size() > MAX_PAPER_QUESTIONS) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "A paper can have at most " + MAX_PAPER_QUESTIONS + " questions.");
        }
        Set<Integer> seen = new HashSet<>();
        for (PaperQuestionDraft q : d.questions()) {
            if (!seen.add(q.questionId())) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "The same question is in the paper twice.");
            }
            if (q.marks() == null || q.marks().signum() <= 0 || q.marks().compareTo(MAX_MARKS) > 0) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "Marks must be greater than 0 and at most 999.99.");
            }
        }
        // The order that matters is the order given; store it as 1..n.
        List<PaperQuestionDraft> ordered = new ArrayList<>(d.questions());
        ordered.sort(Comparator.comparingInt(PaperQuestionDraft::order));

        return Db.run(conn -> Db.tx(conn, c -> {
            ExamDAO.Head h = lockedExam(c, d.examId());
            checkTeaches(c, h.courseId());
            if (ExamDAO.countAttempts(c, d.examId()) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted this exam, so its paper cannot change.");
            }
            Set<Integer> allowed = CourseDAO.questionIds(c, h.courseId());
            for (PaperQuestionDraft q : ordered) {
                if (!allowed.contains(q.questionId())) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Question " + q.questionId() + " is not in this course's question bank.");
                }
            }

            int paperId;
            if (d.paperKey() == null) {
                paperId = PaperDAO.nextPaperId(c, d.examId());
                PaperDAO.insert(c, d.examId(), paperId);
            } else {
                paperId = d.paperKey();
                if (!PaperDAO.exists(c, d.examId(), paperId)) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND,
                            "That paper does not belong to this exam.");
                }
                PaperDAO.clear(c, d.examId(), paperId);
            }
            List<PaperDAO.Item> items = new ArrayList<>();
            int order = 1;
            for (PaperQuestionDraft q : ordered) {
                items.add(new PaperDAO.Item(q.questionId(), order++, q.marks()));
            }
            PaperDAO.addQuestions(c, d.examId(), paperId, items);
            return paperId;
        }));
    }

    // ---- Question bank ----------------------------------------------------

    @Override
    public List<TopicOption> listTopics(int courseId) throws ServiceException {
        return Db.run(conn -> {
            checkTeaches(conn, courseId);
            List<TopicOption> out = new ArrayList<>();
            for (CourseDAO.TopicRow t : CourseDAO.listTopics(conn, courseId)) {
                out.add(new TopicOption(t.topicId(), t.name()));
            }
            return out;
        });
    }

    @Override
    public Page<QuestionBankItem> searchQuestionBank(int courseId, QuestionBankFilter f,
                                                     int page, int pageSize) throws ServiceException {
        int size = Math.max(1, Math.min(pageSize, 200));
        int pageNo = Math.max(1, page);
        QuestionBankFilter filter = f == null ? QuestionBankFilter.none() : f;
        Boolean mcq = filter.type() == null ? null : filter.type() == QuestionType.MCQ;

        return Db.run(conn -> {
            checkTeaches(conn, courseId);
            QuestionDAO.SearchResult res = QuestionDAO.search(conn, courseId, filter.keyword(),
                    filter.topicId(), mcq, filter.difficulty(), size, (long) (pageNo - 1) * size);
            List<QuestionBankItem> items = new ArrayList<>();
            for (QuestionDAO.BankRow r : res.rows()) {
                items.add(new QuestionBankItem(r.questionId(), r.content(),
                        r.mcq() ? QuestionType.MCQ : QuestionType.SHORT_ANSWER, r.difficulty(), r.topic()));
            }
            return new Page<>(items, pageNo, size, res.total());
        });
    }

    @Override
    public QuestionDraft getQuestion(int questionId) throws ServiceException {
        return Db.run(conn -> {
            checkQuestionAccess(conn, questionId);
            return loadQuestion(conn, questionId);
        });
    }

    @Override
    public int saveQuestion(int instructorId, QuestionDraft d) throws ServiceException {
        validateQuestion(d);
        return Db.run(conn -> Db.tx(conn, c -> {
            if (Session.isLoggedIn() && Session.user().userId() != instructorId) {
                throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                        "You can only save questions as yourself.");
            }
            if (!UserDAO.isInstructor(c, instructorId)) {
                throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                        "Only instructors can write questions.");
            }
            checkTopicAccess(c, d.topicId(), instructorId);

            String content = d.content().trim();
            if (d.questionId() == null) {
                int id = QuestionDAO.insert(c, content, d.difficulty(), d.topicId(), instructorId);
                insertSubtype(c, id, d);
                return id;
            }

            int id = d.questionId();
            checkQuestionAccess(c, id);
            if (!QuestionDAO.lock(c, id)) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
            }
            QuestionDraft old = loadQuestion(c, id);
            boolean answered = QuestionDAO.answerCount(c, id) > 0;
            boolean attempted = QuestionDAO.attemptedCount(c, id) > 0;
            boolean sameContent = Objects.equals(trimOrNull(old.content()), content);
            boolean sameBody = old.type() == d.type() && old.topicId() == d.topicId()
                    && sameOptions(old.options(), d.options())
                    && Objects.equals(trimOrNull(old.answerKey()), trimOrNull(d.answerKey()));
            // The database freezes a question once an attempt used it (historical freeze triggers).
            if ((answered || attempted) && (!sameBody || !sameContent)) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted an exam with this question. Only its " +
                        "difficulty can be changed now.");
            }
            if (!sameBody && QuestionDAO.paperUseCount(c, id) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This question is on an exam paper. Remove it from the paper(s) before " +
                        "changing its type, topic, options or answer key.");
            }
            QuestionDAO.update(c, id, content, d.difficulty(), d.topicId());
            if (!sameBody) {
                QuestionDAO.deleteSubtype(c, id);
                insertSubtype(c, id, d);
            }
            return id;
        }));
    }

    @Override
    public void deleteQuestion(int questionId) throws ServiceException {
        Db.run(conn -> Db.tx(conn, c -> {
            checkQuestionAccess(c, questionId);
            if (!QuestionDAO.exists(c, questionId)) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
            }
            if (QuestionDAO.answerCount(c, questionId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already answered this question, so it cannot be deleted.");
            }
            if (QuestionDAO.publishedExamUseCount(c, questionId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This question is in a published exam. Unpublish the exam or change its paper first.");
            }
            QuestionDAO.removeFromAllPapers(c, questionId); // draft exams only
            QuestionDAO.deleteSubtype(c, questionId);
            QuestionDAO.delete(c, questionId);
            return null;
        }));
    }

    // ---- Statistics tab ---------------------------------------------------
    //
    // D09 (chosen): per student and exam the BEST finished attempt counts (highest score, a later
    // attempt wins a tie). Draft exams are ignored everywhere on this tab.

    @Override
    public CourseStatistics getStatistics(int courseId) throws ServiceException {
        return Db.run(conn -> {
            checkTeaches(conn, courseId);

            int completed = 0;
            LocalDateTime now = LocalDateTime.now();
            for (StatsDAO.ExamTime t : StatsDAO.examTimes(conn, courseId)) {
                if (status(t.status(), toLdt(t.date()), t.duration(), now) == ExamStatus.COMPLETED) {
                    completed++;
                }
            }

            Set<String> best = new HashSet<>();
            for (StatsDAO.AttemptTotal a : bestAttempts(StatsDAO.attemptTotals(conn, courseId)).values()) {
                best.add(a.regisId() + ":" + a.attemptNo());
            }

            Map<String, BigDecimal[]> byTopic = new LinkedHashMap<>();  // topic -> {earned, max}
            Map<Integer, Object[]> byQuestion = new LinkedHashMap<>();  // id -> {content, correct, total}
            for (StatsDAO.QuestionMark m : StatsDAO.questionMarks(conn, courseId)) {
                if (!best.contains(m.regisId() + ":" + m.attemptNo())) {
                    continue;
                }
                BigDecimal[] t = byTopic.computeIfAbsent(m.topic(),
                        k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                t[0] = t[0].add(m.earned());
                t[1] = t[1].add(m.marks());
                Object[] q = byQuestion.computeIfAbsent(m.questionId(), k -> new Object[]{null, 0, 0});
                q[0] = m.content();
                q[1] = (Integer) q[1] + (m.earned().compareTo(m.marks()) >= 0 ? 1 : 0);
                q[2] = (Integer) q[2] + 1;
            }

            List<TopicAverage> topics = new ArrayList<>();
            byTopic.forEach((name, v) -> {
                if (v[1].signum() > 0) {
                    topics.add(new TopicAverage(name, percent(v[0], v[1])));
                }
            });
            topics.sort(Comparator.comparing(TopicAverage::topic, String.CASE_INSENSITIVE_ORDER));

            List<HardQuestion> hard = new ArrayList<>();
            byQuestion.forEach((id, v) -> {
                int ok = (Integer) v[1];
                int total = (Integer) v[2];
                if (ok < total) { // a question everybody got right is not "hard"
                    hard.add(new HardQuestion(id, (String) v[0],
                            percent(BigDecimal.valueOf(ok), BigDecimal.valueOf(total))));
                }
            });
            hard.sort(Comparator.comparing(HardQuestion::correctPercent)
                    .thenComparingInt(HardQuestion::questionId));
            List<HardQuestion> top = hard.size() > HARDEST_LIMIT ? hard.subList(0, HARDEST_LIMIT) : hard;

            return new CourseStatistics(completed, topics, new ArrayList<>(top));
        });
    }

    @Override
    public StudentMarkTable listStudentMarks(int courseId, boolean completedAllOnly,
                                             int page, int pageSize) throws ServiceException {
        int size = Math.max(1, Math.min(pageSize, 200));
        int pageNo = Math.max(1, page);
        return Db.run(conn -> {
            checkTeaches(conn, courseId);

            List<ExamColumn> columns = new ArrayList<>();
            for (StatsDAO.ExamColumn e : StatsDAO.publishedExams(conn, courseId)) {
                columns.add(new ExamColumn(e.examId(), e.title()));
            }
            Map<String, StatsDAO.AttemptTotal> best =
                    bestAttempts(StatsDAO.attemptTotals(conn, courseId)); // "user:exam" -> attempt

            List<StudentMarkRow> all = new ArrayList<>();
            int completedAll = 0;
            for (StatsDAO.StudentRow st : StatsDAO.registeredStudents(conn, courseId)) {
                List<ExamScore> scores = new ArrayList<>();
                BigDecimal sumPercent = BigDecimal.ZERO;
                int done = 0;
                for (ExamColumn col : columns) {
                    StatsDAO.AttemptTotal a = best.get(st.userId() + ":" + col.examId());
                    if (a == null) {
                        scores.add(null);
                    } else {
                        scores.add(new ExamScore(a.score(), a.max()));
                        sumPercent = sumPercent.add(a.max().signum() == 0 ? BigDecimal.ZERO
                                : percent(a.score(), a.max()));
                        done++;
                    }
                }
                BigDecimal avg = done == 0 ? null
                        : sumPercent.divide(BigDecimal.valueOf(done), 1, RoundingMode.HALF_UP);
                if (!columns.isEmpty() && done == columns.size()) {
                    completedAll++;
                }
                all.add(new StudentMarkRow(st.userId(), (st.firstName() + " " + st.lastName()).trim(),
                        scores, avg, done, columns.size()));
            }

            List<StudentMarkRow> shown = completedAllOnly
                    ? all.stream().filter(r -> r.totalCount() > 0 && r.completedCount() == r.totalCount()).toList()
                    : all;
            int from = (int) Math.min((long) (pageNo - 1) * size, shown.size());
            int to = Math.min(from + size, shown.size());
            Page<StudentMarkRow> rows = new Page<>(new ArrayList<>(shown.subList(from, to)),
                    pageNo, size, shown.size());
            return new StudentMarkTable(columns, rows, all.size(), completedAll);
        });
    }

    // ---- Rules and helpers ------------------------------------------------

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** DRAFT / PUBLISHED from the column; COMPLETED when published and the exam window is over. */
    static ExamStatus status(String column, LocalDateTime date, int durationMin, LocalDateTime now) {
        if (!"PUBLISHED".equals(column)) {
            return ExamStatus.DRAFT;
        }
        if (date != null && now.isAfter(date.plusMinutes(durationMin))) {
            return ExamStatus.COMPLETED;
        }
        return ExamStatus.PUBLISHED;
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** Best finished attempt per (student, exam); input must be ordered by attempt. Key = "userId:examId". */
    private static Map<String, StatsDAO.AttemptTotal> bestAttempts(List<StatsDAO.AttemptTotal> totals) {
        Map<String, StatsDAO.AttemptTotal> best = new LinkedHashMap<>();
        for (StatsDAO.AttemptTotal a : totals) {
            String k = a.userId() + ":" + a.examId();
            StatsDAO.AttemptTotal cur = best.get(k);
            // >= lets the later attempt win a tie
            if (cur == null || a.score().compareTo(cur.score()) >= 0) {
                best.put(k, a);
            }
        }
        return best;
    }

    private static ExamDAO.Head requireExam(Connection conn, int examId) throws SQLException, ServiceException {
        ExamDAO.Head h = ExamDAO.find(conn, examId);
        if (h == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
        }
        return h;
    }

    private static ExamDAO.Head lockedExam(Connection conn, int examId) throws SQLException, ServiceException {
        ExamDAO.Head h = ExamDAO.findForUpdate(conn, examId);
        if (h == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
        }
        return h;
    }

    /** When someone is logged in, only an instructor who teaches the course may continue. */
    private static void checkTeaches(Connection conn, int courseId) throws SQLException, ServiceException {
        if (!Session.isLoggedIn()) {
            return;
        }
        AuthService.UserAccount u = Session.user();
        if (u.role() != AuthService.Role.INSTRUCTOR || !CourseDAO.teaches(conn, u.userId(), courseId)) {
            throw new ServiceException(ServiceException.Kind.UNAUTHORIZED, "You do not teach this course.");
        }
    }

    /** The instructor must teach at least one course that covers the topic. */
    private static void checkTopicAccess(Connection conn, int topicId, int instructorId)
            throws SQLException, ServiceException {
        if (!CourseDAO.topicExists(conn, topicId)) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Topic not found.");
        }
        if (!CourseDAO.teachesTopic(conn, instructorId, topicId)) {
            throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                    "You do not teach a course that covers this topic.");
        }
    }

    /** Reading/changing a question needs a course you teach that covers its topic. */
    private static void checkQuestionAccess(Connection conn, int questionId)
            throws SQLException, ServiceException {
        if (!Session.isLoggedIn()) {
            return;
        }
        Integer topic = QuestionDAO.topicOf(conn, questionId);
        if (topic == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
        }
        AuthService.UserAccount u = Session.user();
        if (u.role() != AuthService.Role.INSTRUCTOR) {
            throw new ServiceException(ServiceException.Kind.UNAUTHORIZED, "Only instructors can do this.");
        }
        checkTopicAccess(conn, topic, u.userId());
    }

    private static void validateQuestion(QuestionDraft d) throws ServiceException {
        if (d == null) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Nothing to save.");
        }
        if (d.content() == null || d.content().isBlank()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Please write the question.");
        }
        if (d.content().trim().length() > MAX_QUESTION_CHARS) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "The question is too long (max " + MAX_QUESTION_CHARS + " characters).");
        }
        if (d.difficulty() < 1 || d.difficulty() > 5) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Difficulty must be from 1 to 5.");
        }
        if (d.type() == null) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Choose a question type.");
        }
        if (d.type() == QuestionType.MCQ) {
            List<OptionDraft> o = d.options();
            if (o == null || o.size() < 2 || o.size() > MAX_OPTIONS) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "A multiple choice question needs 2 to " + MAX_OPTIONS + " options.");
            }
            int correct = 0;
            Set<String> texts = new HashSet<>();
            for (OptionDraft x : o) {
                if (x.content() == null || x.content().isBlank()) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION, "An option is empty.");
                }
                if (x.content().trim().length() > MAX_QUESTION_CHARS) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION, "An option is too long.");
                }
                if (!texts.add(x.content().trim().toLowerCase())) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Two options have the same text.");
                }
                correct += x.correct() ? 1 : 0;
            }
            if (correct != 1) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "Exactly one option must be marked correct.");
            }
        } else if (d.answerKey() == null || d.answerKey().isBlank()) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "Enter the model answer.");
        } else if (d.answerKey().trim().length() > MAX_QUESTION_CHARS) {
            throw new ServiceException(ServiceException.Kind.VALIDATION, "The model answer is too long.");
        }
    }

    /** Fills MCQ + OPTIONS or SHORT_ANSWER for a question that already has its QUESTION row. */
    private static void insertSubtype(Connection c, int id, QuestionDraft d) throws SQLException {
        if (d.type() == QuestionType.MCQ) {
            List<QuestionDAO.OptionRow> options = new ArrayList<>();
            for (OptionDraft o : d.options()) {
                options.add(new QuestionDAO.OptionRow(o.content().trim(), o.correct()));
            }
            QuestionDAO.insertMcq(c, id, options);
        } else {
            QuestionDAO.insertShortAnswer(c, id, d.answerKey().trim());
        }
    }

    private static QuestionDraft loadQuestion(Connection conn, int questionId)
            throws SQLException, ServiceException {
        QuestionDAO.Row r = QuestionDAO.find(conn, questionId);
        if (r == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
        }
        List<OptionDraft> options = new ArrayList<>();
        if (r.mcq()) {
            for (QuestionDAO.OptionRow o : QuestionDAO.options(conn, questionId)) {
                options.add(new OptionDraft(o.content(), o.correct()));
            }
        }
        return new QuestionDraft(questionId, r.topicId(),
                r.mcq() ? QuestionType.MCQ : QuestionType.SHORT_ANSWER, r.content(), r.difficulty(),
                options, r.mcq() ? null : r.answerKey());
    }

    private static boolean sameOptions(List<OptionDraft> a, List<OptionDraft> b) {
        List<OptionDraft> x = a == null ? List.of() : a;
        List<OptionDraft> y = b == null ? List.of() : b;
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            if (x.get(i).correct() != y.get(i).correct()
                    || !x.get(i).content().trim().equals(y.get(i).content().trim())) {
                return false;
            }
        }
        return true;
    }

    private static String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }
}
