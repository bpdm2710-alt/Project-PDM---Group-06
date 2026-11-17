-- READ-ONLY audit for the exact 20-table schema in the supplied script.sql.
-- Select that schema explicitly in the client. Do not run this against the
-- PaperKey proposal without adapting the joins. A zero count on an empty DB
-- does not prove the constraints are implemented. No data is repaired here.
-- Names must match the exported lower-case table names.

SELECT DATABASE() AS selected_schema, VERSION() AS mysql_version;

SELECT 'duplicate_email' AS rule, COUNT(*) AS violations
FROM (SELECT Email FROM `user` GROUP BY Email HAVING COUNT(*)>1) x;
SELECT 'duplicate_student_exam' AS rule, COUNT(*) AS violations
FROM (SELECT User_ID,Exam_ID FROM registration GROUP BY User_ID,Exam_ID HAVING COUNT(*)>1) x;
SELECT 'duplicate_paper_order' AS rule, COUNT(*) AS violations
FROM (SELECT Exam_ID,PaperID,QuestionOrder FROM contains
      GROUP BY Exam_ID,PaperID,QuestionOrder HAVING COUNT(*)>1) x;

SELECT 'attempt_wrong_exam' AS rule, COUNT(*) AS violations
FROM attempt a JOIN registration r ON r.RegisID=a.RegisID
WHERE a.Exam_ID<>r.Exam_ID;
SELECT 'answer_not_on_paper' AS rule, COUNT(*) AS violations
FROM answer an JOIN attempt a ON a.RegisID=an.RegisID AND a.Attempt_no=an.Attempt_no
LEFT JOIN contains c ON c.Exam_ID=a.Exam_ID AND c.PaperID=a.PaperID AND c.Quest_ID=an.Quest_ID
WHERE c.Quest_ID IS NULL;
SELECT 'awarded_mark_out_of_range' AS rule, COUNT(*) AS violations
FROM answer an JOIN attempt a ON a.RegisID=an.RegisID AND a.Attempt_no=an.Attempt_no
JOIN contains c ON c.Exam_ID=a.Exam_ID AND c.PaperID=a.PaperID AND c.Quest_ID=an.Quest_ID
WHERE an.MarkAwarded<0 OR an.MarkAwarded>c.Marks;

SELECT 'selection_without_answer' AS rule, COUNT(*) AS violations
FROM select_table s LEFT JOIN answer a
  ON a.Quest_ID=s.Quest_ID AND a.Attempt_no=s.Attempt_no AND a.RegisID=s.RegisID
WHERE a.Quest_ID IS NULL;
SELECT 'multiple_choices_if_single_select' AS rule, COUNT(*) AS violations
FROM (SELECT Quest_ID,Attempt_no,RegisID FROM select_table
      GROUP BY Quest_ID,Attempt_no,RegisID HAVING COUNT(*)>1) x;

SELECT 'user_total_or_disjoint' AS rule, COUNT(*) AS violations
FROM `user` u LEFT JOIN student s ON s.User_ID=u.User_ID
LEFT JOIN instructor i ON i.User_ID=u.User_ID
WHERE (s.User_ID IS NULL AND i.User_ID IS NULL)
   OR (s.User_ID IS NOT NULL AND i.User_ID IS NOT NULL);
SELECT 'question_total_or_disjoint' AS rule, COUNT(*) AS violations
FROM question q LEFT JOIN mcq m ON m.Quest_ID=q.Quest_ID
LEFT JOIN short_answer s ON s.Quest_ID=q.Quest_ID
WHERE (m.Quest_ID IS NULL AND s.Quest_ID IS NULL)
   OR (m.Quest_ID IS NOT NULL AND s.Quest_ID IS NOT NULL);

SELECT 'attempt_number_or_limit' AS rule, COUNT(*) AS violations
FROM attempt a JOIN registration r ON r.RegisID=a.RegisID
JOIN examination e ON e.Exam_ID=r.Exam_ID
WHERE a.Attempt_no<1 OR a.Attempt_no>e.Max_attempts;
SELECT 'too_many_attempts' AS rule, COUNT(*) AS violations
FROM (SELECT r.RegisID FROM registration r JOIN examination e ON e.Exam_ID=r.Exam_ID
      JOIN attempt a ON a.RegisID=r.RegisID
      GROUP BY r.RegisID,e.Max_attempts HAVING COUNT(*)>e.Max_attempts) x;
SELECT 'end_before_start' AS rule, COUNT(*) AS violations
FROM attempt WHERE EndTime<StartTime;

SELECT 'mcq_incomplete_if_single_correct' AS rule, COUNT(*) AS violations
FROM (SELECT m.Quest_ID FROM mcq m LEFT JOIN options o ON o.Quest_ID=m.Quest_ID
      GROUP BY m.Quest_ID
      HAVING COUNT(o.Option_no)<2 OR SUM(CASE WHEN o.Bool_Correct=1 THEN 1 ELSE 0 END)<>1) x;
SELECT 'invalid_boolean' AS rule, COUNT(*) AS violations
FROM options WHERE Bool_Correct NOT IN (0,1);
SELECT 'self_prerequisite' AS rule, COUNT(*) AS violations
FROM requires WHERE Course_ID_1=Course_ID_2;

SELECT 'paper_question_outside_course_topics' AS rule, COUNT(*) AS violations
FROM contains c JOIN examination e ON e.Exam_ID=c.Exam_ID
JOIN question q ON q.Quest_ID=c.Quest_ID
LEFT JOIN covers cv ON cv.Course_ID=e.Course_ID AND cv.Topic_ID=q.Topic_ID
WHERE cv.Course_ID IS NULL;

-- Diagnostic only: not a violation if D07 allows per-paper marks.
SELECT 'different_marks_same_exam_question_DIAGNOSTIC' AS rule, COUNT(*) AS violations
FROM (SELECT Exam_ID,Quest_ID FROM contains GROUP BY Exam_ID,Quest_ID
      HAVING COUNT(DISTINCT Marks)>1) x;

-- Inspect declared keys and actions; an index alone is not proof of a UNIQUE.
SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA=DATABASE()
ORDER BY TABLE_NAME,CONSTRAINT_TYPE,CONSTRAINT_NAME;
SELECT TABLE_NAME,CONSTRAINT_NAME,UPDATE_RULE,DELETE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA=DATABASE()
ORDER BY TABLE_NAME,CONSTRAINT_NAME;

-- Remaining checks: multi-edge prerequisite cycles, true course/topic total
-- participation, parent-side updates, concurrency, historical edits and query
-- grading denominators require the wider acceptance matrix and team decisions.
