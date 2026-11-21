-- ============================================================================
-- 04_dml_and_transactions.sql
-- IT079IU - Principles of Database Management | Group 06 | Topic 20: Online Examination System
-- Target DBMS: MySQL 8.0.16+ / MySQL 8.4 (InnoDB). Run after 02_online_examination_full_submission.sql.
--
-- Purpose (assignment, "What to submit"):
--   * at least one INSERT, one UPDATE and one DELETE for each main table;
--   * operations that change two tables together inside a transaction, ended once with
--     COMMIT and once with ROLLBACK.
--
-- Safe on a shared database:
--   * every row uses its own id block (User_ID / Course_ID / Topic_ID / Quest_ID / Exam_ID 9001-9099,
--     RegisID 90001-90099), outside the sample data (02) and the demo data (database/demo);
--   * Part 4 deletes every row this script created and Part 5 checks that none is left,
--     so the database ends exactly as it started;
--   * Part 0 removes rows left by an interrupted run, so the script can be run again at any time.
-- The application does not run this file (it is not packaged in the jar).
-- Every statement respects the constraints and triggers of 02, so the script runs without errors.
-- ============================================================================
USE `Online_Examination`;

-- ----------------------------------------------------------------------------
-- PART 0. Remove rows left by an interrupted run (children first; no row matches on a clean database)
-- ----------------------------------------------------------------------------
DELETE FROM `SELECT_TABLE` WHERE `RegisID` BETWEEN 90001 AND 90099;
DELETE FROM `ANSWER`       WHERE `RegisID` BETWEEN 90001 AND 90099;
DELETE FROM `ATTEMPT`      WHERE `RegisID` BETWEEN 90001 AND 90099;
DELETE FROM `REGISTRATION` WHERE `RegisID` BETWEEN 90001 AND 90099;
DELETE pq FROM `PAPER_QUESTION` pq JOIN `PAPER` p ON p.`PaperKey` = pq.`PaperKey` WHERE p.`Exam_ID` BETWEEN 9001 AND 9099;
DELETE FROM `PAPER`               WHERE `Exam_ID` BETWEEN 9001 AND 9099;
DELETE FROM `EXAM_QUESTION_MARKS` WHERE `Exam_ID` BETWEEN 9001 AND 9099;
DELETE FROM `EXAMINATION`         WHERE `Exam_ID` BETWEEN 9001 AND 9099;
DELETE FROM `OPTIONS`      WHERE `Quest_ID` BETWEEN 9001 AND 9099;
DELETE FROM `MCQ`          WHERE `Quest_ID` BETWEEN 9001 AND 9099;
DELETE FROM `SHORT_ANSWER` WHERE `Quest_ID` BETWEEN 9001 AND 9099;
DELETE FROM `QUESTION`     WHERE `Quest_ID` BETWEEN 9001 AND 9099;
DELETE FROM `TEACH`        WHERE `Course_ID` BETWEEN 9001 AND 9099;
DELETE FROM `COVERS`       WHERE `Course_ID` BETWEEN 9001 AND 9099;
DELETE FROM `TOPIC`        WHERE `Topic_ID`  BETWEEN 9001 AND 9099;
DELETE FROM `COURSE`       WHERE `Course_ID` BETWEEN 9001 AND 9099;
DELETE FROM `USER_PHONE`   WHERE `User_ID`   BETWEEN 9001 AND 9099;
DELETE FROM `STUDENT`      WHERE `User_ID`   BETWEEN 9001 AND 9099;
DELETE FROM `INSTRUCTOR`   WHERE `User_ID`   BETWEEN 9001 AND 9099;
DELETE FROM `USER`         WHERE `User_ID`   BETWEEN 9001 AND 9099;

