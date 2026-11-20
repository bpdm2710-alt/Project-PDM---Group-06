-- ============================================================================
-- SAFE NON-DESTRUCTIVE MIGRATION SCRIPT (Baseline 20 Tables -> Target 21 Tables)
-- Run ONLY on an isolated copy first! Replace `online_examination_old` with
-- the actual baseline schema name before executing.
-- Aborts (ROLLBACK + SIGNAL 45000) before committing ANY rows if pre-audit or
-- post-migration lossless reconciliation detects any violation.
-- ============================================================================
USE `Online_Examination`;
DROP PROCEDURE IF EXISTS `sp_migrate_old_to_new`;

DELIMITER $$
CREATE PROCEDURE `sp_migrate_old_to_new`()
BEGIN
  DECLARE v_target_rows    INT DEFAULT 0;
  DECLARE v_dup_email      INT DEFAULT 0;
  DECLARE v_dup_regis      INT DEFAULT 0;
  DECLARE v_wrong_exam     INT DEFAULT 0;
  DECLARE v_conflict_marks INT DEFAULT 0;
  DECLARE v_orphan_sel     INT DEFAULT 0;
  DECLARE v_multi_sel      INT DEFAULT 0;
  DECLARE v_dup_order      INT DEFAULT 0;

  DECLARE v_miss_att       INT DEFAULT 0;
  DECLARE v_spur_att       INT DEFAULT 0;
  DECLARE v_miss_cnt       INT DEFAULT 0;
  DECLARE v_spur_cnt       INT DEFAULT 0;
  DECLARE v_err_msg        VARCHAR(128) DEFAULT '';

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

    -- ------------------------------------------------------------------------
    -- STEP 1: PRE-MIGRATION AUDIT & ABORT GATE ON `online_examination_old`
    -- ------------------------------------------------------------------------
    SELECT
      (SELECT COUNT(*) FROM `user`) +
      (SELECT COUNT(*) FROM `course`) +
      (SELECT COUNT(*) FROM `topic`) +
      (SELECT COUNT(*) FROM `examination`) +
      (SELECT COUNT(*) FROM `question`) +
      (SELECT COUNT(*) FROM `paper`)
    INTO v_target_rows;

    SELECT COUNT(*) INTO v_dup_email
    FROM (
      SELECT LOWER(TRIM(`Email`)) AS norm_email
      FROM `online_examination_old`.`user`
      GROUP BY LOWER(TRIM(`Email`))
      HAVING COUNT(*) > 1
    ) x;

    SELECT COUNT(*) INTO v_dup_regis
    FROM (
      SELECT `User_ID`, `Exam_ID`
      FROM `online_examination_old`.`registration`
      GROUP BY `User_ID`, `Exam_ID`
      HAVING COUNT(*) > 1
    ) x;

    SELECT COUNT(*) INTO v_wrong_exam
    FROM `online_examination_old`.`attempt` a
    JOIN `online_examination_old`.`registration` r ON r.`RegisID` = a.`RegisID`
    WHERE a.`Exam_ID` <> r.`Exam_ID`;

    SELECT COUNT(*) INTO v_conflict_marks
    FROM (
      SELECT `Exam_ID`, `Quest_ID`
      FROM `online_examination_old`.`contains`
      GROUP BY `Exam_ID`, `Quest_ID`
      HAVING COUNT(DISTINCT `Marks`) > 1
          OR SUM(CASE WHEN `Marks` IS NULL OR `Marks` <= 0 THEN 1 ELSE 0 END) > 0
    ) x;

    SELECT COUNT(*) INTO v_orphan_sel
    FROM `online_examination_old`.`select_table` s
    LEFT JOIN `online_examination_old`.`answer` a
      ON a.`Quest_ID` = s.`Quest_ID` AND a.`Attempt_no` = s.`Attempt_no` AND a.`RegisID` = s.`RegisID`
    WHERE a.`Quest_ID` IS NULL;

    SELECT COUNT(*) INTO v_multi_sel
    FROM (
      SELECT `Quest_ID`, `Attempt_no`, `RegisID`
      FROM `online_examination_old`.`select_table`
      GROUP BY `Quest_ID`, `Attempt_no`, `RegisID`
      HAVING COUNT(*) > 1
    ) x;

    SELECT COUNT(*) INTO v_dup_order
    FROM (
      SELECT `Exam_ID`, `PaperID`, `QuestionOrder`
      FROM `online_examination_old`.`contains`
      GROUP BY `Exam_ID`, `PaperID`, `QuestionOrder`
      HAVING COUNT(*) > 1
    ) x;

    SELECT 'audit_target_not_empty' AS check_name, v_target_rows AS violations
    UNION ALL SELECT 'audit_duplicate_email', v_dup_email
    UNION ALL SELECT 'audit_duplicate_regis', v_dup_regis
    UNION ALL SELECT 'audit_wrong_exam_attempts', v_wrong_exam
    UNION ALL SELECT 'audit_conflicting_exam_marks_across_papers', v_conflict_marks
    UNION ALL SELECT 'audit_selection_without_answer', v_orphan_sel
    UNION ALL SELECT 'audit_multi_select_mcq', v_multi_sel
    UNION ALL SELECT 'audit_duplicate_paper_question_order', v_dup_order;

    IF (v_target_rows + v_dup_email + v_dup_regis + v_wrong_exam + v_conflict_marks + v_orphan_sel + v_multi_sel + v_dup_order) > 0 THEN
      SET v_err_msg = LEFT(CONCAT(
        'Pre-audit failed: marks=', v_conflict_marks,
        ', target=', v_target_rows,
        ', email=', v_dup_email,
        ', regis=', v_dup_regis,
        ', exam=', v_wrong_exam,
        ', orphan=', v_orphan_sel,
        ', multi=', v_multi_sel,
        ', order=', v_dup_order
      ), 128);
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_err_msg;
    END IF;

    -- ------------------------------------------------------------------------
    -- STEP 2: MIGRATE ALL 21 TABLES INSIDE SINGLE TRANSACTION
    -- ------------------------------------------------------------------------
    INSERT INTO `user` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`)
    SELECT `User_ID`, `FirstName`, `LastName`, `DOB`, LOWER(TRIM(`Email`)), `Password_hash_`
    FROM `online_examination_old`.`user`;

    INSERT INTO `user_phone` (`Phone`, `User_ID`)
    SELECT `Phone`, `User_ID` FROM `online_examination_old`.`user_phone`;

    INSERT INTO `student` (`User_ID`, `Enrollment_year`)
    SELECT `User_ID`, `Enrollment_year` FROM `online_examination_old`.`student`;

    INSERT INTO `instructor` (`User_ID`, `Department`)
    SELECT `User_ID`, `Department` FROM `online_examination_old`.`instructor`;

    INSERT INTO `course` (`Course_ID`, `CName`, `Credits`)
    SELECT `Course_ID`, `CName`, `Credits` FROM `online_examination_old`.`course`;

    INSERT INTO `requires` (`Prerequisite_ID`, `Advanced_ID`)
    SELECT `Course_ID_1`, `Course_ID_2` FROM `online_examination_old`.`requires`;

    INSERT INTO `topic` (`Topic_ID`, `Content`)
    SELECT `Topic_ID`, `Content` FROM `online_examination_old`.`topic`;

    INSERT INTO `covers` (`Course_ID`, `Topic_ID`)
    SELECT `Course_ID`, `Topic_ID` FROM `online_examination_old`.`covers`;

    INSERT INTO `teach` (`User_ID`, `Course_ID`, `Semester`)
    SELECT `User_ID`, `Course_ID`, `Semester` FROM `online_examination_old`.`teach`;

    INSERT INTO `question` (`Quest_ID`, `Content`, `Difficulty`, `Topic_ID`, `User_ID`)
    SELECT `Quest_ID`, `Content`, `Difficulty`, `Topic_ID`, `User_ID` FROM `online_examination_old`.`question`;

    INSERT INTO `mcq` (`Quest_ID`)
    SELECT `Quest_ID` FROM `online_examination_old`.`mcq`;

    INSERT INTO `short_answer` (`Quest_ID`, `Answer_key`)
    SELECT `Quest_ID`, `Answer_key` FROM `online_examination_old`.`short_answer`;

    INSERT INTO `options` (`Option_no`, `Quest_ID`, `Op_Content`, `Bool_Correct`)
    SELECT `Option_no`, `Quest_ID`, `Op_Content`, `Bool_Correct` FROM `online_examination_old`.`options`;

    INSERT INTO `examination` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`)
    SELECT `Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`
    FROM `online_examination_old`.`examination`;

    -- Assign deterministic PaperKey bijection for every (Exam_ID, PaperID)
    INSERT INTO `paper` (`PaperID`, `Exam_ID`)
    SELECT `PaperID`, `Exam_ID`
    FROM `online_examination_old`.`paper`
    ORDER BY `Exam_ID`, `PaperID`;

    -- Split old `contains` into `exam_question_marks` + `paper_question` (pre-audit guarantees 0 marks conflicts)
    INSERT INTO `exam_question_marks` (`Exam_ID`, `Quest_ID`, `Marks`)
    SELECT `Exam_ID`, `Quest_ID`, MIN(`Marks`)
    FROM `online_examination_old`.`contains`
    GROUP BY `Exam_ID`, `Quest_ID`;

    INSERT INTO `paper_question` (`PaperKey`, `Quest_ID`, `QuestionOrder`)
    SELECT p.`PaperKey`, c.`Quest_ID`, c.`QuestionOrder`
    FROM `online_examination_old`.`contains` c
    JOIN `paper` p ON p.`Exam_ID` = c.`Exam_ID` AND p.`PaperID` = c.`PaperID`;

    INSERT INTO `registration` (`RegisID`, `RegisDate`, `User_ID`, `Exam_ID`)
    SELECT `RegisID`, `RegisDate`, `User_ID`, `Exam_ID` FROM `online_examination_old`.`registration`;

    -- Resolve each attempt's PaperKey via both PaperID and validated registration.Exam_ID
    INSERT INTO `attempt` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
    SELECT a.`Attempt_no`, a.`RegisID`, a.`StartTime`, a.`EndTime`, p.`PaperKey`
    FROM `online_examination_old`.`attempt` a
    JOIN `registration` r ON r.`RegisID` = a.`RegisID`
    JOIN `paper` p        ON p.`Exam_ID` = r.`Exam_ID` AND p.`PaperID` = a.`PaperID`;

    INSERT INTO `answer` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
    SELECT a.`Quest_ID`, a.`Attempt_no`, a.`RegisID`,
           CASE
             WHEN m.`Quest_ID` IS NOT NULL THEN NULL
             WHEN a.`AnswerText` IS NULL OR NOT REGEXP_LIKE(a.`AnswerText`, '[^[:space:]]') THEN NULL
             ELSE a.`AnswerText`
           END,
           a.`MarkAwarded`
    FROM `online_examination_old`.`answer` a
    LEFT JOIN `mcq` m ON m.`Quest_ID` = a.`Quest_ID`;

    INSERT INTO `select_table` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`)
    SELECT `Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`
    FROM `online_examination_old`.`select_table`;

    -- ------------------------------------------------------------------------
    -- STEP 3: PRE-COMMIT LOSSLESS RECONCILIATION GATE (0 missing, 0 spurious)
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_miss_att
    FROM `online_examination_old`.`attempt` old_a
    LEFT JOIN (
      SELECT a.`Attempt_no`, a.`RegisID`, a.`StartTime`, a.`EndTime`, p.`PaperID`, r.`Exam_ID`
      FROM `Online_Examination`.`attempt` a
      JOIN `Online_Examination`.`registration` r ON r.`RegisID` = a.`RegisID`
      JOIN `Online_Examination`.`paper` p        ON p.`PaperKey` = a.`PaperKey`
    ) new_a
      ON new_a.`Attempt_no` = old_a.`Attempt_no`
     AND new_a.`RegisID`    = old_a.`RegisID`
     AND new_a.`PaperID`    = old_a.`PaperID`
     AND new_a.`Exam_ID`    = old_a.`Exam_ID`
     AND new_a.`StartTime` <=> old_a.`StartTime`
     AND new_a.`EndTime`   <=> old_a.`EndTime`
    WHERE new_a.`RegisID` IS NULL;

    SELECT COUNT(*) INTO v_spur_att
    FROM (
      SELECT a.`Attempt_no`, a.`RegisID`, a.`StartTime`, a.`EndTime`, p.`PaperID`, r.`Exam_ID`
      FROM `Online_Examination`.`attempt` a
      JOIN `Online_Examination`.`registration` r ON r.`RegisID` = a.`RegisID`
      JOIN `Online_Examination`.`paper` p        ON p.`PaperKey` = a.`PaperKey`
    ) new_a
    LEFT JOIN `online_examination_old`.`attempt` old_a
      ON old_a.`Attempt_no` = new_a.`Attempt_no`
     AND old_a.`RegisID`    = new_a.`RegisID`
     AND old_a.`PaperID`    = new_a.`PaperID`
     AND old_a.`Exam_ID`    = new_a.`Exam_ID`
     AND old_a.`StartTime` <=> new_a.`StartTime`
     AND old_a.`EndTime`   <=> new_a.`EndTime`
    WHERE old_a.`RegisID` IS NULL;

    SELECT COUNT(*) INTO v_miss_cnt
    FROM `online_examination_old`.`contains` old_c
    LEFT JOIN (
      SELECT p.`Exam_ID`, p.`PaperID`, pq.`Quest_ID`, pq.`QuestionOrder`, eqm.`Marks`
      FROM `Online_Examination`.`paper_question` pq
      JOIN `Online_Examination`.`paper` p                 ON p.`PaperKey` = pq.`PaperKey`
      JOIN `Online_Examination`.`exam_question_marks` eqm ON eqm.`Exam_ID` = p.`Exam_ID` AND eqm.`Quest_ID` = pq.`Quest_ID`
    ) new_c
      ON new_c.`Exam_ID`       = old_c.`Exam_ID`
     AND new_c.`PaperID`       = old_c.`PaperID`
     AND new_c.`Quest_ID`      = old_c.`Quest_ID`
     AND new_c.`QuestionOrder` = old_c.`QuestionOrder`
     AND new_c.`Marks`         = old_c.`Marks`
    WHERE new_c.`Quest_ID` IS NULL;

    SELECT COUNT(*) INTO v_spur_cnt
    FROM (
      SELECT p.`Exam_ID`, p.`PaperID`, pq.`Quest_ID`, pq.`QuestionOrder`, eqm.`Marks`
      FROM `Online_Examination`.`paper_question` pq
      JOIN `Online_Examination`.`paper` p                 ON p.`PaperKey` = pq.`PaperKey`
      JOIN `Online_Examination`.`exam_question_marks` eqm ON eqm.`Exam_ID` = p.`Exam_ID` AND eqm.`Quest_ID` = pq.`Quest_ID`
    ) new_c
    LEFT JOIN `online_examination_old`.`contains` old_c
      ON old_c.`Exam_ID`       = new_c.`Exam_ID`
     AND old_c.`PaperID`       = new_c.`PaperID`
     AND old_c.`Quest_ID`      = new_c.`Quest_ID`
     AND old_c.`QuestionOrder` = new_c.`QuestionOrder`
     AND old_c.`Marks`         = new_c.`Marks`
    WHERE old_c.`Quest_ID` IS NULL;

    SELECT 'missing_attempt_rejoin' AS verify_metric, v_miss_att AS violations
    UNION ALL SELECT 'spurious_attempt_rejoin', v_spur_att
    UNION ALL SELECT 'missing_contains_rejoin', v_miss_cnt
    UNION ALL SELECT 'spurious_contains_rejoin', v_spur_cnt;

    IF (v_miss_att + v_spur_att + v_miss_cnt + v_spur_cnt) > 0 THEN
      SET v_err_msg = LEFT(CONCAT(
        'Lossless check failed: miss_att=', v_miss_att,
        ', spur_att=', v_spur_att,
        ', miss_cnt=', v_miss_cnt,
        ', spur_cnt=', v_spur_cnt
      ), 128);
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_err_msg;
    END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `sp_migrate_old_to_new`();
DROP PROCEDURE IF EXISTS `sp_migrate_old_to_new`;
