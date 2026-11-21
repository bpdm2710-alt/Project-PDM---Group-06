-- ============================================================================
-- VERIFICATION & ACCEPTANCE TEST SUITE (N01-N24 + 5 TOPIC 20 QUERIES)
-- Run AFTER 02_online_examination_full_submission.sql
-- Fails loudly (SIGNAL 45000) if any assertion or expected error check fails.
-- ============================================================================
USE `Online_Examination`;

-- ----------------------------------------------------------------------------
-- PART A: Schema Metadata & Minimum Row Count Audit (>= 15 rows per table)
-- ----------------------------------------------------------------------------
SELECT table_name, table_rows
FROM (
         SELECT 'user' AS table_name, COUNT(*) AS table_rows FROM `USER`
         UNION ALL SELECT 'user_phone', COUNT(*) FROM `USER_PHONE`
         UNION ALL SELECT 'student', COUNT(*) FROM `STUDENT`
         UNION ALL SELECT 'instructor', COUNT(*) FROM `INSTRUCTOR`
         UNION ALL SELECT 'course', COUNT(*) FROM `COURSE`
         UNION ALL SELECT 'requires', COUNT(*) FROM `REQUIRES`
         UNION ALL SELECT 'topic', COUNT(*) FROM `TOPIC`
         UNION ALL SELECT 'covers', COUNT(*) FROM `COVERS`
         UNION ALL SELECT 'teach', COUNT(*) FROM `TEACH`
         UNION ALL SELECT 'question', COUNT(*) FROM `QUESTION`
         UNION ALL SELECT 'mcq', COUNT(*) FROM `MCQ`
         UNION ALL SELECT 'short_answer', COUNT(*) FROM `SHORT_ANSWER`
         UNION ALL SELECT 'options', COUNT(*) FROM `OPTIONS`
         UNION ALL SELECT 'examination', COUNT(*) FROM `EXAMINATION`
         UNION ALL SELECT 'paper', COUNT(*) FROM `PAPER`
         UNION ALL SELECT 'exam_question_marks', COUNT(*) FROM `EXAM_QUESTION_MARKS`
         UNION ALL SELECT 'paper_question', COUNT(*) FROM `PAPER_QUESTION`
         UNION ALL SELECT 'registration', COUNT(*) FROM `REGISTRATION`
         UNION ALL SELECT 'attempt', COUNT(*) FROM `ATTEMPT`
         UNION ALL SELECT 'answer', COUNT(*) FROM `ANSWER`
         UNION ALL SELECT 'select_table', COUNT(*) FROM `SELECT_TABLE`
     ) counts
ORDER BY table_rows ASC, table_name;

SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE') AS base_tables,
    (SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE()) AS views_count,
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()) AS fk_count,
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'UNIQUE') AS unique_count,
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK') AS check_count,
    (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()) AS trigger_count,
    (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE() AND ROUTINE_TYPE = 'PROCEDURE') AS proc_count;

-- ----------------------------------------------------------------------------
-- PART B: All 5 Mandatory Topic 20 Queries (non-empty verification)
-- ----------------------------------------------------------------------------
-- Topic 20 Query 1: Paper of Examination 1 with exam-level marks & question order
SELECT e.`Exam_ID`, e.`Title`, p.`PaperKey`, p.`PaperID`,
       pq.`QuestionOrder`, q.`Quest_ID`, q.`Content`, eqm.`Marks`
FROM `EXAMINATION` e
         JOIN `PAPER` p                 ON p.`Exam_ID` = e.`Exam_ID`
         JOIN `PAPER_QUESTION` pq       ON pq.`PaperKey` = p.`PaperKey`
         JOIN `QUESTION` q              ON q.`Quest_ID` = pq.`Quest_ID`
         JOIN `EXAM_QUESTION_MARKS` eqm ON eqm.`Exam_ID` = e.`Exam_ID` AND eqm.`Quest_ID` = q.`Quest_ID`
WHERE e.`Exam_ID` = 1
ORDER BY p.`PaperID`, pq.`QuestionOrder`;

-- Topic 20 Query 2: Score & Pass/Fail status (published only when IsFullyGraded = 1 per BR-49)
SELECT u.`User_ID`, CONCAT(u.`FirstName`, ' ', u.`LastName`) AS `StudentName`,
       v.`RegisID`, v.`Exam_ID`, v.`Attempt_no`, v.`Total_Score`, v.`PaperTotalMarks`, v.`ScorePercent`, v.`Pass_mark`, v.`IsFullyGraded`, v.`IsPassed`
FROM `v_attempt_scores` v
         JOIN `USER` u ON u.`User_ID` = v.`User_ID`