-- ----------------------------------------------------------------------------
-- PART 1. INSERT: one small, valid scenario (an instructor writes two questions for a new course,
--         builds an examination with one paper; a student will take it in Part 3)
-- ----------------------------------------------------------------------------
-- USER + subclasses (ISA): one instructor and one student
INSERT INTO `USER` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`) VALUES
  (9001, 'Demo', 'Instructor', '1985-03-12', 'dml.instructor9001@example.edu', 'not-a-real-hash'),
  (9002, 'Demo', 'Student',    '2005-09-01', 'dml.student9002@example.edu',    'not-a-real-hash');
INSERT INTO `INSTRUCTOR` (`User_ID`, `Department`)      VALUES (9001, 'Computer Science');
INSERT INTO `STUDENT`    (`User_ID`, `Enrollment_year`) VALUES (9002, 2024);
INSERT INTO `USER_PHONE` (`Phone`, `User_ID`)           VALUES ('0900000001', 9002);

-- COURSE, TOPIC and their links
INSERT INTO `COURSE` (`Course_ID`, `CName`, `Credits`) VALUES (9001, 'DML Demo Course', 3);
INSERT INTO `TOPIC`  (`Topic_ID`, `Content`)          VALUES (9001, 'DML demo topic');
INSERT INTO `COVERS` (`Course_ID`, `Topic_ID`)        VALUES (9001, 9001);
INSERT INTO `TEACH`  (`User_ID`, `Course_ID`, `Semester`) VALUES (9001, 9001, '2029-S1');

-- QUESTION (one multiple-choice with two options, one short answer)
INSERT INTO `QUESTION` (`Quest_ID`, `Content`, `Difficulty`, `Topic_ID`, `User_ID`) VALUES
  (9001, 'Which SQL keyword ends a transaction and keeps its changes?', 2, 9001, 9001),
  (9002, 'Name the SQL keyword that undoes the changes of a transaction.', 2, 9001, 9001);
INSERT INTO `MCQ`          (`Quest_ID`) VALUES (9001);
INSERT INTO `SHORT_ANSWER` (`Quest_ID`, `Answer_key`) VALUES (9002, 'ROLLBACK');
INSERT INTO `OPTIONS` (`Option_no`, `Quest_ID`, `Op_Content`, `Bool_Correct`) VALUES
  (1, 9001, 'COMMIT', 1),
  (2, 9001, 'SAVE',   0);

-- EXAMINATION (starts as DRAFT), its maximum marks, one PAPER and the paper layout
INSERT INTO `EXAMINATION` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`, `Status`)
VALUES (9001, 'DML Demo Exam', '2030-01-15 09:00:00', 30, 50.00, 2, 9001, 'DRAFT');
INSERT INTO `EXAM_QUESTION_MARKS` (`Exam_ID`, `Quest_ID`, `Marks`) VALUES (9001, 9001, 5.00), (9001, 9002, 5.00);
INSERT INTO `PAPER` (`PaperID`, `Exam_ID`) VALUES (1, 9001);   -- PaperKey is AUTO_INCREMENT
SET @paper_key = LAST_INSERT_ID();
INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES (@paper_key, 9001, 1), (@paper_key, 9002, 2);

SELECT 'PART 1 INSERT' AS step, (SELECT COUNT(*) FROM `USER` WHERE `User_ID` BETWEEN 9001 AND 9099) AS users,
       (SELECT COUNT(*) FROM `QUESTION` WHERE `Quest_ID` BETWEEN 9001 AND 9099) AS questions,
       (SELECT COUNT(*) FROM `PAPER_QUESTION` WHERE `PaperKey` = @paper_key) AS paper_questions;

