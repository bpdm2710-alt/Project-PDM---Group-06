package org.example.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the instructor screens need. */
public interface InstructorService {

    // ---- My courses -------------------------------------------------------

    record TeachingCourse(int courseId,
                          String name,
                          int credits,
                          String semester,
                          int studentCount,
                          int examCount) {
    }

    List<TeachingCourse> listTeachingCourses(int instructorId) throws ServiceException;

    // ---- Exams tab --------------------------------------------------------

    /**
     * DRAFT/PUBLISHED have no column in EXAMINATION yet (needs a schema
     * decision). COMPLETED is derived: published and its date has passed.
     */
    enum ExamStatus { DRAFT, PUBLISHED, COMPLETED }

    /** @param examDate null = "Not scheduled" */
    record ExamRow(int examId,
                   String title,
                   LocalDateTime examDate,
                   int questionCount,
                   BigDecimal totalMarks,
                   ExamStatus status) {
    }

    record PaperQuestion(int questionId,
                         int order,
                         String content,
                         QuestionType type,
                         BigDecimal marks) {
    }

    record PaperDetail(int paperKey,
                       int paperNo,
                       List<PaperQuestion> questions,
                       BigDecimal totalMarks) {
    }

    record ExamDetail(int examId,
                      int courseId,
                      String title,
                      LocalDateTime examDate,
                      int durationMin,
                      BigDecimal passMark,
                      int maxAttempts,
                      ExamStatus status,
                      List<PaperDetail> papers) {
    }

    /** @param examId null = create a new exam */
    record ExamDraft(Integer examId,
                     int courseId,
                     String title,
                     LocalDateTime examDate,
                     int durationMin,
                     BigDecimal passMark,
                     int maxAttempts) {
    }

    List<ExamRow> listExams(int courseId) throws ServiceException;

    ExamDetail getExam(int examId) throws ServiceException;

    /** @return the exam id */
    int saveExam(ExamDraft draft) throws ServiceException;

    /** @throws ServiceException CONFLICT if students already attempted it. */
    void deleteExam(int examId) throws ServiceException;

    void setExamStatus(int examId, ExamStatus status) throws ServiceException;

    // ---- Build your paper -------------------------------------------------

    record PaperQuestionDraft(int questionId, int order, BigDecimal marks) {
    }

    /** @param paperKey null = create a new paper for the exam */
    record PaperDraft(Integer paperKey, int examId, List<PaperQuestionDraft> questions) {
    }

    /** @return the paper key */
    int savePaper(PaperDraft draft) throws ServiceException;

    // ---- Question bank ----------------------------------------------------

    record TopicOption(int topicId, String name) {
    }

    record QuestionBankFilter(String keyword, Integer topicId, QuestionType type,
                              Integer difficulty) {
        public static QuestionBankFilter none() {
            return new QuestionBankFilter(null, null, null, null);
        }
    }

    record QuestionBankItem(int questionId,
                            String content,
                            QuestionType type,
                            int difficulty,
                            String topic) {
    }

    record OptionDraft(String content, boolean correct) {
    }

    /**
     * MCQ: at least 2 options, exactly one correct. Short answer: answerKey set.
     * @param questionId null = create
     */
    record QuestionDraft(Integer questionId,
                         int topicId,
                         QuestionType type,
                         String content,
                         int difficulty,
                         List<OptionDraft> options,
                         String answerKey) {
    }

    List<TopicOption> listTopics(int courseId) throws ServiceException;

    Page<QuestionBankItem> searchQuestionBank(int courseId, QuestionBankFilter filter,
                                              int page, int pageSize) throws ServiceException;

    QuestionDraft getQuestion(int questionId) throws ServiceException;

    /** @return the question id */
    int saveQuestion(int instructorId, QuestionDraft draft) throws ServiceException;

    /** @throws ServiceException CONFLICT when a paper that has attempts uses it. */
    void deleteQuestion(int questionId) throws ServiceException;

    // ---- Statistics tab ---------------------------------------------------

    /** Query 5: average score per topic, as earned/max so papers are comparable. */
    record TopicAverage(String topic, BigDecimal averagePercent) {
    }

    /** Query 3: questions most students got wrong, hardest first. */
    record HardQuestion(int questionId, String content, BigDecimal correctPercent) {
    }

    record CourseStatistics(int completedExamCount,
                            List<TopicAverage> topicAverages,
                            List<HardQuestion> hardestQuestions) {
    }

    CourseStatistics getStatistics(int courseId) throws ServiceException;

    record ExamColumn(int examId, String title) {
    }

    /** Both null = not attempted. */
    record ExamScore(BigDecimal score, BigDecimal maxScore) {
    }

