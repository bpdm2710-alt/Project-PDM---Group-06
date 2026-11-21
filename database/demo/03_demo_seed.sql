-- ============================================================================
-- 03_demo_seed.sql  –  demo data with RELATIVE dates (NOW() +/- INTERVAL), so the exam flow can be
-- demonstrated on any day. Run AFTER normalized_21tables/02_online_examination_full_submission.sql.
-- Safe to run again: every run first removes the previous "DEMO - " exams and their data.
--
-- Times are written in Vietnam time (+07:00) because the desktop app compares them with the
-- clock of the student's PC (LocalDateTime.now()). Aiven's NOW() is UTC, hence SET time_zone.
--
-- What you get (course 1 = Principles of Database Management, taught by inst01/inst02):
--   DEMO - Open now          opened 5 min ago, 180 min, 2 attempts; sv101, sv102, sv103 registered
--                           -> log in as a student and press Start. 2 papers (same questions, other
--                           order): a retake gets the other paper (paper rotation).
--   DEMO - Upcoming          opens in 2 days; nobody registered -> test "Register" (PDM requires Data
--                           Structures and Algorithms: sv104 is blocked, sv106 / sv111 may register).
--   DEMO - Expired attempt   opened 3 h ago, 60 min, 1 attempt; sv101 has an attempt that was never
--                           submitted (EndTime NULL) -> opening My exams / Results must close and
--                           grade it automatically (fix of Step 1).
--   DEMO - Draft             DRAFT, not scheduled -> instructor can edit / build paper / publish.
-- Logins: students svNNN@student.hcmiu.edu.vn / Stu@123, instructors instNN@hcmiu.edu.vn / Inst@123
--   (after normalized_21tables/05_upgrade_password_hashes.sql; with the raw 02 seed the password is the
--    stored text, e.g. sha256:stu_hash_101, and the app upgrades it to a real hash on first login)
-- ============================================================================
USE `Online_Examination`;
SET time_zone = '+07:00';
SET @now = NOW();

-- ---- 0. remove the previous demo run (children first; trigger-safe order) -----------
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

START TRANSACTION;

-- The submitted schema has no AUTO_INCREMENT on Exam_ID / RegisID: ids are MAX + 1.
SELECT COALESCE(MAX(`Exam_ID`), 0) INTO @last_exam FROM `EXAMINATION` FOR UPDATE;
SELECT COALESCE(MAX(`RegisID`), 0) INTO @last_reg FROM `REGISTRATION` FOR UPDATE;

-- ---- 1. DEMO - Open now ----------------------------------------------------------------
SET @e1 = @last_exam + 1;
INSERT INTO `EXAMINATION` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`, `Status`)
VALUES (@e1, 'DEMO - Open now', DATE_SUB(@now, INTERVAL 5 MINUTE), 180, 50.00, 2, 1, 'PUBLISHED');
INSERT INTO `PAPER` (`PaperID`, `Exam_ID`) VALUES (1, @e1);
SET @p1 = LAST_INSERT_ID();
-- marks first (EXAM_QUESTION_MARKS), then the layout (PAPER_QUESTION) – trigger order
INSERT INTO `EXAM_QUESTION_MARKS` (`Exam_ID`, `Quest_ID`, `Marks`) VALUES
  (@e1, 1, 10.00), (@e1, 2, 10.00), (@e1, 3, 10.00), (@e1, 4, 10.00), (@e1, 21, 20.00);
INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES
  (@p1, 1, 1), (@p1, 2, 2), (@p1, 3, 3), (@p1, 4, 4), (@p1, 21, 5);
-- paper 2 = same 5 questions (same marks) in another order: a retake / the next student gets the other paper
INSERT INTO `PAPER` (`PaperID`, `Exam_ID`) VALUES (2, @e1);
SET @p1b = LAST_INSERT_ID();
INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES
  (@p1b, 4, 1), (@p1b, 3, 2), (@p1b, 2, 3), (@p1b, 1, 4), (@p1b, 21, 5);
INSERT INTO `REGISTRATION` (`RegisID`, `User_ID`, `Exam_ID`, `RegisDate`) VALUES
  (@last_reg + 1, 101, @e1, DATE_SUB(@now, INTERVAL 1 DAY)),
  (@last_reg + 2, 102, @e1, DATE_SUB(@now, INTERVAL 1 DAY)),
  (@last_reg + 3, 103, @e1, DATE_SUB(@now, INTERVAL 1 DAY));

-- ---- 2. DEMO - Upcoming ----------------------------------------------------------------
SET @e2 = @last_exam + 2;
INSERT INTO `EXAMINATION` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`, `Status`)
VALUES (@e2, 'DEMO - Upcoming', DATE_ADD(DATE(@now), INTERVAL 2 DAY) + INTERVAL 9 HOUR, 60, 50.00, 1, 1, 'PUBLISHED');
INSERT INTO `PAPER` (`PaperID`, `Exam_ID`) VALUES (1, @e2);
SET @p2 = LAST_INSERT_ID();
INSERT INTO `EXAM_QUESTION_MARKS` (`Exam_ID`, `Quest_ID`, `Marks`) VALUES
  (@e2, 5, 10.00), (@e2, 6, 10.00), (@e2, 7, 10.00), (@e2, 22, 20.00);
INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES
  (@p2, 5, 1), (@p2, 6, 2), (@p2, 7, 3), (@p2, 22, 4);

-- ---- 3. DEMO - Expired attempt -------------------------------------------------------
SET @e3 = @last_exam + 3;
INSERT INTO `EXAMINATION` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`, `Status`)
VALUES (@e3, 'DEMO - Expired attempt', DATE_SUB(@now, INTERVAL 3 HOUR), 60, 50.00, 1, 1, 'PUBLISHED');
INSERT INTO `PAPER` (`PaperID`, `Exam_ID`) VALUES (1, @e3);
SET @p3 = LAST_INSERT_ID();
INSERT INTO `EXAM_QUESTION_MARKS` (`Exam_ID`, `Quest_ID`, `Marks`) VALUES
  (@e3, 1, 10.00), (@e3, 8, 10.00), (@e3, 23, 20.00);
INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES
  (@p3, 1, 1), (@p3, 8, 2), (@p3, 23, 3);
SET @r3 = @last_reg + 4;
INSERT INTO `REGISTRATION` (`RegisID`, `User_ID`, `Exam_ID`, `RegisDate`) VALUES (@r3, 101, @e3, DATE_SUB(@now, INTERVAL 1 DAY));
-- started 2h55m ago with a 60-minute limit and never submitted
INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
VALUES (1, @r3, DATE_SUB(@now, INTERVAL 175 MINUTE), NULL, @p3);
-- one saved MCQ answer (correct option of question 1), not graded yet
INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`) VALUES (1, 1, @r3, NULL, NULL);
INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`)
SELECT `Option_no`, 1, 1, @r3 FROM `OPTIONS` WHERE `Quest_ID` = 1 AND `Bool_Correct` = 1 ORDER BY `Option_no` LIMIT 1;

-- ---- 4. DEMO - Draft -------------------------------------------------------------------
SET @e4 = @last_exam + 4;
INSERT INTO `EXAMINATION` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`, `Status`)
VALUES (@e4, 'DEMO - Draft', NULL, 45, 60.00, 1, 1, 'DRAFT');

COMMIT;

SELECT e.`Exam_ID`, e.`Title`, e.`Exam_date`, e.`Duration`, e.`Max_attempts`, e.`Status`,
       (SELECT COUNT(*) FROM `REGISTRATION` r WHERE r.`Exam_ID` = e.`Exam_ID`) AS registrations,
       (SELECT COUNT(*) FROM `ATTEMPT` a JOIN `REGISTRATION` r ON r.`RegisID` = a.`RegisID`
         WHERE r.`Exam_ID` = e.`Exam_ID` AND a.`EndTime` IS NULL) AS open_attempts
FROM `EXAMINATION` e WHERE e.`Title` LIKE 'DEMO - %' ORDER BY e.`Exam_ID`;
