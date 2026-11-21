-- ============================================================================
-- PDMProjectQuery.sql - analytical queries of the Online Examination project (Group 06)
-- Table names are UPPER CASE (MySQL on Linux/Aiven is case-sensitive). Run after
-- normalized_21tables/02_online_examination_full_submission.sql. Needs MySQL 8.0.31+ (EXCEPT).
-- Same SQL as src/main/java/org/example/DAO/jdbc/ReportDAO.java (one method per query).
--
-- Rules applied to every score query (Step 5 of the progress report):
--   * only SUBMITTED attempts count (ATTEMPT.EndTime IS NOT NULL) – attempts in progress are ignored;
--   * a total score is shown only for FULLY GRADED attempts (no ANSWER.MarkAwarded IS NULL left),
--     otherwise a short answer waiting for manual grading would give a partial score;
--   * the relational division only looks at PUBLISHED exams (drafts cannot be attempted).
-- ============================================================================
USE `Online_Examination`;

-- Q1. Questions with more than 70% error rate (MCQ, submitted attempts only)
WITH AttemptQuestion AS (
    SELECT ST.Quest_ID, ST.RegisID, ST.Attempt_no,
           MAX(CASE WHEN O.Bool_Correct = 0 THEN 1 ELSE 0 END) AS IsIncorrect
    FROM SELECT_TABLE ST
    JOIN OPTIONS O ON ST.Option_no = O.Option_no AND ST.Quest_ID = O.Quest_ID
    JOIN ATTEMPT A ON A.RegisID = ST.RegisID AND A.Attempt_no = ST.Attempt_no
    WHERE A.EndTime IS NOT NULL
    GROUP BY ST.Quest_ID, ST.RegisID, ST.Attempt_no
),
QuestionError AS (
    SELECT Quest_ID, COUNT(*) AS TotalAnswers, SUM(IsIncorrect) AS IncorrectAnswers,
           ROUND(SUM(IsIncorrect) * 100.0 / COUNT(*), 2) AS ErrorRate
    FROM AttemptQuestion
    GROUP BY Quest_ID
)
SELECT Q.Quest_ID, Q.Content AS Question_Content, QE.TotalAnswers, QE.IncorrectAnswers, QE.ErrorRate
FROM QuestionError QE
JOIN QUESTION Q ON QE.Quest_ID = Q.Quest_ID
WHERE QE.ErrorRate > 70
ORDER BY QE.ErrorRate DESC, Q.Quest_ID;

-- Q2. Multi-table JOIN – Exam paper and marks
SELECT E.Exam_ID, P.PaperID, Q.Quest_ID, Q.Content AS Question_Content, PQ.QuestionOrder, EQM.Marks
FROM EXAMINATION E
JOIN PAPER P ON E.Exam_ID = P.Exam_ID
JOIN PAPER_QUESTION PQ ON P.PaperKey = PQ.PaperKey
JOIN EXAM_QUESTION_MARKS EQM ON E.Exam_ID = EQM.Exam_ID AND PQ.Quest_ID = EQM.Quest_ID
JOIN QUESTION Q ON PQ.Quest_ID = Q.Quest_ID
ORDER BY E.Exam_ID, P.PaperID, PQ.QuestionOrder;

-- Q3. NOT EXISTS – Students with no attempts
SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name
FROM STUDENT S
JOIN `USER` U ON S.User_ID = U.User_ID
WHERE NOT EXISTS (
    SELECT 1
    FROM REGISTRATION R
    JOIN ATTEMPT A ON R.RegisID = A.RegisID
    WHERE R.User_ID = S.User_ID
)
ORDER BY S.User_ID;

-- Q4. Nested IN – Score of every submitted, fully graded attempt
SELECT S.User_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name, A.Attempt_no, R.Exam_ID,
       SUM(AN.MarkAwarded) AS Total_Score
FROM STUDENT S
JOIN `USER` U ON S.User_ID = U.User_ID
JOIN REGISTRATION R ON S.User_ID = R.User_ID
JOIN ATTEMPT A ON R.RegisID = A.RegisID
JOIN ANSWER AN ON A.RegisID = AN.RegisID AND A.Attempt_no = AN.Attempt_no
WHERE A.EndTime IS NOT NULL
  AND (A.RegisID, A.Attempt_no) IN (
      SELECT V.RegisID, V.Attempt_no FROM v_attempt_scores V WHERE V.IsFullyGraded = 1
  )
GROUP BY S.User_ID, U.FirstName, U.LastName, A.RegisID, A.Attempt_no, R.Exam_ID
ORDER BY S.User_ID, R.Exam_ID, A.Attempt_no;