    /**
     * @param scores one entry per {@link ExamColumn}, same order; null entry =
     *               student has no finished attempt for that exam
     */
    record StudentMarkRow(int studentId,
                          String name,
                          List<ExamScore> scores,
                          BigDecimal averagePercent,
                          int completedCount,
                          int totalCount) {
    }

    /**
     * @param totalStudents        for the "All students (n)" button
     * @param completedAllStudents for "Completed all exams (n)" (query 4)
     */
    record StudentMarkTable(List<ExamColumn> exams,
                            Page<StudentMarkRow> rows,
                            int totalStudents,
                            int completedAllStudents) {
    }

    /** Which attempt counts per student is decided by D09. */
    StudentMarkTable listStudentMarks(int courseId, boolean completedAllOnly,
                                      int page, int pageSize) throws ServiceException;

    // ---- Manual grading (short answers) -----------------------------------

    /** Identifies one ANSWER row. */
    record AnswerRef(int regisId, int attemptNo, int questionId) {
    }

    /** A submitted short answer that did not match the answer key and still has no mark. */
    record PendingAnswer(AnswerRef ref,
                         int studentId,
                         String studentName,
                         int examId,
                         String examTitle,
                         LocalDateTime submittedAt,
                         String question,
                         String answerText,
                         String answerKey,
                         BigDecimal maxMarks) {
    }

    /** Pending short answers of the course, oldest submission first. */
    List<PendingAnswer> listPendingGrading(int courseId) throws ServiceException;

    /**
     * Gives a manual mark (0 .. max marks, at most 2 decimals) to one submitted short answer.
     * A mark can be corrected later by calling this again.
     */
    void gradeAnswer(AnswerRef ref, BigDecimal mark) throws ServiceException;

    // ---- Project queries (PDMProjectQuery.sql / ReportDAO) --------------------------------

    /** The analytical queries, Q1..Q11 as numbered in PDMProjectQuery.sql. */
    enum ReportKind {
        HIGH_ERROR_QUESTIONS("Q1. Questions with a high error rate",
                "MCQs answered wrongly in more than N % of submitted attempts (CTE + GROUP BY)."),
        EXAM_PAPER_MARKS("Q2. Exam papers and marks",
                "Every question of every paper with its order and marks (5-table JOIN)."),
        STUDENTS_NO_ATTEMPTS("Q3. Students with no attempts",
                "Students who never started an attempt (NOT EXISTS)."),
        ATTEMPT_SCORES("Q4. Score of every attempt",
                "Total score of each submitted, fully graded attempt (nested IN)."),
        QUESTIONS_IN_EXAM_NOT_OTHER("Q5. Questions in exam A but not in exam B",
                "Set difference of two exams' questions (EXCEPT)."),
        TOPIC_QUESTION_COUNTS("Q6. Topics with many questions",
                "Topics having at least N questions (GROUP BY / HAVING)."),
        EXAMS_AVERAGE_PASS("Q7. Exams whose average reaches the pass mark",
                "Average score % of fully graded attempts >= Pass_mark (GROUP BY / HAVING)."),
        STUDENTS_MANY_ATTEMPTS("Q8. Students with many attempts",
                "Students with at least N attempts (GROUP BY / HAVING)."),
        EXAM_PERFORMANCE("Q9. Examination performance summary",
                "Attempts, average and highest score per exam (view v_attempt_scores)."),
        STUDENTS_ALL_EXAMS("Q10. Students who took every exam of this course",
                "Submitted an attempt for every published exam of the course (relational division)."),
        TOPIC_AVERAGES("Q11. Average score by topic",
                "Average mark and % per topic, submitted attempts (same SQL as view vw_AverageScoreByTopic).");

        private final String title;
        private final String description;

        ReportKind(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        /** True when the report uses {@link ReportParams#threshold()}. */
        public boolean usesThreshold() {
            return this == HIGH_ERROR_QUESTIONS || this == TOPIC_QUESTION_COUNTS || this == STUDENTS_MANY_ATTEMPTS;
        }

        /** True when the report compares {@link ReportParams#examA()} with {@link ReportParams#examB()}. */
        public boolean usesTwoExams() {
            return this == QUESTIONS_IN_EXAM_NOT_OTHER;
        }

        /** Value used when the user leaves the threshold empty (same as PDMProjectQuery.sql). */
        public Integer defaultThreshold() {
            return this == HIGH_ERROR_QUESTIONS ? Integer.valueOf(70) : usesThreshold() ? Integer.valueOf(2) : null;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** {@code threshold}/{@code examA}/{@code examB} may be null when the report does not use them. */
    record ReportParams(int courseId, Integer threshold, Integer examA, Integer examB) {
    }

    /** A query result ready to show in a table (values already formatted as text). */
    record ReportTable(ReportKind kind, List<String> columns, List<List<String>> rows) {
    }

    ReportTable runReport(ReportKind kind, ReportParams params) throws ServiceException;
}
