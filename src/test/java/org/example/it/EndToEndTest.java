package org.example.it;

import org.example.Service.*;
import org.example.Service.InstructorService.*;
import org.example.Service.StudentService.*;
import org.example.session.Session;
import org.example.util.DatabaseConnection;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole flow against a LOCAL MySQL: login -> expired attempt auto-closed -> start / save / resume /
 * submit -> register -> short answer waits -> instructor grades -> reports.
 *
 * Runs only with {@code mvn test -Dit.db=local} AND a db.url on localhost/127.0.0.1
 * (src/test/resources/application.properties), because it writes data.
 * Prepare the DB first: 02_online_examination_full_submission.sql, then database/demo/03_demo_seed.sql
 * (re-run 03_demo_seed.sql before every test run).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTest {

    private static final StudentService STUDENT = new StudentServiceImpl();
    private static final InstructorService INSTRUCTOR = new InstructorServiceImpl();
    private static final AuthService AUTH = new AuthServiceImpl();

    @BeforeAll
    static void onlyOnLocalDatabase() throws Exception {
        Assumptions.assumeTrue("local".equals(System.getProperty("it.db")),
                "end-to-end tests skipped (run with -Dit.db=local)");
        Properties p = new Properties();
        try (InputStream in = EndToEndTest.class.getClassLoader().getResourceAsStream("application.properties")) {
            Assumptions.assumeTrue(in != null, "no application.properties on the test classpath");
            p.load(in);
        }
        String url = p.getProperty("db.url", "");
        Assumptions.assumeTrue(url.contains("//localhost") || url.contains("//127.0.0.1"),
                "end-to-end tests write data: refusing to run against " + url);
    }

    @AfterEach
    void logout() {
        Session.end();
    }

    private static ExamCard myExam(int studentId, String title) throws ServiceException {
        return STUDENT.listMyExams(studentId).stream().filter(e -> e.title().equals(title)).findFirst()
                .orElseThrow(() -> new AssertionError("exam not found: " + title + " (run demo/03_demo_seed.sql)"));
    }

    private static String scalar(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    @Order(1)
    void login() throws Exception {
        assertEquals(101, AUTH.login("SV101@student.hcmiu.edu.vn", "Stu@123").userId());
        assertEquals(AuthService.Role.INSTRUCTOR, AUTH.login("inst01@hcmiu.edu.vn", "Inst@123").role());
        ServiceException e = assertThrows(ServiceException.class, () -> AUTH.login("sv101@student.hcmiu.edu.vn", "bad"));
        assertEquals(ServiceException.Kind.UNAUTHORIZED, e.getKind());
    }

    @Test
    @Order(2)
    void expiredAttemptIsClosedAndGraded() throws Exception {
        Session.start(AUTH.login("sv101@student.hcmiu.edu.vn", "Stu@123"));
        assertEquals(ExamState.COMPLETED, myExam(101, "DEMO - Expired attempt").state());
        AttemptResult r = STUDENT.listResults(101).stream()
                .filter(x -> x.examTitle().equals("DEMO - Expired attempt")).findFirst().orElseThrow();
        assertEquals(0, r.score().compareTo(new BigDecimal("10")));
        assertEquals(0, r.maxScore().compareTo(new BigDecimal("40")));
    }

    @Test
    @Order(3)
    void takeExamWithCorrectAnswers() throws Exception {
        Session.start(AUTH.login("sv101@student.hcmiu.edu.vn", "Stu@123"));
        ExamCard open = myExam(101, "DEMO - Open now");
        assertEquals(ExamState.OPEN, open.state());
        AttemptSession s = STUDENT.startAttempt(open.regisId());
        for (ExamQuestion q : s.questions()) {
            if (q.type() == QuestionType.MCQ) {
                int right = Integer.parseInt(scalar("SELECT Option_no FROM OPTIONS WHERE Bool_Correct = 1 AND Quest_ID = "
                        + q.questionId() + " LIMIT 1"));
                STUDENT.saveAnswer(s.key(), q.questionId(), new AnswerInput(right, null));
            } else {
                STUDENT.saveAnswer(s.key(), q.questionId(), new AnswerInput(null,
                        scalar("SELECT Answer_key FROM SHORT_ANSWER WHERE Quest_ID = " + q.questionId())));
            }
        }
        AttemptSession resumed = STUDENT.startAttempt(open.regisId());
        assertEquals(s.key(), resumed.key());
        assertEquals(s.questions().size(), resumed.savedAnswers().size());
        AttemptResult r = STUDENT.submitAttempt(s.key());
        assertTrue(r.fullyGraded());
        assertTrue(r.passed());
        assertEquals(0, r.score().compareTo(r.maxScore()));
    }

    @Test
    @Order(4)
    void registerForUpcomingExam() throws Exception {
        // PDM (course 1) requires Data Structures and Algorithms (REQUIRES 3 -> 1).
        Session.start(AUTH.login("sv104@student.hcmiu.edu.vn", "Stu@123"));
        ExamCard blocked = STUDENT.listCourseExams(104, 1).stream().filter(e -> e.title().equals("DEMO - Upcoming"))
                .findFirst().orElseThrow();
        ServiceException pre = assertThrows(ServiceException.class, () -> STUDENT.registerForExam(104, blocked.examId()));
        assertTrue(pre.getMessage().contains("Data Structures and Algorithms"), pre.getMessage());

        // sv106 passed DSA in the seed -> may register
        Session.start(AUTH.login("sv106@student.hcmiu.edu.vn", "Stu@123"));
        ExamCard up = STUDENT.listCourseExams(106, 1).stream().filter(e -> e.title().equals("DEMO - Upcoming"))
                .findFirst().orElseThrow();
        ExamCard reg = STUDENT.registerForExam(106, up.examId());
        assertEquals(ExamState.UPCOMING, reg.state());
        ServiceException e = assertThrows(ServiceException.class, () -> STUDENT.startAttempt(reg.regisId()));
        assertEquals(ServiceException.Kind.CONFLICT, e.getKind());
    }

    @Test
    @Order(5)
    void shortAnswerWaitsThenInstructorGrades() throws Exception {
        Session.start(AUTH.login("sv102@student.hcmiu.edu.vn", "Stu@123"));
        AttemptSession s = STUDENT.startAttempt(myExam(102, "DEMO - Open now").regisId());
        for (ExamQuestion q : s.questions()) {
            if (q.type() == QuestionType.SHORT_ANSWER) {
                STUDENT.saveAnswer(s.key(), q.questionId(), new AnswerInput(null, "my own words"));
            }
        }
        assertFalse(STUDENT.submitAttempt(s.key()).fullyGraded());

        Session.start(AUTH.login("inst01@hcmiu.edu.vn", "Inst@123"));
        List<PendingAnswer> pending = INSTRUCTOR.listPendingGrading(1);
        PendingAnswer p = pending.stream().filter(x -> x.ref().regisId() == s.key().regisId()).findFirst().orElseThrow();
        assertThrows(ServiceException.class, () -> INSTRUCTOR.gradeAnswer(p.ref(), p.maxMarks().add(BigDecimal.ONE)));
        INSTRUCTOR.gradeAnswer(p.ref(), new BigDecimal("12.5"));

        Session.start(AUTH.login("sv102@student.hcmiu.edu.vn", "Stu@123"));
        AttemptResult r = STUDENT.listResults(102).stream().filter(x -> x.key().equals(s.key())).findFirst().orElseThrow();
        assertTrue(r.fullyGraded());
        assertEquals(0, r.score().compareTo(new BigDecimal("12.5")));
    }

    @Test
    @Order(6)
    void everyReportRuns() throws Exception {
        Session.start(AUTH.login("inst01@hcmiu.edu.vn", "Inst@123"));
        for (ReportKind k : ReportKind.values()) {
            ReportTable t = INSTRUCTOR.runReport(k, new ReportParams(1, null, 1, 2));
            assertFalse(t.columns().isEmpty(), k.title());
        }
        assertFalse(INSTRUCTOR.getStatistics(1).topicAverages().isEmpty());
    }
}
