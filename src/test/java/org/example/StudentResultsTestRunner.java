package org.example;

import org.example.MODEL.Answer;
import org.example.MODEL.Attempt;
import org.example.MODEL.Score;
import org.example.MODEL.Student;
import org.example.Service.StudentResultService;
import org.example.util.DatabaseConnection;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;

/**
 * End-to-end test for the Student & Results module.
 * Run from the project root after `mvn test-compile`.
 */
public final class StudentResultsTestRunner {

    private static final Path SEED =
            Path.of("database/student_results/01_seed_test.sql");
    private static final Path CLEANUP =
            Path.of("database/student_results/02_cleanup_test.sql");

    private static final StudentResultService SERVICE =
            new StudentResultService();

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        try {
            runSqlScript(SEED);
            resetRegistration9001();   // the seed already has a finished attempt on RegisID 9001
            runTests();
        } catch (Exception e) {
            failed++;
            System.err.println("FATAL: " + e);
            e.printStackTrace();
        } finally {
            try {
                runSqlScript(CLEANUP);
                System.out.println("Cleanup done.");
            } catch (Exception e) {
                failed++;
                System.err.println("CLEANUP FAILED: " + e);
                e.printStackTrace();
            }
        }

        System.out.printf("%nRESULT: %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runTests() throws Exception {
        Student student = SERVICE.getStudent(9001);
        check("student found", student != null);
        if (student != null) {
            checkEquals("student full name", "Test Student", student.getFullName());
            checkEquals("student email", "student.test@example.com", student.getEmail());
            checkEquals("student enrollment year", 2026, student.getEnrollmentYear());
        }
        check("unknown student is null", SERVICE.getStudent(9999) == null);
        check("student list contains 9001",
                SERVICE.listStudents().stream()
                        .anyMatch(s -> s.getUserId() == 9001));

        expectThrows("unknown registration rejected",
                IllegalArgumentException.class,
                () -> SERVICE.startAttempt(9999, 1));
        expectThrows("paper outside exam rejected",
                IllegalArgumentException.class,
                () -> SERVICE.startAttempt(9001, 9999));

        Attempt first = SERVICE.startAttempt(9001, 1);
        checkEquals("first attempt number", 1, first.getAttemptNo());
        checkEquals("first attempt registration", 9001, first.getRegisId());
        checkEquals("first attempt paper", 1, first.getPaperId());
        checkEquals("first attempt exam", 9001, first.getExamId());
        check("first attempt has start time", first.getStartTime() != null);
        check("first attempt has no end time yet (EndTime NULL = in progress)",
                first.getEndTime() == null);
        check("first attempt is in progress", !first.isSubmitted());

        check("attempt lookup works", SERVICE.getAttempt(9001, 1) != null);
        checkEquals("attempt list size", 1, SERVICE.listAttempts(9001).size());
        check("unsubmitted attempt has no score",
                SERVICE.getAttemptScore(9001, 1) == null);

        expectThrows("unknown question rejected",
                IllegalArgumentException.class,
                () -> SERVICE.saveAnswer(9001, 1, 9999, null, 1));
        expectThrows("unknown option rejected",
                IllegalArgumentException.class,
                () -> SERVICE.saveAnswer(9001, 1, 9001, null, 99));

        Answer q1 = SERVICE.saveAnswer(9001, 1, 9001, null, 1);
        check("MCQ answer: AnswerText NULL, option in SELECT_TABLE",
                q1.getAnswerText() == null && Integer.valueOf(1).equals(q1.getSelectedOptionNo()));
        SERVICE.saveAnswer(9001, 1, 9001, null, 1);
        checkEquals("duplicate answer does not add a row",
                1, SERVICE.listAnswers(9001, 1).size());

        SERVICE.saveAnswer(9001, 1, 9002, "  NoRmAlIzAtIoN  ", null);
        checkEquals("short answer does not add a row",
                2, SERVICE.listAnswers(9001, 1).size());

        SERVICE.saveAnswer(9001, 1, 9003, null, 2);
        SERVICE.saveAnswer(9001, 1, 9003, null, 1);
        List<Answer> beforeSubmit = SERVICE.listAnswers(9001, 1);
        checkEquals("three answered questions", 3, beforeSubmit.size());
        Answer q3 = find(beforeSubmit, 9003);
        check("changed MCQ option persisted",
                q3 != null && Integer.valueOf(1).equals(q3.getSelectedOptionNo()));

        Score firstScore = SERVICE.submitAttempt(9001, 1);
        checkBigDecimal("first total score", "15.00", firstScore.getTotalScore());
        checkBigDecimal("first max score", "20.00", firstScore.getMaxScore());
        checkBigDecimal("first percentage", "75.00", firstScore.getPercentage());
        check("first attempt passed", firstScore.isPassed());
        check("first score has graded time", firstScore.getGradedAt() != null);

        Attempt submitted = SERVICE.getAttempt(9001, 1);
        check("attempt locked after submit", submitted.isSubmitted());
        List<Answer> afterSubmit = SERVICE.listAnswers(9001, 1);
        checkEquals("blank question materialized", 4, afterSubmit.size());
        Answer q4 = find(afterSubmit, 9004);
        check("blank answer text is NULL", q4 != null && q4.getAnswerText() == null);
        checkBigDecimal("blank mark", "0.00",
                q4 == null ? null : q4.getMarkAwarded());
        checkBigDecimal("Q1 mark", "5.00", mark(afterSubmit, 9001));
        checkBigDecimal("Q2 mark", "5.00", mark(afterSubmit, 9002));
        checkBigDecimal("Q3 mark", "5.00", mark(afterSubmit, 9003));

        expectThrows("double submit rejected",
                IllegalStateException.class,
                () -> SERVICE.submitAttempt(9001, 1));
        expectThrows("edit after submit rejected",
                IllegalStateException.class,
                () -> SERVICE.saveAnswer(9001, 1, 9001, null, 1));

        Score loadedFirst = SERVICE.getAttemptScore(9001, 1);
        check("submitted score readable", loadedFirst != null);
        if (loadedFirst != null) {
            checkBigDecimal("loaded first total", "15.00",
                    loadedFirst.getTotalScore());
            checkBigDecimal("loaded first percentage", "75.00",
                    loadedFirst.getPercentage());
        }

        Attempt second = SERVICE.startAttempt(9001, 1);
        checkEquals("second attempt number", 2, second.getAttemptNo());
        check("second attempt is in progress", !second.isSubmitted());
        check("second attempt has no score yet",
                SERVICE.getAttemptScore(9001, 2) == null);

        Score secondScore = SERVICE.submitAttempt(9001, 2);
        checkBigDecimal("second total score", "0.00", secondScore.getTotalScore());
        checkBigDecimal("second max score", "20.00", secondScore.getMaxScore());
        checkBigDecimal("second percentage", "0.00", secondScore.getPercentage());
        check("second attempt not passed", !secondScore.isPassed());
        checkEquals("second attempt blank answers",
                4, SERVICE.listAnswers(9001, 2).size());

        expectThrows("max attempts enforced",
                IllegalStateException.class,
                () -> SERVICE.startAttempt(9001, 1));

        List<Score> scores = SERVICE.getStudentScores(9001);
        checkEquals("two submitted scores", 2, scores.size());

        StudentResultService.ResultSummary summary =
                SERVICE.getStudentSummary(9001);
        checkEquals("summary attempt count", 2, summary.attemptCount());
        checkBigDecimal("summary average percentage", "37.50",
                summary.averagePercentage());
        checkBigDecimal("summary best percentage", "75.00",
                summary.bestPercentage());
        checkEquals("summary passed count", 1, summary.passedCount());
    }

