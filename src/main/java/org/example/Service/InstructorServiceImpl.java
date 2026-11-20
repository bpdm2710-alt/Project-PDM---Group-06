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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instructor data layer.
 * Patch 03 (this file): My courses and the Exams tab (list / detail / save / delete / publish).
 * Still to come: paper builder (patch 04), question bank (patch 04), statistics (patch 05).
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

    // ---- Not written yet (patch 04 / 05) -----------------------------------

    @Override
    public int savePaper(PaperDraft draft) throws ServiceException {
        throw notYet("Paper builder");
    }

    @Override
    public List<TopicOption> listTopics(int courseId) throws ServiceException {
        throw notYet("Question bank");
    }

    @Override
    public Page<QuestionBankItem> searchQuestionBank(int courseId, QuestionBankFilter filter,
                                                     int page, int pageSize) throws ServiceException {
        throw notYet("Question bank");
    }

    @Override
    public QuestionDraft getQuestion(int questionId) throws ServiceException {
        throw notYet("Question bank");
    }

    @Override
    public int saveQuestion(int instructorId, QuestionDraft draft) throws ServiceException {
        throw notYet("Question bank");
    }

    @Override
    public void deleteQuestion(int questionId) throws ServiceException {
        throw notYet("Question bank");
    }

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
}