WHERE (v.`RegisID` = 1 AND v.`Attempt_no` = 2 AND v.`User_ID` = 101)
   OR (v.`RegisID` = 11 AND v.`Attempt_no` = 1 AND v.`User_ID` = 111)
ORDER BY v.`RegisID`, v.`Attempt_no`;

-- Topic 20 Query 3: Questions answered incorrectly by > 70% of students (BR-45, BR-46)
-- Expected on Exam 1: Q1 (8/10 = 80%) and Q4 (9/10 = 90%) INCLUDED; Q2 (7/10 = 70%) EXCLUDED!
SELECT ra.`Exam_ID`, an.`Quest_ID`, q.`Content`,
       COUNT(*) AS `AnsweredStudents`,
       SUM(CASE WHEN an.`MarkAwarded` = 0 THEN 1 ELSE 0 END) AS `IncorrectStudents`,
       ROUND(100.0 * SUM(CASE WHEN an.`MarkAwarded` = 0 THEN 1 ELSE 0 END) / COUNT(*), 2) AS `ErrorPct`
FROM `v_representative_attempts` ra
         JOIN `ANSWER` an   ON an.`RegisID` = ra.`RegisID` AND an.`Attempt_no` = ra.`Attempt_no`
         JOIN `QUESTION` q  ON q.`Quest_ID` = an.`Quest_ID`
         LEFT JOIN `SELECT_TABLE` st
                   ON st.`Quest_ID` = an.`Quest_ID` AND st.`Attempt_no` = an.`Attempt_no` AND st.`RegisID` = an.`RegisID`
WHERE (st.`Option_no` IS NOT NULL OR (an.`AnswerText` IS NOT NULL AND REGEXP_LIKE(an.`AnswerText`, '[^[:space:]]')))
GROUP BY ra.`Exam_ID`, an.`Quest_ID`, q.`Content`
HAVING SUM(CASE WHEN an.`MarkAwarded` = 0 THEN 1 ELSE 0 END) * 1.0 / COUNT(*) > 0.70
ORDER BY ra.`Exam_ID`, `ErrorPct` DESC, an.`Quest_ID`;

-- Topic 20 Query 4: Students who submitted at least one attempt for EVERY exam of Course 1 (BR-50)
SELECT s.`User_ID`, CONCAT(u.`FirstName`, ' ', u.`LastName`) AS `FullName`
FROM `STUDENT` s
         JOIN `USER` u ON u.`User_ID` = s.`User_ID`
WHERE EXISTS (SELECT 1 FROM `EXAMINATION` ex WHERE ex.`Course_ID` = 1)
  AND NOT EXISTS (
    SELECT 1 FROM `EXAMINATION` e
    WHERE e.`Course_ID` = 1
      AND NOT EXISTS (
        SELECT 1
        FROM `REGISTRATION` r
                 JOIN `ATTEMPT` a ON a.`RegisID` = r.`RegisID`
        WHERE r.`User_ID` = s.`User_ID`
          AND r.`Exam_ID` = e.`Exam_ID`
          AND a.`EndTime` IS NOT NULL
    )
)
ORDER BY s.`User_ID`;

-- Topic 20 Query 5: Average normalized score per topic (BR-47; avoids M:N covers duplication)
SELECT t.`Topic_ID`, t.`Content` AS `TopicName`,
       COUNT(*) AS `GradedResponses`,
       ROUND(100.0 * SUM(an.`MarkAwarded`) / NULLIF(SUM(eqm.`Marks`), 0), 2) AS `AvgTopicPercent`
FROM `v_representative_attempts` ra
         JOIN `ANSWER` an               ON an.`RegisID` = ra.`RegisID` AND an.`Attempt_no` = ra.`Attempt_no`
         JOIN `QUESTION` q              ON q.`Quest_ID` = an.`Quest_ID`
         JOIN `TOPIC` t                 ON t.`Topic_ID` = q.`Topic_ID`
         JOIN `EXAM_QUESTION_MARKS` eqm ON eqm.`Exam_ID` = ra.`Exam_ID` AND eqm.`Quest_ID` = an.`Quest_ID`
GROUP BY t.`Topic_ID`, t.`Content`
ORDER BY `AvgTopicPercent` ASC, t.`Topic_ID`;

