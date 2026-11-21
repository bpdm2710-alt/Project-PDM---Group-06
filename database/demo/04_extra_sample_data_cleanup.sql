-- ============================================================================
-- 04_extra_sample_data_cleanup.sql — removes everything 04_extra_sample_data.sql inserted
-- (also registrations/attempts that students made later in the app on those exams).
-- Fails with a foreign-key / freeze error only if an exam created in the app uses one of the
-- sample questions: remove that question from the exam first.
-- ============================================================================
USE `Online_Examination`;
START TRANSACTION;
-- Remove the sample block: exams 2001-2028 (with every registration/attempt on them, also ones made later
-- in the app) and questions 2001-2084. Course 1 (PDM) and the DEMO exams are not touched.
DELETE st FROM `SELECT_TABLE` st JOIN `REGISTRATION` r ON r.`RegisID` = st.`RegisID` WHERE r.`Exam_ID` BETWEEN 2001 AND 2028;
DELETE an FROM `ANSWER` an JOIN `REGISTRATION` r ON r.`RegisID` = an.`RegisID` WHERE r.`Exam_ID` BETWEEN 2001 AND 2028;
DELETE a FROM `ATTEMPT` a JOIN `REGISTRATION` r ON r.`RegisID` = a.`RegisID` WHERE r.`Exam_ID` BETWEEN 2001 AND 2028;
DELETE FROM `REGISTRATION` WHERE `Exam_ID` BETWEEN 2001 AND 2028;
DELETE pq FROM `PAPER_QUESTION` pq JOIN `PAPER` p ON p.`PaperKey` = pq.`PaperKey` WHERE p.`Exam_ID` BETWEEN 2001 AND 2028;
DELETE FROM `EXAM_QUESTION_MARKS` WHERE `Exam_ID` BETWEEN 2001 AND 2028;
DELETE FROM `PAPER` WHERE `Exam_ID` BETWEEN 2001 AND 2028;
DELETE FROM `EXAMINATION` WHERE `Exam_ID` BETWEEN 2001 AND 2028;
DELETE FROM `OPTIONS` WHERE `Quest_ID` BETWEEN 2001 AND 2084;
DELETE FROM `MCQ` WHERE `Quest_ID` BETWEEN 2001 AND 2084;
DELETE FROM `SHORT_ANSWER` WHERE `Quest_ID` BETWEEN 2001 AND 2084;
DELETE FROM `QUESTION` WHERE `Quest_ID` BETWEEN 2001 AND 2084;
COMMIT;
