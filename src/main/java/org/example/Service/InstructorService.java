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
}