-- ----------------------------------------------------------------------------
-- PART 2. UPDATE (done before any attempt exists, because papers, options and marks are frozen
--         once a student has used them: BR-43)
-- ----------------------------------------------------------------------------
UPDATE `USER`     SET `Email` = 'dml.student9002.new@example.edu' WHERE `User_ID` = 9002;
UPDATE `STUDENT`  SET `Enrollment_year` = 2025                    WHERE `User_ID` = 9002;
UPDATE `INSTRUCTOR` SET `Department` = 'Information Technology'   WHERE `User_ID` = 9001;
UPDATE `COURSE`   SET `Credits` = 4                               WHERE `Course_ID` = 9001;
UPDATE `TOPIC`    SET `Content` = 'Transactions (DML demo)'       WHERE `Topic_ID` = 9001;
UPDATE `QUESTION` SET `Difficulty` = 3                            WHERE `Quest_ID` = 9001;
UPDATE `OPTIONS`  SET `Op_Content` = 'SAVE WORK'                  WHERE `Quest_ID` = 9001 AND `Option_no` = 2;
UPDATE `EXAM_QUESTION_MARKS` SET `Marks` = 6.00                   WHERE `Exam_ID` = 9001 AND `Quest_ID` = 9002;
UPDATE `PAPER`    SET `PaperID` = 2                               WHERE `PaperKey` = @paper_key;
UPDATE `EXAMINATION` SET `Duration` = 45, `Status` = 'PUBLISHED'  WHERE `Exam_ID` = 9001;   -- publish (BR-51)

SELECT 'PART 2 UPDATE' AS step, e.`Status`, e.`Duration`, c.`Credits`, u.`Email`
FROM `EXAMINATION` e JOIN `COURSE` c ON c.`Course_ID` = e.`Course_ID`
JOIN `USER` u ON u.`User_ID` = 9002
WHERE e.`Exam_ID` = 9001;

-- ----------------------------------------------------------------------------
-- PART 3. TRANSACTIONS that change two tables together
-- ----------------------------------------------------------------------------
-- 3a. COMMIT: register the student for the examination AND start the first attempt.
--     Both rows are kept, or neither (REGISTRATION + ATTEMPT).
START TRANSACTION;
  INSERT INTO `REGISTRATION` (`RegisID`, `RegisDate`, `User_ID`, `Exam_ID`)
  VALUES (90001, '2029-12-01 10:00:00', 9002, 9001);
  INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
  VALUES (1, 90001, '2030-01-15 09:05:00', NULL, @paper_key);
COMMIT;

SELECT '3a after COMMIT' AS step,
       (SELECT COUNT(*) FROM `REGISTRATION` WHERE `RegisID` = 90001) AS registrations,
       (SELECT COUNT(*) FROM `ATTEMPT` WHERE `RegisID` = 90001)      AS attempts;   -- 1, 1

-- 3b. ROLLBACK: start a second attempt AND record an answer for it, then cancel.
--     Inside the transaction both rows are visible; after ROLLBACK neither exists (ATTEMPT + ANSWER).
START TRANSACTION;
  INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
  VALUES (2, 90001, '2030-01-15 10:00:00', NULL, @paper_key);
  INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
  VALUES (9002, 2, 90001, 'COMMIT', 0.00);
  SELECT '3b inside transaction' AS step,
         (SELECT COUNT(*) FROM `ATTEMPT` WHERE `RegisID` = 90001) AS attempts,           -- 2
         (SELECT COUNT(*) FROM `ANSWER`  WHERE `RegisID` = 90001) AS answers;            -- 1
ROLLBACK;

SELECT '3b after ROLLBACK' AS step,
       (SELECT COUNT(*) FROM `ATTEMPT` WHERE `RegisID` = 90001) AS attempts,             -- 1
       (SELECT COUNT(*) FROM `ANSWER`  WHERE `RegisID` = 90001) AS answers;              -- 0

-- 3c. COMMIT: answer both questions of attempt 1 (ANSWER + SELECT_TABLE for the multiple-choice
--     question) and submit the attempt (ATTEMPT.EndTime) as one unit.
START TRANSACTION;
  INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`) VALUES
    (9001, 1, 90001, NULL, 5.00),            -- multiple choice: no text, option chosen below
    (9002, 1, 90001, 'rollback', NULL);      -- short answer: waits for grading
  INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`) VALUES (1, 9001, 1, 90001);
  UPDATE `ATTEMPT` SET `EndTime` = '2030-01-15 09:40:00' WHERE `RegisID` = 90001 AND `Attempt_no` = 1;