    /** Removes the attempts the seed created on RegisID 9001 so the scenario starts from attempt 1. */
    private static void resetRegistration9001() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String sql : new String[]{
                    "DELETE FROM SELECT_TABLE WHERE RegisID = 9001",
                    "DELETE FROM ANSWER WHERE RegisID = 9001",
                    "DELETE FROM ATTEMPT WHERE RegisID = 9001"}) {
                try (PreparedStatement statement = conn.prepareStatement(sql)) {
                    statement.execute();
                }
            }
        }
    }

    private static void runSqlScript(Path path) throws Exception {
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "Missing SQL script: " + path.toAbsolutePath());
        }

        StringBuilder cleaned = new StringBuilder();
        for (String line : Files.readString(path).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String raw : cleaned.toString().split(";")) {
                    String sql = raw.trim();
                    if (sql.isEmpty()
                            || sql.equalsIgnoreCase("START TRANSACTION")
                            || sql.equalsIgnoreCase("COMMIT")) {
                        continue;
                    }
                    try (PreparedStatement statement = conn.prepareStatement(sql)) {
                        statement.execute();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static Answer find(List<Answer> answers, int questId) {
        return answers.stream()
                .filter(a -> a.getQuestId() == questId)
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal mark(List<Answer> answers, int questId) {
        Answer answer = find(answers, questId);
        return answer == null ? null : answer.getMarkAwarded();
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        check(name + " [expected=" + expected + ", actual=" + actual + "]",
                Objects.equals(expected, actual));
    }

    private static void checkBigDecimal(String name, String expected,
                                        BigDecimal actual) {
        boolean ok = actual != null
                && actual.compareTo(new BigDecimal(expected)) == 0;
        check(name + " [expected=" + expected + ", actual=" + actual + "]", ok);
    }

    private static void expectThrows(String name,
                                     Class<? extends Throwable> type,
                                     ThrowingRunnable action) {
        try {
            action.run();
            check(name + " [no exception]", false);
        } catch (Throwable t) {
            check(name + " [threw " + t.getClass().getSimpleName() + "]",
                    type.isInstance(t));
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