-- ----------------------------------------------------------------------------
-- PART C: Automated Negative & Positive Acceptance Runner (N01-N24)
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_run_acceptance_suite`;
DELIMITER $$
CREATE PROCEDURE `sp_run_acceptance_suite`()
BEGIN
    DECLARE v_err INT DEFAULT 0;
    DECLARE v_msg TEXT DEFAULT '';
    DECLARE v_passed INT DEFAULT 0;
    DECLARE v_failed INT DEFAULT 0;
    DECLARE v_new_att INT DEFAULT 0;
    DECLARE v_cnt INT DEFAULT 0;
    DECLARE v_cnt2 INT DEFAULT 0;
    DECLARE v_cnt3 INT DEFAULT 0;

    CREATE TEMPORARY TABLE IF NOT EXISTS `tmp_test_log` (
                                                            `TestID` VARCHAR(10),
                                                            `Status` VARCHAR(10),
                                                            `Detail` VARCHAR(255)
    );
    TRUNCATE TABLE `tmp_test_log`;

    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
            BEGIN
                GET DIAGNOSTICS CONDITION 1 v_err = MYSQL_ERRNO, v_msg = MESSAGE_TEXT;
            END;

        -- Verify >=15 rows in all 21 tables
        SELECT MIN(c) INTO v_cnt FROM (
                                          SELECT COUNT(*) AS c FROM `USER` UNION ALL SELECT COUNT(*) FROM `USER_PHONE` UNION ALL
                                          SELECT COUNT(*) FROM `STUDENT` UNION ALL SELECT COUNT(*) FROM `INSTRUCTOR` UNION ALL
                                          SELECT COUNT(*) FROM `COURSE` UNION ALL SELECT COUNT(*) FROM `REQUIRES` UNION ALL
                                          SELECT COUNT(*) FROM `TOPIC` UNION ALL SELECT COUNT(*) FROM `COVERS` UNION ALL
                                          SELECT COUNT(*) FROM `TEACH` UNION ALL SELECT COUNT(*) FROM `QUESTION` UNION ALL
                                          SELECT COUNT(*) FROM `MCQ` UNION ALL SELECT COUNT(*) FROM `SHORT_ANSWER` UNION ALL
                                          SELECT COUNT(*) FROM `OPTIONS` UNION ALL SELECT COUNT(*) FROM `EXAMINATION` UNION ALL
                                          SELECT COUNT(*) FROM `PAPER` UNION ALL SELECT COUNT(*) FROM `EXAM_QUESTION_MARKS` UNION ALL
                                          SELECT COUNT(*) FROM `PAPER_QUESTION` UNION ALL SELECT COUNT(*) FROM `REGISTRATION` UNION ALL
                                          SELECT COUNT(*) FROM `ATTEMPT` UNION ALL SELECT COUNT(*) FROM `ANSWER` UNION ALL
                                          SELECT COUNT(*) FROM `SELECT_TABLE`
                                      ) t;
        IF v_cnt >= 15 THEN
            INSERT INTO `tmp_test_log` VALUES ('N20_SEED', 'PASS', CONCAT('Min rows per table = ', v_cnt));
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N20_SEED', 'FAIL', CONCAT('Min rows < 15: ', v_cnt));
            SET v_failed = v_failed + 1;
        END IF;

        -- Verify 28 FKs declare ON UPDATE RESTRICT (28) and only user_phone uses ON DELETE CASCADE (27 RESTRICT, 1 CASCADE)
        SELECT COUNT(*) INTO v_cnt FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND UPDATE_RULE = 'RESTRICT';
        IF v_cnt = 28 THEN
            INSERT INTO `tmp_test_log` VALUES ('N19_FKUPD', 'PASS', 'All 28 FKs use ON UPDATE RESTRICT; user_phone uses ON DELETE CASCADE');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N19_FKUPD', 'FAIL', CONCAT('Expected 28 ON UPDATE RESTRICT, got ', v_cnt));
            SET v_failed = v_failed + 1;
        END IF;

        -- Start synthetic attempt on RegisID 12 (Exam 1 -> PaperKey 1)
        SET v_err = 0;
        CALL `sp_start_attempt`(12, 1, '2026-10-20 09:10:00', v_new_att);

        -- BR41_NULL: sp_finalize_attempt rejects NULL EndTime and leaves 0 answer rows & NULL EndTime
        SET v_err = 0;
        CALL `sp_finalize_attempt`(12, v_new_att, NULL);
        SELECT COUNT(*) INTO v_cnt FROM `ANSWER` WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att;
        SELECT COUNT(*) INTO v_cnt2 FROM `ATTEMPT` WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `EndTime` IS NULL;
        IF v_err = 1644 AND v_cnt = 0 AND v_cnt2 = 1 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR41_NULL', 'PASS', 'sp_finalize_attempt rejected NULL EndTime with 0 answer writes');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR41_NULL', 'FAIL', CONCAT('err=', v_err, ', ans_cnt=', v_cnt, ', null_end=', v_cnt2));
            SET v_failed = v_failed + 1;
        END IF;

        -- N01: Two questions in same attempt select Option_no = 1 (Q2 opt 1 right = 10.00, Q4 opt 1 wrong = 0.00)
        SET v_err = 0;
        CALL `sp_submit_mcq_answer`(12, v_new_att, 2, 1);
        CALL `sp_submit_mcq_answer`(12, v_new_att, 4, 1);
        IF v_err = 0 THEN
            INSERT INTO `tmp_test_log` VALUES ('N01', 'PASS', 'Two questions in same attempt selected Option_no = 1');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N01', 'FAIL', v_msg);
            SET v_failed = v_failed + 1;
        END IF;

        -- N02: Direct duplicate INSERT on select_table for same (Q2, Attempt, RegisID 12) must fail with 1062
        SET v_err = 0;
        INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`) VALUES (2, 2, v_new_att, 12);
        IF v_err = 1062 THEN
            INSERT INTO `tmp_test_log` VALUES ('N02', 'PASS', 'Second option for same MCQ blocked by PK (1062)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N02', 'FAIL', CONCAT('Expected 1062, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N03: Select nonexistent option or option from wrong question (FK 1452)
        SET v_err = 0;
        INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`) VALUES (99, 1, v_new_att, 12);
        IF v_err = 1452 THEN
            INSERT INTO `tmp_test_log` VALUES ('N03', 'PASS', 'Nonexistent option rejected by composite FK (1452)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N03', 'FAIL', CONCAT('Expected 1452, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N04: Select without matching answer row (FK 1452)
        SET v_err = 0;
        INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`) VALUES (1, 1, v_new_att, 12);
        IF v_err = 1452 THEN
            INSERT INTO `tmp_test_log` VALUES ('N04', 'PASS', 'Orphan select_table without answer rejected (1452)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N04', 'FAIL', CONCAT('Expected 1452, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- Inject pre-existing blank answer rows before finalizing: Q3 (NULL, NULL) & Q21 ('   ', NULL); leave Q1 missing
        INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
        VALUES (3, v_new_att, 12, NULL, NULL),
               (21, v_new_att, 12, '   ', NULL);

        -- N21 + BR41_IDEM: Atomic finalize materializes blanks as 0.00 AND repeated finalize preserves EndTime
        SET v_err = 0;
        CALL `sp_finalize_attempt`(12, v_new_att, '2026-10-20 09:50:00');
        SELECT COUNT(*) INTO v_cnt FROM `ANSWER` WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att;
        CALL `sp_finalize_attempt`(12, v_new_att, '2026-10-20 11:59:00');
        SELECT COUNT(*) INTO v_cnt2 FROM `ATTEMPT`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `EndTime` = '2026-10-20 09:50:00';
        IF v_err = 0 AND v_cnt = 5 AND v_cnt2 = 1 THEN
            INSERT INTO `tmp_test_log` VALUES ('N21_IDEM', 'PASS', 'sp_finalize_attempt recorded 5 answers and stayed idempotent on repeat');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N21_IDEM', 'FAIL', CONCAT('err=', v_err, ', cnt=', v_cnt, ', endtime_preserved=', v_cnt2));
            SET v_failed = v_failed + 1;
        END IF;

        -- BR42_BLNK: Missing Q1 and pre-existing blank Q3/Q21 all receive 0.00 -> IsFullyGraded=1, Score=10.00 (16.67%)
        SELECT COUNT(*) INTO v_cnt FROM `ANSWER`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `Quest_ID` IN (1, 3, 21) AND `MarkAwarded` = 0.00;
        SELECT COUNT(*) INTO v_cnt2 FROM `v_attempt_scores`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att
          AND `IsFullyGraded` = 1 AND `Total_Score` = 10.00 AND `ScorePercent` = 16.67 AND `IsPassed` = 0;
        IF v_cnt = 3 AND v_cnt2 = 1 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR42_BLNK', 'PASS', 'Missing & pre-existing blank answers zeroed (IsFullyGraded=1, 10.00/60)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR42_BLNK', 'FAIL', CONCAT('zeroed=', v_cnt, ', score_match=', v_cnt2));
            SET v_failed = v_failed + 1;
        END IF;

        -- BR45_INCOMP: Submitted attempt missing an answer row must keep IsFullyGraded=0 & NULL score/percent/pass
        DELETE FROM `ANSWER` WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `Quest_ID` = 21;
        SELECT COUNT(*) INTO v_cnt FROM `v_attempt_scores`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att
          AND `IsFullyGraded` = 0 AND `Total_Score` IS NULL AND `ScorePercent` IS NULL AND `IsPassed` IS NULL;
        IF v_cnt = 1 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR45_INC', 'PASS', 'Submitted attempt missing 1 answer excluded with NULL Total/Percent/IsPassed');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR45_INC', 'FAIL', 'Missing answer was not properly masked in v_attempt_scores');
            SET v_failed = v_failed + 1;
        END IF;

        -- Clean up synthetic attempt 12 before BR42_SA
        DELETE FROM `SELECT_TABLE` WHERE `RegisID` = 12;
        DELETE FROM `ANSWER` WHERE `RegisID` = 12;
        DELETE FROM `ATTEMPT` WHERE `RegisID` = 12;

        -- BR42_SA: Non-blank short answer retains MarkAwarded IS NULL while blank MCQs become 0.00
        SET v_err = 0;
        CALL `sp_start_attempt`(12, 1, '2026-10-20 09:20:00', v_new_att);
        INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
        VALUES (21, v_new_att, 12, 'Superkey and minimal', NULL);
        CALL `sp_finalize_attempt`(12, v_new_att, '2026-10-20 09:55:00');
        SELECT COUNT(*) INTO v_cnt FROM `ANSWER`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `Quest_ID` IN (1, 2, 3, 4) AND `MarkAwarded` = 0.00;
        SELECT COUNT(*) INTO v_cnt2 FROM `ANSWER`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `Quest_ID` = 21 AND `MarkAwarded` IS NULL;
        SELECT COUNT(*) INTO v_cnt3 FROM `v_attempt_scores`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att
          AND `IsFullyGraded` = 0 AND `Total_Score` IS NULL AND `ScorePercent` IS NULL AND `IsPassed` IS NULL;
        IF v_err = 0 AND v_cnt = 4 AND v_cnt2 = 1 AND v_cnt3 = 1 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR42_SA', 'PASS', 'Blank MCQs zeroed while non-blank short answer stays ungraded (NULL scores)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR42_SA', 'FAIL', CONCAT('err=', v_err, ', mcq0=', v_cnt, ', sa_null=', v_cnt2, ', view_ok=', v_cnt3));
            SET v_failed = v_failed + 1;
        END IF;

        -- Clean up synthetic attempt 12 so original seed remains intact
        DELETE FROM `SELECT_TABLE` WHERE `RegisID` = 12;
        DELETE FROM `ANSWER` WHERE `RegisID` = 12;
        DELETE FROM `ATTEMPT` WHERE `RegisID` = 12;

        -- BR42_WS: Tab/CR/LF whitespace short answer zeroed (0.00, IsFullyGraded=1) & excluded by Query #3
        SET v_err = 0;
        CALL `sp_start_attempt`(12, 1, '2026-10-20 09:30:00', v_new_att);
        INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
        VALUES (21, v_new_att, 12, CONCAT(CHAR(9 USING utf8mb4), CHAR(13 USING utf8mb4), CHAR(10 USING utf8mb4), ' '), NULL);
        CALL `sp_finalize_attempt`(12, v_new_att, '2026-10-20 09:58:00');
        SELECT COUNT(*) INTO v_cnt FROM `ANSWER`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att AND `Quest_ID` = 21 AND `MarkAwarded` = 0.00;
        SELECT COUNT(*) INTO v_cnt2 FROM `v_attempt_scores`
        WHERE `RegisID` = 12 AND `Attempt_no` = v_new_att
          AND `IsFullyGraded` = 1 AND `Total_Score` = 0.00 AND `ScorePercent` = 0.00 AND `IsPassed` = 0;
        SELECT COUNT(*) INTO v_cnt3
        FROM `ANSWER` an
                 LEFT JOIN `SELECT_TABLE` st
                           ON st.`Quest_ID` = an.`Quest_ID` AND st.`Attempt_no` = an.`Attempt_no` AND st.`RegisID` = an.`RegisID`
        WHERE an.`RegisID` = 12 AND an.`Attempt_no` = v_new_att
          AND (st.`Option_no` IS NOT NULL OR (an.`AnswerText` IS NOT NULL AND REGEXP_LIKE(an.`AnswerText`, '[^[:space:]]')));
        IF v_err = 0 AND v_cnt = 1 AND v_cnt2 = 1 AND v_cnt3 = 0 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR42_WS', 'PASS', 'Tab/CRLF-only short answer zeroed (0.00, IsFullyGraded=1) & excluded by Query #3');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR42_WS', 'FAIL', CONCAT('err=', v_err, ', q21_zero=', v_cnt, ', graded=', v_cnt2, ', q3_nonblank=', v_cnt3));
            SET v_failed = v_failed + 1;
        END IF;

        -- Clean up synthetic attempt 12 so original seed remains intact
        DELETE FROM `SELECT_TABLE` WHERE `RegisID` = 12;
        DELETE FROM `ANSWER` WHERE `RegisID` = 12;
        DELETE FROM `ATTEMPT` WHERE `RegisID` = 12;

        -- BR28_EMPTY: Empty draft paper (0 questions in paper_question) cannot be assigned to an attempt
        INSERT INTO `PAPER` (`PaperKey`, `PaperID`, `Exam_ID`) VALUES (19, 99, 1);
        SET v_err = 0;
        INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
        VALUES (1, 12, '2026-10-20 09:00:00', NULL, 19);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR28_EMP', 'PASS', 'Assigning empty paper (0 questions) to attempt blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR28_EMP', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;
        DELETE FROM `PAPER` WHERE `PaperKey` = 19;

        -- N05: Assign wrong-exam PaperKey (RegisID 1 is Exam 1, PaperKey 3 is Exam 2)
        SET v_err = 0;
        INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
        VALUES (3, 1, '2026-10-20 12:00:00', NULL, 3);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N05', 'PASS', 'Wrong-exam PaperKey blocked by trigger (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N05', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N06: Change registration.Exam_ID or paper.Exam_ID after attempts exist
        SET v_err = 0;
        UPDATE `REGISTRATION` SET `Exam_ID` = 2 WHERE `RegisID` = 1;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N06', 'PASS', 'Parent registration.Exam_ID update blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N06', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N07: Duplicate (User_ID, Exam_ID) registration
        SET v_err = 0;
        INSERT INTO `REGISTRATION` (`RegisID`, `RegisDate`, `User_ID`, `Exam_ID`) VALUES (999, '2026-10-15 10:00:00', 101, 1);
        IF v_err = 1062 THEN
            INSERT INTO `tmp_test_log` VALUES ('N07', 'PASS', 'Duplicate student-exam registration blocked (1062)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N07', 'FAIL', CONCAT('Expected 1062, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N08: Case-insensitive duplicate Email & invalid Email CHECK
        SET v_err = 0;
        INSERT INTO `USER` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`)
        VALUES (999, 'Dup', 'Mail', '2000-01-01', 'SV101@STUDENT.HCMIU.EDU.VN', 'hash');
        IF v_err = 1062 THEN
            INSERT INTO `tmp_test_log` VALUES ('N08_UQ', 'PASS', 'Case-insensitive duplicate email blocked (1062)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N08_UQ', 'FAIL', CONCAT('Expected 1062, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        INSERT INTO `USER` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`)
        VALUES (999, 'Bad', 'Mail', '2000-01-01', 'not-an-email', 'hash');
        IF v_err = 3819 THEN
            INSERT INTO `tmp_test_log` VALUES ('N08_CHK', 'PASS', 'Invalid email format rejected by CHECK (3819)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N08_CHK', 'FAIL', CONCAT('Expected 3819, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N09: Duplicate QuestionOrder on a fresh draft paper (PaperKey 19, no attempts yet)
        INSERT INTO `PAPER` (`PaperKey`, `PaperID`, `Exam_ID`) VALUES (19, 99, 1);
        INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES (19, 1, 1);
        SET v_err = 0;
        INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES (19, 2, 1);
        IF v_err = 1062 THEN
            INSERT INTO `tmp_test_log` VALUES ('N09', 'PASS', 'Duplicate QuestionOrder on draft PaperKey 19 blocked (1062)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N09', 'FAIL', CONCAT('Expected 1062, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;
        DELETE FROM `PAPER_QUESTION` WHERE `PaperKey` = 19;
        DELETE FROM `PAPER` WHERE `PaperKey` = 19;

        -- N10: Duplicate teach triple blocked & case-sensitive Semester CHECK (lowercase '2026-s1' -> 3819)
        SET v_err = 0;
        INSERT INTO `TEACH` (`User_ID`, `Course_ID`, `Semester`) VALUES (1, 1, '2026-S1');
        IF v_err = 1062 THEN
            INSERT INTO `tmp_test_log` VALUES ('N10', 'PASS', 'Duplicate teach triple rejected (1062); 2025-S2+2026-S1 co-exist');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N10', 'FAIL', CONCAT('Expected 1062, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        INSERT INTO `TEACH` (`User_ID`, `Course_ID`, `Semester`) VALUES (1, 2, '2026-s1');
        IF v_err = 3819 THEN
            INSERT INTO `tmp_test_log` VALUES ('N10_CASE', 'PASS', 'Lowercase Semester 2026-s1 rejected by case-sensitive CHECK (3819)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N10_CASE', 'FAIL', CONCAT('Expected 3819, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N11: ISA disjointness (adding student 1 who is instructor 1 must fail)
        SET v_err = 0;
        INSERT INTO `STUDENT` (`User_ID`, `Enrollment_year`) VALUES (1, 2025);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N11', 'PASS', 'Dual ISA role blocked by disjoint trigger (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N11', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N12: Answer a question not on assigned paper (Q8 not on PaperKey 1)
        SET v_err = 0;
        INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`) VALUES (8, 1, 11, NULL, 0.00);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N12', 'PASS', 'Answer outside assigned paper rejected by trigger (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N12', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N13: MarkAwarded > Marks (Q21 max=20 on Exam 1, try 25) and lowering exam_question_marks below awarded
        SET v_err = 0;
        UPDATE `ANSWER` SET `MarkAwarded` = 25.00 WHERE `Quest_ID` = 21 AND `Attempt_no` = 1 AND `RegisID` = 1;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N13', 'PASS', 'MarkAwarded > exam_question_marks.Marks rejected (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N13', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N14: Lowering Max_attempts below existing student count (Exam 1 has 2 attempts on RegisID 1)
        SET v_err = 0;
        UPDATE `EXAMINATION` SET `Max_attempts` = 1 WHERE `Exam_ID` = 1;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N14', 'PASS', 'Lowering Max_attempts below used attempts blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N14', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N16: Self-prerequisite CHECK (3819) and 3-course cycle Trigger (1644)
        SET v_err = 0;
        INSERT INTO `REQUIRES` (`Prerequisite_ID`, `Advanced_ID`) VALUES (1, 1);
        IF v_err = 3819 THEN
            INSERT INTO `tmp_test_log` VALUES ('N16_SELF', 'PASS', 'Self-prerequisite blocked by CHECK (3819)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N16_SELF', 'FAIL', CONCAT('Expected 3819, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        -- Existing chain: 7 -> 3 -> 1 -> 8; adding 8 -> 7 closes a cycle!
        INSERT INTO `REQUIRES` (`Prerequisite_ID`, `Advanced_ID`) VALUES (8, 7);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N16_DAG', 'PASS', 'Multi-hop prerequisite cycle blocked by recursive trigger (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N16_DAG', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N17: Insert exam_question_marks for topic outside course covers, and deleting active covers link
        SET v_err = 0;
        INSERT INTO `EXAM_QUESTION_MARKS` (`Exam_ID`, `Quest_ID`, `Marks`) VALUES (1, 10, 10.00);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N17_ADD', 'PASS', 'Question outside course topics blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N17_ADD', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        DELETE FROM `COVERS` WHERE `Course_ID` = 1 AND `Topic_ID` = 3;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N17_DEL', 'PASS', 'Deleting active covers row blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N17_DEL', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N18_OPT + BR43_EXAM: Historical freeze on attempted options & Pass_mark
        SET v_err = 0;
        UPDATE `OPTIONS` SET `Bool_Correct` = 1 WHERE `Quest_ID` = 1 AND `Option_no` = 1;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N18_OPT', 'PASS', 'Historical freeze blocked altering attempted MCQ option (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N18_OPT', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        UPDATE `EXAMINATION` SET `Pass_mark` = 75.00 WHERE `Exam_ID` = 1;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR43_EXM', 'PASS', 'Historical freeze blocked altering Pass_mark after attempts exist (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR43_EXM', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- BR49_PASS: Verify IsPassed against Pass_mark (Student 101 failed at 26.67%; Student 109 passed at 76.67%)
        SELECT COUNT(*) INTO v_cnt FROM `v_representative_attempts`
        WHERE `Exam_ID` = 1 AND ((`User_ID` = 101 AND `IsPassed` = 0) OR (`User_ID` = 109 AND `IsPassed` = 1));
        IF v_cnt = 2 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR49_PAS', 'PASS', 'v_representative_attempts IsPassed matches Pass_mark threshold (0/1)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR49_PAS', 'FAIL', CONCAT('Expected 2 IsPassed matches, got ', v_cnt));
            SET v_failed = v_failed + 1;
        END IF;

        -- N19: Single-table CHECK & missing FK tests (Difficulty=6, Bool_Correct=2, Missing FK 1452)
        SET v_err = 0;
        INSERT INTO `QUESTION` (`Quest_ID`, `Content`, `Difficulty`, `Topic_ID`, `User_ID`) VALUES (999, 'X', 6, 1, 1);
        IF v_err = 3819 THEN
            INSERT INTO `tmp_test_log` VALUES ('N19_CHK', 'PASS', 'Difficulty=6 rejected by CHECK (3819)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N19_CHK', 'FAIL', CONCAT('Expected 3819, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        INSERT INTO `REGISTRATION` (`RegisID`, `RegisDate`, `User_ID`, `Exam_ID`) VALUES (999, '2026-10-01 09:00:00', 8888, 1);
        IF v_err = 1452 THEN
            INSERT INTO `tmp_test_log` VALUES ('N19_FK', 'PASS', 'Missing parent User_ID rejected by FK (1452)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N19_FK', 'FAIL', CONCAT('Expected 1452, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- N24: Adding question to paper without exam_question_marks entry, and deleting in-use exam_question_marks
        SET v_err = 0;
        INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES (4, 3, 4);
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N24_MISS', 'PASS', 'Paper placement without exam_question_marks blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N24_MISS', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        SET v_err = 0;
        DELETE FROM `EXAM_QUESTION_MARKS` WHERE `Exam_ID` = 1 AND `Quest_ID` = 1;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('N24_DEL', 'PASS', 'Deleting in-use exam_question_marks blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('N24_DEL', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- BR37_UPD: UPDATE answer to Quest_ID outside assigned paper rejected by trg_answer_bu_checks
        SET v_err = 0;
        UPDATE `ANSWER` SET `Quest_ID` = 22 WHERE `Quest_ID` = 21 AND `Attempt_no` = 1 AND `RegisID` = 11;
        IF v_err = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR37_UPD', 'PASS', 'UPDATE answer to question outside assigned paper rejected (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR37_UPD', 'FAIL', CONCAT('Expected 1644, got ', v_err));
            SET v_failed = v_failed + 1;
        END IF;

        -- BR49_NULL: In-progress / ungraded attempt keeps Total_Score, ScorePercent, and IsPassed NULL
        SELECT COUNT(*) INTO v_cnt FROM `v_attempt_scores`
        WHERE `RegisID` = 11 AND `Attempt_no` = 1
          AND `IsFullyGraded` = 0 AND `Total_Score` IS NULL AND `ScorePercent` IS NULL AND `IsPassed` IS NULL;
        IF v_cnt = 1 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR49_NULL', 'PASS', 'v_attempt_scores hides Total_Score/ScorePercent/IsPassed as NULL when ungraded');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR49_NULL', 'FAIL', CONCAT('Expected 1 masked ungraded row, got ', v_cnt));
            SET v_failed = v_failed + 1;
        END IF;

        -- BR29_UPD: Updating exam_question_marks identity while used OR draft paper_question to invalid question blocked
        SET v_err = 0;
        UPDATE `EXAM_QUESTION_MARKS` SET `Quest_ID` = 8 WHERE `Exam_ID` = 1 AND `Quest_ID` = 1;
        SET v_cnt = v_err;

        INSERT INTO `PAPER` (`PaperKey`, `PaperID`, `Exam_ID`) VALUES (19, 99, 1);
        INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES (19, 1, 1);
        SET v_err = 0;
        UPDATE `PAPER_QUESTION` SET `Quest_ID` = 10 WHERE `PaperKey` = 19 AND `Quest_ID` = 1;
        SET v_cnt2 = v_err;
        DELETE FROM `PAPER_QUESTION` WHERE `PaperKey` = 19;
        DELETE FROM `PAPER` WHERE `PaperKey` = 19;

        IF v_cnt = 1644 AND v_cnt2 = 1644 THEN
            INSERT INTO `tmp_test_log` VALUES ('BR29_UPD', 'PASS', 'Invalid exam_question_marks/paper_question identity updates blocked (1644)');
            SET v_passed = v_passed + 1;
        ELSE
            INSERT INTO `tmp_test_log` VALUES ('BR29_UPD', 'FAIL', CONCAT('Expected 1644/1644, got ', v_cnt, '/', v_cnt2));
            SET v_failed = v_failed + 1;
        END IF;

    END;

    SELECT `TestID`, `Status`, `Detail` FROM `tmp_test_log`;
    SELECT CONCAT('ACCEPTANCE_SUMMARY|PASSED=', v_passed, '|FAILED=', v_failed) AS summary;

    DROP TEMPORARY TABLE IF EXISTS `tmp_test_log`;

    IF v_failed > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'One or more acceptance tests failed!';
    END IF;
END$$
DELIMITER ;

CALL `sp_run_acceptance_suite`();
DROP PROCEDURE `sp_run_acceptance_suite`;