package org.example.Service;

import org.example.session.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Instructor data layer.
 * Patch 03: My courses and the Exams tab (list / detail / save / delete / publish).
 * Patch 04: paper builder + question bank.
 * Still to come: statistics (patch 05).
 *
 * Conventions:
 *  - EXAMINATION.Status is DRAFT or PUBLISHED; COMPLETED is derived (published and the exam
 *    window Exam_date + Duration has passed);
 *  - Exam_date NULL = "Not scheduled";
 *  - a paper is identified by (PaperID, Exam_ID); the UI's "paperKey" is the PaperID (the exam is
 *    always known next to it) and "paperNo" is the same number;
 *  - when somebody is logged in, an instructor can only touch courses he teaches.
 */
public class InstructorServiceImpl implements InstructorService {

    // ---- My courses -------------------------------------------------------

    @Override
    public List<TeachingCourse> listTeachingCourses(int instructorId) throws ServiceException {
        return Db.run(conn -> {
            if (Session.isLoggedIn() && Session.user().userId() != instructorId) {
                throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                        "You can only see your own courses.");
            }
            String sql = "SELECT c.Course_ID, c.CName, c.Credits, t.Semester, " +
                    "(SELECT COUNT(DISTINCT r.User_ID) FROM REGISTRATION r " +
                    "   JOIN EXAMINATION e ON e.Exam_ID = r.Exam_ID WHERE e.Course_ID = c.Course_ID) AS students, " +
                    "(SELECT COUNT(*) FROM EXAMINATION e2 WHERE e2.Course_ID = c.Course_ID) AS exams " +
                    "FROM TEACH t JOIN COURSE c ON c.Course_ID = t.Course_ID " +
                    "WHERE t.User_ID = ? ORDER BY c.CName, c.Course_ID";
            List<TeachingCourse> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, instructorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new TeachingCourse(rs.getInt("Course_ID"), rs.getString("CName"),
                                rs.getInt("Credits"), rs.getString("Semester"),
                                rs.getInt("students"), rs.getInt("exams")));
                    }
                }
            }
            return out;
        });
    }

    // ---- Exams tab --------------------------------------------------------

    @Override
    public List<ExamRow> listExams(int courseId) throws ServiceException {
        return Db.run(conn -> {
            checkTeaches(conn, courseId);
            String paper = " FROM CONTAINS ct WHERE ct.Exam_ID = e.Exam_ID AND ct.PaperID = " +
                    "(SELECT MIN(p.PaperID) FROM PAPER p WHERE p.Exam_ID = e.Exam_ID)";
            String sql = "SELECT e.Exam_ID, e.Title, e.Exam_date, e.Duration, e.Status, " +
                    "(SELECT COUNT(*)" + paper + ") AS question_count, " +
                    "(SELECT COALESCE(SUM(ct.Marks), 0)" + paper + ") AS total_marks " +
                    "FROM EXAMINATION e WHERE e.Course_ID = ? " +
                    "ORDER BY (e.Exam_date IS NULL), e.Exam_date DESC, e.Exam_ID DESC";
            LocalDateTime now = LocalDateTime.now();
            List<ExamRow> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDateTime date = toLdt(rs.getTimestamp("Exam_date"));
                        out.add(new ExamRow(rs.getInt("Exam_ID"), rs.getString("Title"), date,
                                rs.getInt("question_count"), rs.getBigDecimal("total_marks"),
                                status(rs.getString("Status"), date, rs.getInt("Duration"), now)));
                    }
                }
            }
            return out;
        });
    }

    @Override
    public ExamDetail getExam(int examId) throws ServiceException {
        return Db.run(conn -> {
            int courseId = courseOfExam(conn, examId);
            checkTeaches(conn, courseId);
            ExamDetail head;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT Title, Exam_date, Duration, Pass_mark, Max_attempts, Status " +
                    "FROM EXAMINATION WHERE Exam_ID = ?")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    LocalDateTime date = toLdt(rs.getTimestamp("Exam_date"));
                    int duration = rs.getInt("Duration");
                    head = new ExamDetail(examId, courseId, rs.getString("Title"), date, duration,
                            rs.getBigDecimal("Pass_mark"), rs.getInt("Max_attempts"),
                            status(rs.getString("Status"), date, duration, LocalDateTime.now()),
                            List.of());
                }
            }

            Map<Integer, List<PaperQuestion>> byPaper = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT PaperID FROM PAPER WHERE Exam_ID = ? ORDER BY PaperID")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byPaper.put(rs.getInt(1), new ArrayList<>());
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ct.PaperID, ct.Quest_ID, ct.QuestionOrder, ct.Marks, q.Content, " +
                    "(m.Quest_ID IS NOT NULL) AS is_mcq " +
                    "FROM CONTAINS ct JOIN QUESTION q ON q.Quest_ID = ct.Quest_ID " +
                    "LEFT JOIN MCQ m ON m.Quest_ID = ct.Quest_ID " +
                    "WHERE ct.Exam_ID = ? ORDER BY ct.PaperID, ct.QuestionOrder, ct.Quest_ID")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byPaper.computeIfAbsent(rs.getInt("PaperID"), k -> new ArrayList<>())
                                .add(new PaperQuestion(rs.getInt("Quest_ID"), rs.getInt("QuestionOrder"),
                                        rs.getString("Content"),
                                        rs.getBoolean("is_mcq") ? QuestionType.MCQ : QuestionType.SHORT_ANSWER,
                                        rs.getBigDecimal("Marks")));
                    }
                }
            }
            List<PaperDetail> papers = new ArrayList<>();
            byPaper.forEach((paperId, qs) -> {
                BigDecimal total = BigDecimal.ZERO;
                for (PaperQuestion q : qs) {
                    total = total.add(q.marks());
                }
                papers.add(new PaperDetail(paperId, paperId, qs, total));
            });
            return new ExamDetail(head.examId(), head.courseId(), head.title(), head.examDate(),
                    head.durationMin(), head.passMark(), head.maxAttempts(), head.status(), papers);
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
        if (d.passMark() == null || d.passMark().signum() < 0
                || d.passMark().compareTo(new BigDecimal("9999.99")) > 0) {
            throw new ServiceException(ServiceException.Kind.VALIDATION,
                    "Pass mark must be a number from 0 to 9999.99.");
        }
        Timestamp date = d.examDate() == null ? null : Timestamp.valueOf(d.examDate().withNano(0));

        return Db.run(conn -> Db.tx(conn, c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM COURSE WHERE Course_ID = ?")) {
                ps.setInt(1, d.courseId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Course not found.");
                    }
                }
            }
            checkTeaches(c, d.courseId());

            if (d.examId() == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO EXAMINATION (Title, Exam_date, Duration, Pass_mark, Max_attempts, " +
                        "Course_ID, Status) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setTimestamp(2, date);
                    ps.setInt(3, d.durationMin());
                    ps.setBigDecimal(4, d.passMark());
                    ps.setInt(5, d.maxAttempts());
                    ps.setInt(6, d.courseId());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new ServiceException(ServiceException.Kind.INTERNAL,
                                    "The exam was saved but its id could not be read.");
                        }
                        return keys.getInt(1);
                    }
                }
            }

            int examId = d.examId();
            int oldCourse;
            int oldDuration;
            BigDecimal oldPass;
            String oldStatus;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Course_ID, Duration, Pass_mark, Status FROM EXAMINATION " +
                    "WHERE Exam_ID = ? FOR UPDATE")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
                    }
                    oldCourse = rs.getInt("Course_ID");
                    oldDuration = rs.getInt("Duration");
                    oldPass = rs.getBigDecimal("Pass_mark");
                    oldStatus = rs.getString("Status");
                }
            }
            if (oldCourse != d.courseId()) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "An exam cannot be moved to another course.");
            }
            if (date == null && "PUBLISHED".equals(oldStatus)) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "A published exam needs a date. Unpublish it first to clear the date.");
            }
            if (countAttempts(c, examId) > 0) {
                if (oldDuration != d.durationMin() || oldPass.compareTo(d.passMark()) != 0) {
                    throw new ServiceException(ServiceException.Kind.CONFLICT,
                            "Duration and pass mark cannot change after students have attempted the exam.");
                }
                if (d.maxAttempts() < mostAttemptsByOneStudent(c, examId)) {
                    throw new ServiceException(ServiceException.Kind.CONFLICT,
                            "Maximum attempts is lower than what a student has already used.");
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE EXAMINATION SET Title = ?, Exam_date = ?, Duration = ?, Pass_mark = ?, " +
                    "Max_attempts = ? WHERE Exam_ID = ?")) {
                ps.setString(1, title);
                ps.setTimestamp(2, date);
                ps.setInt(3, d.durationMin());
                ps.setBigDecimal(4, d.passMark());
                ps.setInt(5, d.maxAttempts());
                ps.setInt(6, examId);
                ps.executeUpdate();
            }
            return examId;
        }));
    }

    @Override
    public void deleteExam(int examId) throws ServiceException {
        Db.run(conn -> Db.tx(conn, c -> {
            checkTeaches(c, courseOfExam(c, examId));
            if (countAttempts(c, examId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted this exam, so it cannot be deleted.");
            }
            // FK-safe order. Registrations without attempts are removed with the exam.
            exec(c, "DELETE FROM CONTAINS WHERE Exam_ID = ?", examId);
            exec(c, "DELETE FROM PAPER WHERE Exam_ID = ?", examId);
            exec(c, "DELETE FROM REGISTRATION WHERE Exam_ID = ?", examId);
            exec(c, "DELETE FROM EXAMINATION WHERE Exam_ID = ?", examId);
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
            checkTeaches(c, courseOfExam(c, examId));
            if (status == ExamStatus.PUBLISHED) {
                Timestamp date = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT Exam_date FROM EXAMINATION WHERE Exam_ID = ? FOR UPDATE")) {
                    ps.setInt(1, examId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        date = rs.getTimestamp(1);
                    }
                }
                if (date == null) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Set a date for the exam before publishing it.");
                }
                if (countPaperQuestions(c, examId) == 0) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Build a paper with at least one question before publishing.");
                }
            } else if (countAttempts(c, examId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted this exam, so it cannot go back to draft.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE EXAMINATION SET Status = ? WHERE Exam_ID = ?")) {
                ps.setString(1, status == ExamStatus.PUBLISHED ? "PUBLISHED" : "DRAFT");
                ps.setInt(2, examId);
                ps.executeUpdate();
            }
            return null;
        }));
    }

    // ---- Build your paper (patch 04) ---------------------------------------

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
            if (q.marks() == null || q.marks().signum() <= 0
                    || q.marks().compareTo(MAX_MARKS) > 0) {
                throw new ServiceException(ServiceException.Kind.VALIDATION,
                        "Marks must be greater than 0 and at most 9999.99.");
            }
        }
        // The order that matters is the order given; store it as 1..n.
        List<PaperQuestionDraft> ordered = new ArrayList<>(d.questions());
        ordered.sort(Comparator.comparingInt(PaperQuestionDraft::order));

        return Db.run(conn -> Db.tx(conn, c -> {
            int courseId = courseOfExam(c, d.examId());
            checkTeaches(c, courseId);
            lockExam(c, d.examId());
            if (countAttempts(c, d.examId()) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already attempted this exam, so its paper cannot change.");
            }
            Set<Integer> allowed = questionIdsOfCourse(c, courseId);
            for (PaperQuestionDraft q : ordered) {
                if (!allowed.contains(q.questionId())) {
                    throw new ServiceException(ServiceException.Kind.VALIDATION,
                            "Question " + q.questionId() + " is not in this course's question bank.");
                }
            }

            int paperId;
            if (d.paperKey() == null) {
                paperId = scalar(c, "SELECT COALESCE(MAX(PaperID), 0) + 1 FROM PAPER WHERE Exam_ID = ?",
                        d.examId());
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO PAPER (PaperID, Exam_ID) VALUES (?, ?)")) {
                    ps.setInt(1, paperId);
                    ps.setInt(2, d.examId());
                    ps.executeUpdate();
                }
            } else {
                paperId = d.paperKey();
                if (scalar2(c, "SELECT COUNT(*) FROM PAPER WHERE Exam_ID = ? AND PaperID = ?",
                        d.examId(), paperId) == 0) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND,
                            "That paper does not belong to this exam.");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM CONTAINS WHERE Exam_ID = ? AND PaperID = ?")) {
                    ps.setInt(1, d.examId());
                    ps.setInt(2, paperId);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO CONTAINS (Quest_ID, PaperID, Exam_ID, QuestionOrder, Marks) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                int order = 1;
                for (PaperQuestionDraft q : ordered) {
                    ps.setInt(1, q.questionId());
                    ps.setInt(2, paperId);
                    ps.setInt(3, d.examId());
                    ps.setInt(4, order++);
                    ps.setBigDecimal(5, q.marks());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return paperId;
        }));
    }

    // ---- Question bank (patch 04) ------------------------------------------

    @Override
    public List<TopicOption> listTopics(int courseId) throws ServiceException {
        return Db.run(conn -> {
            checkTeaches(conn, courseId);
            List<TopicOption> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT t.Topic_ID, t.Content FROM COVERS cv JOIN TOPIC t ON t.Topic_ID = cv.Topic_ID " +
                    "WHERE cv.Course_ID = ? ORDER BY t.Content, t.Topic_ID")) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new TopicOption(rs.getInt(1), rs.getString(2)));
                    }
                }
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

        return Db.run(conn -> {
            checkTeaches(conn, courseId);
            StringBuilder from = new StringBuilder(
                    " FROM QUESTION q JOIN TOPIC t ON t.Topic_ID = q.Topic_ID " +
                    "JOIN COVERS cv ON cv.Topic_ID = q.Topic_ID AND cv.Course_ID = ? " +
                    "LEFT JOIN MCQ m ON m.Quest_ID = q.Quest_ID WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            params.add(courseId);
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                from.append(" AND q.Content LIKE ? ESCAPE '\\\\'");
                params.add("%" + likeEscape(filter.keyword().trim()) + "%");
            }
            if (filter.topicId() != null) {
                from.append(" AND q.Topic_ID = ?");
                params.add(filter.topicId());
            }
            if (filter.type() == QuestionType.MCQ) {
                from.append(" AND m.Quest_ID IS NOT NULL");
            } else if (filter.type() == QuestionType.SHORT_ANSWER) {
                from.append(" AND m.Quest_ID IS NULL");
            }
            if (filter.difficulty() != null) {
                from.append(" AND q.Difficulty = ?");
                params.add(filter.difficulty());
            }

            long total;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*)" + from)) {
                bind(ps, params, 0);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<QuestionBankItem> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT q.Quest_ID, q.Content, q.Difficulty, t.Content AS topic, " +
                    "(m.Quest_ID IS NOT NULL) AS is_mcq" + from +
                    " ORDER BY q.Quest_ID DESC LIMIT ? OFFSET ?")) {
                int next = bind(ps, params, 0);
                ps.setInt(next + 1, size);
                ps.setLong(next + 2, (long) (pageNo - 1) * size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new QuestionBankItem(rs.getInt("Quest_ID"), rs.getString("Content"),
                                rs.getBoolean("is_mcq") ? QuestionType.MCQ : QuestionType.SHORT_ANSWER,
                                rs.getInt("Difficulty"), rs.getString("topic")));
                    }
                }
            }
            return new Page<>(items, pageNo, size, total);
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
            if (scalar(c, "SELECT COUNT(*) FROM INSTRUCTOR WHERE User_ID = ?", instructorId) == 0) {
                throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                        "Only instructors can write questions.");
            }
            checkTopicAccess(c, d.topicId(), instructorId);

            String content = d.content().trim();
            if (d.questionId() == null) {
                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO QUESTION (Content, Difficulty, Topic_ID, User_ID) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, content);
                    ps.setInt(2, d.difficulty());
                    ps.setInt(3, d.topicId());
                    ps.setInt(4, instructorId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new ServiceException(ServiceException.Kind.INTERNAL,
                                    "The question was saved but its id could not be read.");
                        }
                        id = keys.getInt(1);
                    }
                }
                insertSubtype(c, id, d);
                return id;
            }

            int id = d.questionId();
            checkQuestionAccess(c, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Quest_ID FROM QUESTION WHERE Quest_ID = ? FOR UPDATE")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
                    }
                }
            }
            QuestionDraft old = loadQuestion(c, id);
            boolean answered = scalar(c, "SELECT COUNT(*) FROM ANSWER WHERE Quest_ID = ?", id) > 0;
            boolean sameBody = old.type() == d.type() && old.topicId() == d.topicId()
                    && sameOptions(old.options(), d.options())
                    && Objects.equals(trimOrNull(old.answerKey()), trimOrNull(d.answerKey()));
            if (answered && !sameBody) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already answered this question. You can fix the wording " +
                        "and difficulty, but not its type, topic, options or answer key.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE QUESTION SET Content = ?, Difficulty = ?, Topic_ID = ? WHERE Quest_ID = ?")) {
                ps.setString(1, content);
                ps.setInt(2, d.difficulty());
                ps.setInt(3, d.topicId());
                ps.setInt(4, id);
                ps.executeUpdate();
            }
            if (!sameBody) {
                deleteSubtype(c, id);
                insertSubtype(c, id, d);
            }
            return id;
        }));
    }

    @Override
    public void deleteQuestion(int questionId) throws ServiceException {
        Db.run(conn -> Db.tx(conn, c -> {
            checkQuestionAccess(c, questionId);
            if (scalar(c, "SELECT COUNT(*) FROM QUESTION WHERE Quest_ID = ?", questionId) == 0) {
                throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
            }
            if (scalar(c, "SELECT COUNT(*) FROM ANSWER WHERE Quest_ID = ?", questionId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "Students have already answered this question, so it cannot be deleted.");
            }
            if (scalar(c, "SELECT COUNT(*) FROM CONTAINS ct JOIN EXAMINATION e ON e.Exam_ID = ct.Exam_ID " +
                    "WHERE ct.Quest_ID = ? AND e.Status = 'PUBLISHED'", questionId) > 0) {
                throw new ServiceException(ServiceException.Kind.CONFLICT,
                        "This question is in a published exam. Unpublish the exam or change its paper first.");
            }
            exec(c, "DELETE FROM CONTAINS WHERE Quest_ID = ?", questionId); // draft exams only
            deleteSubtype(c, questionId);
            exec(c, "DELETE FROM QUESTION WHERE Quest_ID = ?", questionId);
            return null;
        }));
    }

    // ---- Not written yet (patch 05) ----------------------------------------

    @Override
    public CourseStatistics getStatistics(int courseId) throws ServiceException {
        throw notYet("Statistics");
    }

    @Override
    public StudentMarkTable listStudentMarks(int courseId, boolean completedAllOnly,
                                             int page, int pageSize) throws ServiceException {
        throw notYet("Student marks");
    }

    // ---- Helpers ----------------------------------------------------------

    private static ServiceException notYet(String what) {
        return new ServiceException(ServiceException.Kind.UNAVAILABLE,
                what + " is not connected to the database yet.");
    }

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

    private static int courseOfExam(Connection conn, int examId) throws SQLException, ServiceException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Course_ID FROM EXAMINATION WHERE Exam_ID = ?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Exam not found.");
                }
                return rs.getInt(1);
            }
        }
    }

    /** When someone is logged in, only an instructor who teaches the course may continue. */
    private static void checkTeaches(Connection conn, int courseId) throws SQLException, ServiceException {
        if (!Session.isLoggedIn()) {
            return;
        }
        AuthService.UserAccount u = Session.user();
        boolean ok = false;
        if (u.role() == AuthService.Role.INSTRUCTOR) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM TEACH WHERE User_ID = ? AND Course_ID = ?")) {
                ps.setInt(1, u.userId());
                ps.setInt(2, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    ok = rs.next();
                }
            }
        }
        if (!ok) {
            throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                    "You do not teach this course.");
        }
    }

    private static int countAttempts(Connection conn, int examId) throws SQLException {
        return scalar(conn, "SELECT COUNT(*) FROM ATTEMPT WHERE Exam_ID = ?", examId);
    }

    private static int mostAttemptsByOneStudent(Connection conn, int examId) throws SQLException {
        return scalar(conn, "SELECT COALESCE(MAX(c), 0) FROM " +
                "(SELECT COUNT(*) AS c FROM ATTEMPT WHERE Exam_ID = ? GROUP BY RegisID) x", examId);
    }

    /** Questions in the exam's lowest-numbered paper (the one students get). */
    private static int countPaperQuestions(Connection conn, int examId) throws SQLException {
        return scalar(conn, "SELECT COUNT(*) FROM CONTAINS ct WHERE ct.Exam_ID = ? AND ct.PaperID = " +
                "(SELECT MIN(p.PaperID) FROM PAPER p WHERE p.Exam_ID = ct.Exam_ID)", examId);
    }

    private static int scalar(Connection conn, String sql, int param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void exec(Connection conn, String sql, int param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            ps.executeUpdate();
        }
    }

    // ---- Helpers for patch 04 ---------------------------------------------

    private static final int MAX_PAPER_QUESTIONS = 200;
    private static final int MAX_QUESTION_CHARS = 5000;
    private static final int MAX_OPTIONS = 10;
    private static final BigDecimal MAX_MARKS = new BigDecimal("9999.99");

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
            exec(c, "INSERT INTO MCQ (Quest_ID) VALUES (?)", id);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO OPTIONS (Option_no, Quest_ID, Op_Content, Bool_Correct) VALUES (?, ?, ?, ?)")) {
                int no = 1;
                for (OptionDraft o : d.options()) {
                    ps.setInt(1, no++);
                    ps.setInt(2, id);
                    ps.setString(3, o.content().trim());
                    ps.setInt(4, o.correct() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO SHORT_ANSWER (Quest_ID, Answer_key) VALUES (?, ?)")) {
                ps.setInt(1, id);
                ps.setString(2, d.answerKey().trim());
                ps.executeUpdate();
            }
        }
    }

    private static void deleteSubtype(Connection c, int id) throws SQLException {
        exec(c, "DELETE FROM SELECT_TABLE WHERE Quest_ID = ?", id);
        exec(c, "DELETE FROM OPTIONS WHERE Quest_ID = ?", id);
        exec(c, "DELETE FROM MCQ WHERE Quest_ID = ?", id);
        exec(c, "DELETE FROM SHORT_ANSWER WHERE Quest_ID = ?", id);
    }

    private static QuestionDraft loadQuestion(Connection conn, int questionId)
            throws SQLException, ServiceException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT q.Content, q.Difficulty, q.Topic_ID, (m.Quest_ID IS NOT NULL) AS is_mcq, sa.Answer_key " +
                "FROM QUESTION q LEFT JOIN MCQ m ON m.Quest_ID = q.Quest_ID " +
                "LEFT JOIN SHORT_ANSWER sa ON sa.Quest_ID = q.Quest_ID WHERE q.Quest_ID = ?")) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
                }
                boolean mcq = rs.getBoolean("is_mcq");
                List<OptionDraft> options = new ArrayList<>();
                if (mcq) {
                    try (PreparedStatement ops = conn.prepareStatement(
                            "SELECT Op_Content, Bool_Correct FROM OPTIONS WHERE Quest_ID = ? ORDER BY Option_no")) {
                        ops.setInt(1, questionId);
                        try (ResultSet or = ops.executeQuery()) {
                            while (or.next()) {
                                options.add(new OptionDraft(or.getString(1), or.getInt(2) == 1));
                            }
                        }
                    }
                }
                return new QuestionDraft(questionId, rs.getInt("Topic_ID"),
                        mcq ? QuestionType.MCQ : QuestionType.SHORT_ANSWER, rs.getString("Content"),
                        rs.getInt("Difficulty"), options, mcq ? null : rs.getString("Answer_key"));
            }
        }
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

    /** Every question whose topic the course covers. */
    private static Set<Integer> questionIdsOfCourse(Connection conn, int courseId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT q.Quest_ID FROM QUESTION q JOIN COVERS cv ON cv.Topic_ID = q.Topic_ID " +
                "WHERE cv.Course_ID = ?")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** The logged-in instructor must teach at least one course that covers the topic. */
    private static void checkTopicAccess(Connection conn, int topicId, int instructorId)
            throws SQLException, ServiceException {
        if (scalar(conn, "SELECT COUNT(*) FROM TOPIC WHERE Topic_ID = ?", topicId) == 0) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Topic not found.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM COVERS cv JOIN TEACH t ON t.Course_ID = cv.Course_ID " +
                "WHERE cv.Topic_ID = ? AND t.User_ID = ? LIMIT 1")) {
            ps.setInt(1, topicId);
            ps.setInt(2, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ServiceException(ServiceException.Kind.UNAUTHORIZED,
                            "You do not teach a course that covers this topic.");
                }
            }
        }
    }

    /** Reading/changing a question needs a course you teach that covers its topic. */
    private static void checkQuestionAccess(Connection conn, int questionId)
            throws SQLException, ServiceException {
        if (!Session.isLoggedIn()) {
            return;
        }
        Integer topic = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT Topic_ID FROM QUESTION WHERE Quest_ID = ?")) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    topic = rs.getInt(1);
                }
            }
        }
        if (topic == null) {
            throw new ServiceException(ServiceException.Kind.NOT_FOUND, "Question not found.");
        }
        AuthService.UserAccount u = Session.user();
        if (u.role() != AuthService.Role.INSTRUCTOR) {
            throw new ServiceException(ServiceException.Kind.UNAUTHORIZED, "Only instructors can do this.");
        }
        checkTopicAccess(conn, topic, u.userId());
    }

    private static void lockExam(Connection conn, int examId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Exam_ID FROM EXAMINATION WHERE Exam_ID = ? FOR UPDATE")) {
            ps.setInt(1, examId);
            ps.executeQuery().close();
        }
    }

    private static int scalar2(Connection conn, String sql, int a, int b) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Binds params starting at index 1 + offset; returns how many parameters are now bound. */
    private static int bind(PreparedStatement ps, List<Object> params, int offset) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(offset + i + 1, params.get(i));
        }
        return offset + params.size();
    }
}
