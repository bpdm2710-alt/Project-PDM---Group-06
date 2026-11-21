-- ============================================================================
-- 03_demo_cleanup.sql — removes the "DEMO - ..." exams that 03_demo_seed.sql created, with every
-- registration / attempt / answer on them (also ones made in the app during the demo).
-- Nothing else is touched. Run 03_demo_seed.sql again to start a new demo session.
-- Also run by:  java -jar <app>.jar --cleanup-demo
-- ============================================================================
USE `Online_Examination`;
START TRANSACTION;
DELETE st FROM `SELECT_TABLE` st JOIN `REGISTRATION` r ON r.`RegisID` = st.`RegisID`
  JOIN `EXAMINATION` e ON e.`Exam_ID` = r.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE an FROM `ANSWER` an JOIN `REGISTRATION` r ON r.`RegisID` = an.`RegisID`
  JOIN `EXAMINATION` e ON e.`Exam_ID` = r.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE a FROM `ATTEMPT` a JOIN `REGISTRATION` r ON r.`RegisID` = a.`RegisID`
  JOIN `EXAMINATION` e ON e.`Exam_ID` = r.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE r FROM `REGISTRATION` r JOIN `EXAMINATION` e ON e.`Exam_ID` = r.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE pq FROM `PAPER_QUESTION` pq JOIN `PAPER` p ON p.`PaperKey` = pq.`PaperKey`
  JOIN `EXAMINATION` e ON e.`Exam_ID` = p.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE eqm FROM `EXAM_QUESTION_MARKS` eqm JOIN `EXAMINATION` e ON e.`Exam_ID` = eqm.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE p FROM `PAPER` p JOIN `EXAMINATION` e ON e.`Exam_ID` = p.`Exam_ID` WHERE e.`Title` LIKE 'DEMO - %';
DELETE FROM `EXAMINATION` WHERE `Title` LIKE 'DEMO - %';
COMMIT;
