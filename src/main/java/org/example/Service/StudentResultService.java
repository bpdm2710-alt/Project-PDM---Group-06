package org.example.Service;

import org.example.DAO.AnswerDAO;
import org.example.DAO.AttemptDAO;
import org.example.DAO.ScoreDAO;
import org.example.DAO.StudentDAO;
import org.example.MODEL.Answer;
import org.example.MODEL.Attempt;
import org.example.MODEL.Score;
import org.example.MODEL.Student;
import org.example.util.DatabaseConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class StudentResultService {

    public record ResultSummary(int attemptCount,
                                BigDecimal averagePercentage,
                                BigDecimal bestPercentage,
                                int passedCount) {
    }

    public Student getStudent(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new StudentDAO(conn).findById(userId);
        }
    }

    public List<Student> listStudents() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new StudentDAO(conn).findAll();
        }
    }

    public Attempt getAttempt(int regisId, int attemptNo) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new AttemptDAO(conn).findById(regisId, attemptNo);
        }
    }

    public List<Attempt> listAttempts(int regisId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new AttemptDAO(conn).findByRegistration(regisId);
        }
    }

    public Attempt startAttempt(int regisId, int paperId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                AttemptDAO attemptDAO = new AttemptDAO(conn);

                AttemptDAO.RegistrationInfo registration =
                        attemptDAO.lockRegistration(regisId);
                if (registration == null) {
                    throw new IllegalArgumentException(
                            "Registration not found: " + regisId);
                }
                if (!attemptDAO.paperBelongsToExam(paperId, registration.examId())) {
                    throw new IllegalArgumentException(
                            "Paper " + paperId + " does not belong to exam "
                                    + registration.examId());
                }

                int used = attemptDAO.countByRegistration(regisId);
                if (used >= registration.maxAttempts()) {
                    throw new IllegalStateException(
                            "Maximum attempts reached for registration " + regisId);
                }

                int nextNo = attemptDAO.nextAttemptNo(regisId);
                Timestamp now = dbNow(conn);
                Attempt attempt = new Attempt(
                        nextNo,
                        regisId,
                        paperId,
                        registration.examId(),
                        now,
                        null            // EndTime NULL = in progress (21-table schema)
                );
                attemptDAO.insert(attempt);

                conn.commit();
                return attempt;
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            }
        }
    }

    public Answer saveAnswer(int regisId, int attemptNo, int questId,
                             String answerText, Integer selectedOptionNo)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                AttemptDAO attemptDAO = new AttemptDAO(conn);
                AnswerDAO answerDAO = new AnswerDAO(conn);

                Attempt attempt = attemptDAO.lockById(regisId, attemptNo);
                if (attempt == null) {
                    throw new IllegalArgumentException("Attempt not found");
                }
                if (attempt.isSubmitted()) {
                    throw new IllegalStateException("Attempt already submitted");
                }

                AnswerDAO.PaperQuestion question =
                        answerDAO.findPaperQuestion(regisId, attemptNo, questId);
                if (question == null) {
                    throw new IllegalArgumentException(
                            "Question " + questId + " is not in this attempt's paper");
                }

                String storedText;
                Integer storedOption = null;

                if (AnswerDAO.TYPE_MCQ.equals(question.type())) {
                    if (selectedOptionNo != null) {
                        if (!answerDAO.optionExists(questId, selectedOptionNo)) {
                            throw new IllegalArgumentException(
                                    "Option " + selectedOptionNo
                                            + " does not belong to question " + questId);
                        }
                        storedOption = selectedOptionNo;
                    }
                    storedText = null; // MCQ: AnswerText must be NULL, the option goes to SELECT_TABLE
                } else if (AnswerDAO.TYPE_SHORT_ANSWER.equals(question.type())) {
                    storedText = answerText == null || answerText.isBlank() ? null : answerText;
                } else {
                    throw new IllegalStateException(
                            "Unsupported question type: " + question.type());
                }

                Answer answer = new Answer(
                        questId,
                        attemptNo,
                        regisId,
                        storedText,
                        null,           // not graded until the attempt is submitted
                        storedOption
                );
                answerDAO.upsert(answer);
                conn.commit();

                Answer saved = answerDAO.findByAttemptAndQuestion(
                        regisId, attemptNo, questId);
                return saved != null ? saved : answer;
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            }
        }
    }

    public List<Answer> listAnswers(int regisId, int attemptNo) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new AnswerDAO(conn).findByAttempt(regisId, attemptNo);
        }
    }

    public Score submitAttempt(int regisId, int attemptNo) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                AttemptDAO attemptDAO = new AttemptDAO(conn);
                AnswerDAO answerDAO = new AnswerDAO(conn);

                Attempt attempt = attemptDAO.lockById(regisId, attemptNo);
                if (attempt == null) {
                    throw new IllegalArgumentException("Attempt not found");
                }
                if (attempt.isSubmitted()) {
                    throw new IllegalStateException("Attempt already submitted");
                }

                List<AnswerDAO.PaperQuestion> questions =
                        answerDAO.findPaperQuestions(
                                attempt.getPaperId(), attempt.getExamId());
                if (questions.isEmpty()) {
                    throw new IllegalStateException("Paper has no questions");
                }

                BigDecimal total = BigDecimal.ZERO;
                BigDecimal max = BigDecimal.ZERO;

                for (AnswerDAO.PaperQuestion question : questions) {
                    max = max.add(question.marks());

                    Answer answer = answerDAO.findByAttemptAndQuestion(
                            regisId, attemptNo, question.questId());
                    if (answer == null) {
                        answer = new Answer(
                                question.questId(),
                                attemptNo,
                                regisId,
                                null,   // unanswered question
                                null,
                                null
                        );
                        answerDAO.upsert(answer);
                    }

                    BigDecimal mark = BigDecimal.ZERO;

                    if (AnswerDAO.TYPE_MCQ.equals(question.type())) {
                        Integer optionNo = answer.getSelectedOptionNo();
                        if (optionNo == null) {
                            optionNo = parseOptionNo(answer.getAnswerText());
                        }
                        if (optionNo != null
                                && answerDAO.isCorrectOption(
                                        question.questId(), optionNo)) {
                            mark = question.marks();
                        }
                    } else if (AnswerDAO.TYPE_SHORT_ANSWER.equals(question.type())) {
                        if (answersMatch(answer.getAnswerText(), question.answerKey())) {
                            mark = question.marks();
                        }
                    } else {
                        throw new IllegalStateException(
                                "Unsupported question type: " + question.type());
                    }

                    total = total.add(mark);
                    answerDAO.updateMark(regisId, attemptNo, question.questId(), mark);
                }

                BigDecimal passMark = attemptDAO.findPassMark(regisId);
                Timestamp end = dbNow(conn);
                // ATTEMPT stores DATETIME without fractional seconds, so the
                // submitted end time must be at least one full second later.
                long minEnd = attempt.getStartTime().getTime() + 1000L;
                if (end.getTime() < minEnd) {
                    end = new Timestamp(minEnd);
                }

                int updated = attemptDAO.markSubmitted(regisId, attemptNo, end);
                if (updated == 0) {
                    throw new IllegalStateException(
                            "Attempt was submitted by another transaction");
                }

                conn.commit();
                return Score.calculate(
                        attemptNo, regisId, total, max, passMark, end);
            } catch (SQLException | RuntimeException e) {
                rollback(conn, e);
                throw e;
            }
        }
    }

    public Score getAttemptScore(int regisId, int attemptNo) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new ScoreDAO(conn).findByAttempt(regisId, attemptNo);
        }
    }

    public List<Score> getStudentScores(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new ScoreDAO(conn).findByStudent(userId);
        }
    }

    public ResultSummary getStudentSummary(int userId) throws SQLException {
        List<Score> scores = getStudentScores(userId);
        int count = scores.size();
        BigDecimal average = BigDecimal.ZERO.setScale(2);
        BigDecimal best = BigDecimal.ZERO.setScale(2);
        int passed = 0;

        if (count > 0) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Score score : scores) {
                sum = sum.add(score.getPercentage());
                if (score.getPercentage().compareTo(best) > 0) {
                    best = score.getPercentage();
                }
                if (score.isPassed()) {
                    passed++;
                }
            }
            average = sum.divide(
                    BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        return new ResultSummary(count, average, best, passed);
    }

    /** Current time from the database clock (session time zone set by DatabaseConnection). */
    private static Timestamp dbNow(Connection conn) throws SQLException {
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT NOW()")) {
            rs.next();
            return rs.getTimestamp(1);
        }
    }

    private boolean answersMatch(String answer, String answerKey) {
        if (answer == null || answerKey == null) {
            return false;
        }
        return answer.trim().equalsIgnoreCase(answerKey.trim());
    }

    private Integer parseOptionNo(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void rollback(Connection conn, Exception cause) {
        try {
            conn.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }
}