-- Q5. Set operation (EXCEPT) – Questions used in Exam A but not in Exam B (here: exam 1 vs exam 2).
--     (Each question belongs to exactly ONE topic, so "topic A EXCEPT topic B" would always equal
--      topic A; comparing two exams' papers is the meaningful version of the set difference.)
SELECT Q.Quest_ID, Q.Content
FROM QUESTION Q
JOIN EXAM_QUESTION_MARKS EQM ON EQM.Quest_ID = Q.Quest_ID
WHERE EQM.Exam_ID = 1
EXCEPT
SELECT Q.Quest_ID, Q.Content
FROM QUESTION Q
JOIN EXAM_QUESTION_MARKS EQM ON EQM.Quest_ID = Q.Quest_ID
WHERE EQM.Exam_ID = 2
ORDER BY Quest_ID;

-- Q6. GROUP BY / HAVING – Topics with at least 2 questions
SELECT T.Topic_ID, T.Content AS Topic_Content, COUNT(Q.Quest_ID) AS Number_of_Questions
FROM TOPIC T
JOIN QUESTION Q ON Q.Topic_ID = T.Topic_ID
GROUP BY T.Topic_ID, T.Content
HAVING COUNT(Q.Quest_ID) >= 2
ORDER BY Number_of_Questions DESC, T.Topic_ID;

-- Q7. GROUP BY / HAVING – Exams whose average score (%) reaches their pass mark
SELECT E.Exam_ID, E.Exam_date, COUNT(*) AS Number_of_Attempts, ROUND(AVG(V.ScorePercent), 2) AS Average_Score_Percent
FROM EXAMINATION E
JOIN v_attempt_scores V ON E.Exam_ID = V.Exam_ID
WHERE V.IsFullyGraded = 1
GROUP BY E.Exam_ID, E.Exam_date, E.Pass_mark
HAVING AVG(V.ScorePercent) >= E.Pass_mark
ORDER BY Average_Score_Percent DESC, E.Exam_ID;

-- Q8. Students with at least 2 attempts
SELECT S.User_ID AS Student_ID, CONCAT(U.FirstName, ' ', U.LastName) AS Student_Name, COUNT(*) AS Total_Attempts
FROM STUDENT S
JOIN `USER` U ON U.User_ID = S.User_ID
JOIN REGISTRATION R ON R.User_ID = S.User_ID
JOIN ATTEMPT A ON A.RegisID = R.RegisID
GROUP BY S.User_ID, U.FirstName, U.LastName
HAVING COUNT(*) >= 2
ORDER BY Total_Attempts DESC, S.User_ID;

-- Q9. Examination performance summary (fully graded attempts)
SELECT E.Exam_ID, COUNT(*) AS Total_Attempts, ROUND(AVG(V.Total_Score), 2) AS Average_Score,
       MAX(V.Total_Score) AS Highest_Score
FROM EXAMINATION E
JOIN v_attempt_scores V ON E.Exam_ID = V.Exam_ID
WHERE V.IsFullyGraded = 1
GROUP BY E.Exam_ID
ORDER BY Average_Score DESC, E.Exam_ID;

-- Q10. Relational division – Students who submitted an attempt for EVERY published exam of course 1
SELECT S.User_ID, U.FirstName, U.LastName
FROM STUDENT S
JOIN `USER` U ON S.User_ID = U.User_ID
WHERE NOT EXISTS (
    SELECT 1
    FROM EXAMINATION E
    WHERE E.Course_ID = 1 AND E.Status = 'PUBLISHED'
      AND NOT EXISTS (
          SELECT 1
          FROM REGISTRATION R
          JOIN ATTEMPT A ON R.RegisID = A.RegisID
          WHERE R.User_ID = S.User_ID AND R.Exam_ID = E.Exam_ID AND A.EndTime IS NOT NULL
      )
)
ORDER BY S.User_ID;

-- Q11. Average score by topic (submitted attempts; raw mark and % of the question's marks)
SELECT T.Topic_ID, T.Content AS Topic_Name, ROUND(AVG(AN.MarkAwarded), 2) AS Average_Score,
       ROUND(AVG(AN.MarkAwarded * 100.0 / EQM.Marks), 2) AS Average_Percent
FROM TOPIC T
JOIN QUESTION Q ON T.Topic_ID = Q.Topic_ID
JOIN ANSWER AN ON Q.Quest_ID = AN.Quest_ID
JOIN ATTEMPT A ON A.RegisID = AN.RegisID AND A.Attempt_no = AN.Attempt_no
JOIN REGISTRATION R ON R.RegisID = A.RegisID
JOIN EXAM_QUESTION_MARKS EQM ON EQM.Exam_ID = R.Exam_ID AND EQM.Quest_ID = AN.Quest_ID
WHERE AN.MarkAwarded IS NOT NULL AND A.EndTime IS NOT NULL
GROUP BY T.Topic_ID, T.Content
ORDER BY T.Topic_ID;

-- Q12. Shared view: average score by topic (same rules as Q11). The submitted 02 does not create it,
--      so this statement creates it (the app itself does not depend on the view).
CREATE OR REPLACE VIEW vw_AverageScoreByTopic AS
SELECT T.Topic_ID, T.Content AS Topic_Name, ROUND(AVG(AN.MarkAwarded), 2) AS Average_Score,
       ROUND(AVG(AN.MarkAwarded * 100.0 / EQM.Marks), 2) AS Average_Percent
FROM TOPIC T
JOIN QUESTION Q ON T.Topic_ID = Q.Topic_ID
JOIN ANSWER AN ON Q.Quest_ID = AN.Quest_ID
JOIN ATTEMPT A ON A.RegisID = AN.RegisID AND A.Attempt_no = AN.Attempt_no
JOIN REGISTRATION R ON R.RegisID = A.RegisID
JOIN EXAM_QUESTION_MARKS EQM ON EQM.Exam_ID = R.Exam_ID AND EQM.Quest_ID = AN.Quest_ID
WHERE AN.MarkAwarded IS NOT NULL AND A.EndTime IS NOT NULL
GROUP BY T.Topic_ID, T.Content;

SELECT * FROM vw_AverageScoreByTopic ORDER BY Topic_ID;
