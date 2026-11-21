package org.example.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Everything the student screens need. Each method names the screen using it.
 * Decisions baked in: D01 single-select MCQ, D02 papers identified by PaperKey
 * (the UI never sees PaperKey/PaperID), D07 marks per paper.
 */
public interface StudentService {

    // ---- Overview ---------------------------------------------------------

    record StudentProfile(int userId, String fullName, int enrollmentYear) {
    }

    /**
     * @param averagePercent    null when the student has no graded attempt yet
     * @param latestResult      null when there is no finished attempt
     * @param upcomingExams     soonest first, at most 5
     */
    record StudentOverview(StudentProfile profile,
                           int courseCount,
                           int upcomingExamCount,
                           BigDecimal averagePercent,
                           int gradedAttemptCount,
                           List<ExamCard> upcomingExams,
                           AttemptResult latestResult) {
    }

    StudentOverview getOverview(int studentId) throws ServiceException;

    // ---- Courses ----------------------------------------------------------

    /** MY = courses where the student registered for an exam; EXPLORE = the rest. */
    enum CourseScope { MY, EXPLORE }

    /** Any field may be null/blank = no filter. */
    record CourseFilter(String keyword, String topic, String semester) {
        public static CourseFilter none() {
            return new CourseFilter(null, null, null);
        }
    }

    record CourseFilterOptions(List<String> topics, List<String> semesters) {
    }

    record CourseCard(int courseId,
                      String name,
                      int credits,
                      List<String> instructors,
                      List<String> topics,
                      String semester,
                      int studentCount,
                      int upcomingExamCount) {
    }

    CourseFilterOptions getCourseFilterOptions() throws ServiceException;

    List<CourseCard> listCourses(int studentId, CourseScope scope, CourseFilter filter)
            throws ServiceException;

    // ---- Exams ------------------------------------------------------------

    enum ExamState {
        NOT_REGISTERED,
        /** Registered, exam time has not come yet. */
        UPCOMING,
        /** Can start a new attempt now. */
        OPEN,
        /** An unfinished attempt exists: resume it. */
        IN_PROGRESS,
        /** Attempts used up or exam closed. */
        COMPLETED
    }

    /**
     * @param regisId       null when NOT_REGISTERED
     * @param questionCount questions in the paper that will be assigned
     * @param totalMarks    sum of marks of that paper
     */
    record ExamCard(int examId,
                    int courseId,
                    String courseName,
                    String title,
                    LocalDateTime examDate,
                    int durationMin,
                    int questionCount,
                    BigDecimal totalMarks,
                    BigDecimal passMark,
                    int maxAttempts,
                    Integer regisId,
                    int attemptsUsed,
                    ExamState state) {
    }

    /** Courses screen, "View exams" (also used to register). */
    List<ExamCard> listCourseExams(int studentId, int courseId) throws ServiceException;

    /** My exams screen: every exam the student registered for. */
    List<ExamCard> listMyExams(int studentId) throws ServiceException;

    /**
     * @throws ServiceException CONFLICT if already registered, if the exam has started, or if a
     *         prerequisite course (REQUIRES) of the exam's course has not been passed yet.
     */
    ExamCard registerForExam(int studentId, int examId) throws ServiceException;

    // ---- Taking an exam ---------------------------------------------------

    record AttemptKey(int regisId, int attemptNo) {
    }

    /** Never contains the correct flag. */
    record ExamOption(int optionNo, String content) {
    }

    record ExamQuestion(int questionId,
                        int order,
                        QuestionType type,
                        String content,
                        BigDecimal marks,
                        List<ExamOption> options) {
    }

    /** Exactly one of the two is used, depending on the question type. */
    record AnswerInput(Integer selectedOptionNo, String text) {
    }

    /**
     * @param secondsRemaining computed by the server, so the countdown does not
     *                         depend on the student's clock
     * @param savedAnswers     by questionId; empty when resuming nothing
     */
    record AttemptSession(AttemptKey key,
                          String examTitle,
                          String courseName,
                          int maxAttempts,
                          long secondsRemaining,
                          List<ExamQuestion> questions,
                          Map<Integer, AnswerInput> savedAnswers) {
    }

    /**
     * Starts a new attempt, or returns the unfinished one.
     * @throws ServiceException CONFLICT when no attempt is left or exam not open
     */
    AttemptSession startAttempt(int regisId) throws ServiceException;

    /** Called on every change; must be idempotent. */
    void saveAnswer(AttemptKey key, int questionId, AnswerInput input) throws ServiceException;

    /** Grades and closes the attempt. Also used when the timer reaches zero. */
    AttemptResult submitAttempt(AttemptKey key) throws ServiceException;

    // ---- Results ----------------------------------------------------------

    /**
     * @param fullyGraded false while some answers are still ungraded; then
     *                    score is provisional and passed is meaningless
     */
    record AttemptResult(AttemptKey key,
                         int examId,
                         String examTitle,
                         String courseName,
                         LocalDateTime submittedAt,
                         BigDecimal score,
                         BigDecimal maxScore,
                         BigDecimal percent,
                         boolean passed,
                         boolean fullyGraded) {
    }

    /** Results screen, newest first. */
    List<AttemptResult> listResults(int studentId) throws ServiceException;
}