COMMIT;

-- UPDATE on the remaining tables: grade the short answer, correct the registration date, change the phone number
UPDATE `ANSWER` SET `MarkAwarded` = 6.00 WHERE `RegisID` = 90001 AND `Attempt_no` = 1 AND `Quest_ID` = 9002;
UPDATE `REGISTRATION` SET `RegisDate` = '2029-12-02 08:30:00' WHERE `RegisID` = 90001;
UPDATE `USER_PHONE` SET `Phone` = '0900000002' WHERE `User_ID` = 9002 AND `Phone` = '0900000001';

SELECT 'PART 3 result' AS step, s.`Total_Score`, s.`ScorePercent`, s.`IsPassed`
FROM `v_attempt_scores` s WHERE s.`RegisID` = 90001 AND s.`Attempt_no` = 1;   -- 11.00, 100.00, 1

-- ----------------------------------------------------------------------------
-- PART 4. DELETE: remove everything created above, children before parents
--         (foreign keys are ON DELETE RESTRICT, so the order matters)
-- ----------------------------------------------------------------------------
DELETE FROM `SELECT_TABLE` WHERE `RegisID` = 90001;
DELETE FROM `ANSWER`       WHERE `RegisID` = 90001;
DELETE FROM `ATTEMPT`      WHERE `RegisID` = 90001;
DELETE FROM `REGISTRATION` WHERE `RegisID` = 90001;
DELETE FROM `PAPER_QUESTION` WHERE `PaperKey` = @paper_key;
DELETE FROM `PAPER`          WHERE `PaperKey` = @paper_key;
DELETE FROM `EXAM_QUESTION_MARKS` WHERE `Exam_ID` = 9001;
DELETE FROM `EXAMINATION`         WHERE `Exam_ID` = 9001;
DELETE FROM `OPTIONS`      WHERE `Quest_ID` = 9001;
DELETE FROM `MCQ`          WHERE `Quest_ID` = 9001;
DELETE FROM `SHORT_ANSWER` WHERE `Quest_ID` = 9002;
DELETE FROM `QUESTION`     WHERE `Quest_ID` IN (9001, 9002);
DELETE FROM `TEACH`        WHERE `Course_ID` = 9001;
DELETE FROM `COVERS`       WHERE `Course_ID` = 9001;
DELETE FROM `TOPIC`        WHERE `Topic_ID` = 9001;
DELETE FROM `COURSE`       WHERE `Course_ID` = 9001;
DELETE FROM `USER_PHONE`   WHERE `User_ID` = 9002;
DELETE FROM `STUDENT`      WHERE `User_ID` = 9002;
DELETE FROM `INSTRUCTOR`   WHERE `User_ID` = 9001;
DELETE FROM `USER`         WHERE `User_ID` IN (9001, 9002);

-- The deleted paper used one AUTO_INCREMENT value; give the counter back (MySQL sets it to MAX(PaperKey) + 1).
ALTER TABLE `PAPER` AUTO_INCREMENT = 1;

-- ----------------------------------------------------------------------------
-- PART 5. Check: nothing created by this script is left (every count must be 0)
-- ----------------------------------------------------------------------------
SELECT 'PART 5 leftovers' AS step,
       (SELECT COUNT(*) FROM `USER`         WHERE `User_ID`  BETWEEN 9001 AND 9099)   AS users,
       (SELECT COUNT(*) FROM `COURSE`       WHERE `Course_ID` BETWEEN 9001 AND 9099)  AS courses,
       (SELECT COUNT(*) FROM `QUESTION`     WHERE `Quest_ID` BETWEEN 9001 AND 9099)   AS questions,
       (SELECT COUNT(*) FROM `EXAMINATION`  WHERE `Exam_ID`  BETWEEN 9001 AND 9099)   AS exams,
       (SELECT COUNT(*) FROM `REGISTRATION` WHERE `RegisID`  BETWEEN 90001 AND 90099) AS registrations;
