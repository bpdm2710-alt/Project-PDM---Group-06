-- ============================================================================
-- IT079IU - Principles of Database Management (Sem 1, 2026-2027)
-- Group 06 | Topic 20: Online Examination System
-- Target DBMS: MySQL 8.0.16+ / MySQL 8.4 (InnoDB, utf8mb4_0900_ai_ci)
-- Schema Design: 21 Normalized Relations (All in BCNF; Business Rules Master v2 BR-01..BR-50)
-- Naming convention: ALL TABLE names are UPPER CASE (MySQL on Linux/Aiven is case-sensitive,
--   lower_case_table_names=0). Column, view, trigger and procedure names keep their own case.
-- Revision: AUTO_INCREMENT on USER/COURSE/TOPIC/QUESTION/EXAMINATION/REGISTRATION ids (app inserts rely on it);
--           + EXAMINATION.Status (DRAFT/PUBLISHED), Exam_date NULLable (= not scheduled yet).
-- ============================================================================

DROP DATABASE IF EXISTS `Online_Examination`;
CREATE DATABASE `Online_Examination`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `Online_Examination`;

-- ----------------------------------------------------------------------------
-- 1. USER (Superclass; Email is an enforced alternate candidate key)
-- ----------------------------------------------------------------------------
CREATE TABLE `USER` (
  `User_ID`       INT          NOT NULL AUTO_INCREMENT,
  `FirstName`     VARCHAR(50)  NOT NULL,
  `LastName`      VARCHAR(50)  NOT NULL,
  `DOB`           DATE         NOT NULL,
  `Email`         VARCHAR(100) NOT NULL,
  `Password_hash` VARCHAR(255) NOT NULL,
  CONSTRAINT `pk_user` PRIMARY KEY (`User_ID`),
  CONSTRAINT `uq_user_email` UNIQUE (`Email`),
  CONSTRAINT `chk_user_firstname_nonempty` CHECK (CHAR_LENGTH(TRIM(`FirstName`)) > 0),
  CONSTRAINT `chk_user_lastname_nonempty`  CHECK (CHAR_LENGTH(TRIM(`LastName`)) > 0),
  CONSTRAINT `chk_user_password_nonempty`  CHECK (CHAR_LENGTH(`Password_hash`) > 0),
  CONSTRAINT `chk_user_email_format`       CHECK (REGEXP_LIKE(`Email`, '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 2. USER_PHONE (1NF extraction of multivalued Phone; VARCHAR preserves '+' & 0)
-- ----------------------------------------------------------------------------
CREATE TABLE `USER_PHONE` (
  `Phone`   VARCHAR(20) NOT NULL,
  `User_ID` INT         NOT NULL,
  CONSTRAINT `pk_user_phone` PRIMARY KEY (`Phone`, `User_ID`),
  CONSTRAINT `chk_user_phone_nonempty` CHECK (CHAR_LENGTH(TRIM(`Phone`)) >= 7),
  CONSTRAINT `fk_user_phone_user`
    FOREIGN KEY (`User_ID`) REFERENCES `USER` (`User_ID`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 3. STUDENT (ISA subtype of user; total + disjoint guarded by procs/triggers)
-- ----------------------------------------------------------------------------
CREATE TABLE `STUDENT` (
  `User_ID`         INT NOT NULL,
  `Enrollment_year` INT NOT NULL,
  CONSTRAINT `pk_student` PRIMARY KEY (`User_ID`),
  CONSTRAINT `chk_student_enroll_year` CHECK (`Enrollment_year` BETWEEN 1990 AND 2100),
  CONSTRAINT `fk_student_user`
    FOREIGN KEY (`User_ID`) REFERENCES `USER` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. INSTRUCTOR (ISA subtype of user; total + disjoint guarded by procs/triggers)
-- ----------------------------------------------------------------------------
CREATE TABLE `INSTRUCTOR` (
  `User_ID`    INT          NOT NULL,
  `Department` VARCHAR(100) NOT NULL,
  CONSTRAINT `pk_instructor` PRIMARY KEY (`User_ID`),
  CONSTRAINT `chk_instructor_dept_nonempty` CHECK (CHAR_LENGTH(TRIM(`Department`)) > 0),
  CONSTRAINT `fk_instructor_user`
    FOREIGN KEY (`User_ID`) REFERENCES `USER` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 5. COURSE
-- ----------------------------------------------------------------------------
CREATE TABLE `COURSE` (
  `Course_ID` INT          NOT NULL AUTO_INCREMENT,
  `CName`     VARCHAR(100) NOT NULL,
  `Credits`   INT          NOT NULL,
  CONSTRAINT `pk_course` PRIMARY KEY (`Course_ID`),
  CONSTRAINT `chk_course_name_nonempty` CHECK (CHAR_LENGTH(TRIM(`CName`)) > 0),
  CONSTRAINT `chk_course_credits_pos`   CHECK (`Credits` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 6. REQUIRES (Course prerequisite M:N self-relationship with explicit roles)
-- ----------------------------------------------------------------------------
CREATE TABLE `REQUIRES` (
  `Prerequisite_ID` INT NOT NULL,
  `Advanced_ID`     INT NOT NULL,
  CONSTRAINT `pk_requires` PRIMARY KEY (`Prerequisite_ID`, `Advanced_ID`),
  CONSTRAINT `chk_requires_no_self` CHECK (`Prerequisite_ID` <> `Advanced_ID`),
  CONSTRAINT `fk_requires_prereq`
    FOREIGN KEY (`Prerequisite_ID`) REFERENCES `COURSE` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_requires_advanced`
    FOREIGN KEY (`Advanced_ID`) REFERENCES `COURSE` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 7. TOPIC
-- ----------------------------------------------------------------------------
CREATE TABLE `TOPIC` (
  `Topic_ID` INT  NOT NULL AUTO_INCREMENT,
  `Content`  TEXT NOT NULL,
  CONSTRAINT `pk_topic` PRIMARY KEY (`Topic_ID`),
  CONSTRAINT `chk_topic_content_nonempty` CHECK (CHAR_LENGTH(TRIM(`Content`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 8. COVERS (Course <-> Topic M:N relationship)
-- ----------------------------------------------------------------------------
CREATE TABLE `COVERS` (
  `Course_ID` INT NOT NULL,
  `Topic_ID`  INT NOT NULL,
  CONSTRAINT `pk_covers` PRIMARY KEY (`Course_ID`, `Topic_ID`),
  CONSTRAINT `fk_covers_course`
    FOREIGN KEY (`Course_ID`) REFERENCES `COURSE` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_covers_topic`
    FOREIGN KEY (`Topic_ID`) REFERENCES `TOPIC` (`Topic_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 9. TEACH (Instructor <-> Course M:N with Semester in PK per Decision #3)
-- ----------------------------------------------------------------------------
CREATE TABLE `TEACH` (
  `User_ID`   INT         NOT NULL,
  `Course_ID` INT         NOT NULL,
  `Semester`  VARCHAR(15) NOT NULL,
  CONSTRAINT `pk_teach` PRIMARY KEY (`User_ID`, `Course_ID`, `Semester`),
  CONSTRAINT `chk_teach_semester_fmt` CHECK (REGEXP_LIKE(`Semester`, '^20[0-9]{2}-(S[12]|SU)$', 'c')),
  CONSTRAINT `fk_teach_instructor`
    FOREIGN KEY (`User_ID`) REFERENCES `INSTRUCTOR` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_teach_course`
    FOREIGN KEY (`Course_ID`) REFERENCES `COURSE` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 10. QUESTION (Superclass; Topic_ID and authoring Instructor User_ID)
-- ----------------------------------------------------------------------------
CREATE TABLE `QUESTION` (
  `Quest_ID`   INT  NOT NULL AUTO_INCREMENT,
  `Content`    TEXT NOT NULL,
  `Difficulty` INT  NOT NULL,
  `Topic_ID`   INT  NOT NULL,
  `User_ID`    INT  NOT NULL,
  CONSTRAINT `pk_question` PRIMARY KEY (`Quest_ID`),
  CONSTRAINT `chk_question_content_nonempty` CHECK (CHAR_LENGTH(TRIM(`Content`)) > 0),
  CONSTRAINT `chk_question_difficulty`       CHECK (`Difficulty` BETWEEN 1 AND 5),
  CONSTRAINT `fk_question_topic`
    FOREIGN KEY (`Topic_ID`) REFERENCES `TOPIC` (`Topic_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_question_instructor`
    FOREIGN KEY (`User_ID`) REFERENCES `INSTRUCTOR` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 11. MCQ (ISA subtype of question)
-- ----------------------------------------------------------------------------
CREATE TABLE `MCQ` (
  `Quest_ID` INT NOT NULL,
  CONSTRAINT `pk_mcq` PRIMARY KEY (`Quest_ID`),
  CONSTRAINT `fk_mcq_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `QUESTION` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 12. SHORT_ANSWER (ISA subtype of question)
-- ----------------------------------------------------------------------------
CREATE TABLE `SHORT_ANSWER` (
  `Quest_ID`   INT  NOT NULL,
  `Answer_key` TEXT NOT NULL,
  CONSTRAINT `pk_short_answer` PRIMARY KEY (`Quest_ID`),
  CONSTRAINT `chk_short_answer_key_nonempty` CHECK (CHAR_LENGTH(TRIM(`Answer_key`)) > 0),
  CONSTRAINT `fk_short_answer_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `QUESTION` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 13. OPTIONS (Weak entity of mcq; Bool_Correct uses standard TINYINT + CHECK)
-- ----------------------------------------------------------------------------
CREATE TABLE `OPTIONS` (
  `Option_no`    INT     NOT NULL,
  `Quest_ID`     INT     NOT NULL,
  `Op_Content`   TEXT    NOT NULL,
  `Bool_Correct` TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT `pk_options` PRIMARY KEY (`Option_no`, `Quest_ID`),
  CONSTRAINT `chk_options_no_pos`      CHECK (`Option_no` >= 1),
  CONSTRAINT `chk_options_content`     CHECK (CHAR_LENGTH(TRIM(`Op_Content`)) > 0),
  CONSTRAINT `chk_options_bool_domain` CHECK (`Bool_Correct` IN (0, 1)),
  CONSTRAINT `fk_options_mcq`
    FOREIGN KEY (`Quest_ID`) REFERENCES `MCQ` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 14. EXAMINATION (Pass_mark = percentage 0.00-100.00; Status DRAFT/PUBLISHED; Exam_date NULL = not scheduled)
-- ----------------------------------------------------------------------------
CREATE TABLE `EXAMINATION` (
  `Exam_ID`      INT           NOT NULL AUTO_INCREMENT,
  `Title`        VARCHAR(150)  NOT NULL,
  `Exam_date`    DATETIME      NULL DEFAULT NULL,
  `Duration`     INT           NOT NULL,
  `Pass_mark`    DECIMAL(5,2)  NOT NULL,
  `Max_attempts` INT           NOT NULL DEFAULT 1,
  `Course_ID`    INT           NOT NULL,
  `Status`       ENUM('DRAFT','PUBLISHED') NOT NULL DEFAULT 'DRAFT',
  CONSTRAINT `pk_examination` PRIMARY KEY (`Exam_ID`),
  CONSTRAINT `chk_exam_title_nonempty` CHECK (CHAR_LENGTH(TRIM(`Title`)) > 0),
  CONSTRAINT `chk_exam_duration_pos`   CHECK (`Duration` > 0),
  CONSTRAINT `chk_exam_pass_mark_pct`  CHECK (`Pass_mark` BETWEEN 0.00 AND 100.00),
  CONSTRAINT `chk_exam_max_attempts`   CHECK (`Max_attempts` >= 1),
  CONSTRAINT `chk_exam_published_dated` CHECK (`Status` = 'DRAFT' OR `Exam_date` IS NOT NULL),
  CONSTRAINT `fk_examination_course`
    FOREIGN KEY (`Course_ID`) REFERENCES `COURSE` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 15. PAPER (Surrogate PaperKey PK + UNIQUE(Exam_ID, PaperID) per Decision #2 B1)
-- ----------------------------------------------------------------------------
CREATE TABLE `PAPER` (
  `PaperKey` INT NOT NULL AUTO_INCREMENT,
  `PaperID`  INT NOT NULL,
  `Exam_ID`  INT NOT NULL,
  CONSTRAINT `pk_paper` PRIMARY KEY (`PaperKey`),
  CONSTRAINT `uq_paper_exam_paperid` UNIQUE (`Exam_ID`, `PaperID`),
  CONSTRAINT `chk_paper_id_pos` CHECK (`PaperID` >= 1),
  CONSTRAINT `fk_paper_examination`
    FOREIGN KEY (`Exam_ID`) REFERENCES `EXAMINATION` (`Exam_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 16. EXAM_QUESTION_MARKS (Marks per Exam_ID + Quest_ID per Topic 20 / #7B)
-- ----------------------------------------------------------------------------
CREATE TABLE `EXAM_QUESTION_MARKS` (
  `Exam_ID`  INT          NOT NULL,
  `Quest_ID` INT          NOT NULL,
  `Marks`    DECIMAL(5,2) NOT NULL,
  CONSTRAINT `pk_exam_question_marks` PRIMARY KEY (`Exam_ID`, `Quest_ID`),
  CONSTRAINT `chk_eqm_marks_pos` CHECK (`Marks` > 0.00),
  CONSTRAINT `fk_eqm_examination`
    FOREIGN KEY (`Exam_ID`) REFERENCES `EXAMINATION` (`Exam_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_eqm_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `QUESTION` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 17. PAPER_QUESTION (Layout and question ordering per paper; BCNF)
-- ----------------------------------------------------------------------------
CREATE TABLE `PAPER_QUESTION` (
  `PaperKey`      INT NOT NULL,
  `Quest_ID`      INT NOT NULL,
  `QuestionOrder` INT NOT NULL,
  CONSTRAINT `pk_paper_question` PRIMARY KEY (`PaperKey`, `Quest_ID`),
  CONSTRAINT `uq_pq_paper_order` UNIQUE (`PaperKey`, `QuestionOrder`),
  CONSTRAINT `chk_pq_order_pos`  CHECK (`QuestionOrder` >= 1),
  CONSTRAINT `fk_pq_paper`
    FOREIGN KEY (`PaperKey`) REFERENCES `PAPER` (`PaperKey`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_pq_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `QUESTION` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 18. REGISTRATION (One registration per student-examination pair)
-- ----------------------------------------------------------------------------
CREATE TABLE `REGISTRATION` (
  `RegisID`   INT      NOT NULL AUTO_INCREMENT,
  `RegisDate` DATETIME NOT NULL,
  `User_ID`   INT      NOT NULL,
  `Exam_ID`   INT      NOT NULL,
  CONSTRAINT `pk_registration` PRIMARY KEY (`RegisID`),
  CONSTRAINT `uq_registration_student_exam` UNIQUE (`User_ID`, `Exam_ID`),
  CONSTRAINT `fk_registration_student`
    FOREIGN KEY (`User_ID`) REFERENCES `STUDENT` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_registration_exam`
    FOREIGN KEY (`Exam_ID`) REFERENCES `EXAMINATION` (`Exam_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 19. ATTEMPT (Exam_ID removed -> BCNF; EndTime NULL = in-progress attempt)
-- ----------------------------------------------------------------------------
CREATE TABLE `ATTEMPT` (
  `Attempt_no` INT      NOT NULL,
  `RegisID`    INT      NOT NULL,
  `StartTime`  DATETIME NOT NULL,
  `EndTime`    DATETIME NULL DEFAULT NULL,
  `PaperKey`   INT      NOT NULL,
  CONSTRAINT `pk_attempt` PRIMARY KEY (`Attempt_no`, `RegisID`),
  CONSTRAINT `chk_attempt_no_pos`    CHECK (`Attempt_no` >= 1),
  CONSTRAINT `chk_attempt_time_order` CHECK (`EndTime` IS NULL OR `EndTime` >= `StartTime`),
  CONSTRAINT `fk_attempt_registration`
    FOREIGN KEY (`RegisID`) REFERENCES `REGISTRATION` (`RegisID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_attempt_paper`
    FOREIGN KEY (`PaperKey`) REFERENCES `PAPER` (`PaperKey`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 20. ANSWER (NULL AnswerText for MCQ/blank; NULL MarkAwarded = ungraded)
-- ----------------------------------------------------------------------------
CREATE TABLE `ANSWER` (
  `Quest_ID`    INT          NOT NULL,
  `Attempt_no`  INT          NOT NULL,
  `RegisID`     INT          NOT NULL,
  `AnswerText`  TEXT         NULL DEFAULT NULL,
  `MarkAwarded` DECIMAL(5,2) NULL DEFAULT NULL,
  CONSTRAINT `pk_answer` PRIMARY KEY (`Quest_ID`, `Attempt_no`, `RegisID`),
  CONSTRAINT `chk_answer_mark_nonneg` CHECK (`MarkAwarded` IS NULL OR `MarkAwarded` >= 0.00),
  CONSTRAINT `fk_answer_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `QUESTION` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_answer_attempt`
    FOREIGN KEY (`Attempt_no`, `RegisID`) REFERENCES `ATTEMPT` (`Attempt_no`, `RegisID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 21. SELECT_TABLE (Single-select MCQ PK = (Quest_ID, Attempt_no, RegisID))
-- ----------------------------------------------------------------------------
CREATE TABLE `SELECT_TABLE` (
  `Option_no`  INT NOT NULL,
  `Quest_ID`   INT NOT NULL,
  `Attempt_no` INT NOT NULL,
  `RegisID`    INT NOT NULL,
  CONSTRAINT `pk_select_table` PRIMARY KEY (`Quest_ID`, `Attempt_no`, `RegisID`),
  CONSTRAINT `fk_select_option`
    FOREIGN KEY (`Option_no`, `Quest_ID`) REFERENCES `OPTIONS` (`Option_no`, `Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_select_answer`
    FOREIGN KEY (`Quest_ID`, `Attempt_no`, `RegisID`) REFERENCES `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- PURPOSEFUL SECONDARY INDEXES (Assignment Sec 2.3 requirement)
-- ============================================================================
-- Speeds up Instructor Question Bank filtering by topic & difficulty:
CREATE INDEX `idx_question_topic_diff`   ON `QUESTION` (`Topic_ID`, `Difficulty`);
-- Speeds up Student "My Exams" / course exam schedule lookups:
CREATE INDEX `idx_examination_course_date` ON `EXAMINATION` (`Course_ID`, `Exam_date`);
-- Speeds up Exam->Registration->Attempt analytical joins (Topic 20 Queries #3-#5):
CREATE INDEX `idx_registration_exam_user` ON `REGISTRATION` (`Exam_ID`, `User_ID`);
CREATE INDEX `idx_attempt_paper`          ON `ATTEMPT` (`PaperKey`);
-- Speeds up retrieving all answers for a single attempt (Query #2 & Results screen):
CREATE INDEX `idx_answer_attempt`         ON `ANSWER` (`RegisID`, `Attempt_no`);

-- ============================================================================
-- DERIVED VIEWS (EER Derived Attributes & Query #9 Representative Attempt)
-- ============================================================================
CREATE VIEW `v_paper_total_marks` AS
SELECT
  p.`PaperKey`,
  p.`Exam_ID`,
  p.`PaperID`,
  COALESCE(SUM(eqm.`Marks`), 0.00) AS `TotalMarks`,
  COUNT(pq.`Quest_ID`)             AS `QuestionCount`
FROM `PAPER` p
LEFT JOIN `PAPER_QUESTION` pq       ON pq.`PaperKey` = p.`PaperKey`
LEFT JOIN `EXAM_QUESTION_MARKS` eqm ON eqm.`Exam_ID` = p.`Exam_ID` AND eqm.`Quest_ID` = pq.`Quest_ID`
GROUP BY p.`PaperKey`, p.`Exam_ID`, p.`PaperID`;

CREATE VIEW `v_attempt_scores` AS
SELECT
  a.`Attempt_no`,
  a.`RegisID`,
  r.`User_ID`,
  r.`Exam_ID`,
  a.`PaperKey`,
  a.`StartTime`,
  a.`EndTime`,
  e.`Pass_mark`,
  COALESCE(SUM(eqm.`Marks`), 0.00)                                        AS `PaperTotalMarks`,
  COUNT(pq.`Quest_ID`)                                                    AS `AssignedQuestions`,
  COUNT(an.`MarkAwarded`)                                                 AS `GradedQuestions`,
  CASE
    WHEN a.`EndTime` IS NOT NULL
     AND COUNT(pq.`Quest_ID`) > 0
     AND SUM(CASE WHEN an.`MarkAwarded` IS NULL THEN 1 ELSE 0 END) = 0
    THEN 1 ELSE 0
  END                                                                     AS `IsFullyGraded`,
  CASE
    WHEN a.`EndTime` IS NOT NULL
     AND COUNT(pq.`Quest_ID`) > 0
     AND SUM(CASE WHEN an.`MarkAwarded` IS NULL THEN 1 ELSE 0 END) = 0
    THEN SUM(an.`MarkAwarded`)
    ELSE NULL
  END                                                                     AS `Total_Score`,
  CASE
    WHEN a.`EndTime` IS NOT NULL
     AND COUNT(pq.`Quest_ID`) > 0
     AND SUM(CASE WHEN an.`MarkAwarded` IS NULL THEN 1 ELSE 0 END) = 0
    THEN ROUND(100.0 * SUM(an.`MarkAwarded`) / NULLIF(SUM(eqm.`Marks`), 0), 2)
    ELSE NULL
  END                                                                     AS `ScorePercent`,
  CASE
    WHEN a.`EndTime` IS NOT NULL
     AND COUNT(pq.`Quest_ID`) > 0
     AND SUM(CASE WHEN an.`MarkAwarded` IS NULL THEN 1 ELSE 0 END) = 0
    THEN CASE
      WHEN (100.0 * SUM(an.`MarkAwarded`) / NULLIF(SUM(eqm.`Marks`), 0)) >= e.`Pass_mark` THEN 1
      ELSE 0
    END
    ELSE NULL
  END                                                                     AS `IsPassed`
FROM `ATTEMPT` a
JOIN `REGISTRATION` r               ON r.`RegisID` = a.`RegisID`
JOIN `EXAMINATION` e                ON e.`Exam_ID` = r.`Exam_ID`
JOIN `PAPER` p                      ON p.`PaperKey` = a.`PaperKey`
LEFT JOIN `PAPER_QUESTION` pq       ON pq.`PaperKey` = a.`PaperKey`
LEFT JOIN `EXAM_QUESTION_MARKS` eqm ON eqm.`Exam_ID` = p.`Exam_ID` AND eqm.`Quest_ID` = pq.`Quest_ID`
LEFT JOIN `ANSWER` an               ON an.`Quest_ID` = pq.`Quest_ID`
                                   AND an.`Attempt_no` = a.`Attempt_no`
                                   AND an.`RegisID` = a.`RegisID`
GROUP BY
  a.`Attempt_no`, a.`RegisID`, r.`User_ID`, r.`Exam_ID`, a.`PaperKey`,
  a.`StartTime`, a.`EndTime`, e.`Pass_mark`;

CREATE VIEW `v_representative_attempts` AS
WITH ranked AS (
  SELECT
    v.*,
    ROW_NUMBER() OVER (
      PARTITION BY v.`User_ID`, v.`Exam_ID`
      ORDER BY v.`EndTime` DESC, v.`Attempt_no` DESC
    ) AS rn
  FROM `v_attempt_scores` v
  WHERE v.`IsFullyGraded` = 1
)
SELECT
  `User_ID`, `Exam_ID`, `RegisID`, `Attempt_no`, `PaperKey`,
  `StartTime`, `EndTime`, `Pass_mark`, `PaperTotalMarks`, `Total_Score`, `ScorePercent`, `IsPassed`
FROM ranked
WHERE rn = 1;


-- Shared view from PDMProjectQuery.sql: average awarded mark per topic.
CREATE VIEW `vw_AverageScoreByTopic` AS
SELECT T.`Topic_ID`, T.`Content` AS `Topic_Name`, AVG(A.`MarkAwarded`) AS `Average_Score`
FROM `TOPIC` T
JOIN `QUESTION` Q ON T.`Topic_ID` = Q.`Topic_ID`
JOIN `ANSWER` A   ON Q.`Quest_ID` = A.`Quest_ID`
WHERE A.`MarkAwarded` IS NOT NULL
GROUP BY T.`Topic_ID`, T.`Content`;

-- ============================================================================
-- CROSS-TABLE TRIGGERS & HISTORICAL FREEZE GUARDS
-- ============================================================================
DELIMITER $$

-- 1) ISA Disjointness Guards (BR-05, BR-17)
CREATE TRIGGER `trg_student_bi_disjoint`
BEFORE INSERT ON `STUDENT`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `INSTRUCTOR` WHERE `User_ID` = NEW.`User_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISA violation: User already belongs to instructor subtype.';
  END IF;
END$$

CREATE TRIGGER `trg_instructor_bi_disjoint`
BEFORE INSERT ON `INSTRUCTOR`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `STUDENT` WHERE `User_ID` = NEW.`User_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISA violation: User already belongs to student subtype.';
  END IF;
END$$

CREATE TRIGGER `trg_mcq_bi_disjoint`
BEFORE INSERT ON `MCQ`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `SHORT_ANSWER` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISA violation: Question already belongs to short_answer subtype.';
  END IF;
END$$

CREATE TRIGGER `trg_short_answer_bi_disjoint`
BEFORE INSERT ON `SHORT_ANSWER`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `MCQ` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISA violation: Question already belongs to mcq subtype.';
  END IF;
END$$

-- 2) Prerequisite DAG Acyclicity Guard (BR-10)
CREATE TRIGGER `trg_requires_bi_no_cycle`
BEFORE INSERT ON `REQUIRES`
FOR EACH ROW
BEGIN
  IF NEW.`Prerequisite_ID` <> NEW.`Advanced_ID` AND EXISTS (
    WITH RECURSIVE reach AS (
      SELECT NEW.`Advanced_ID` AS cid
      UNION
      SELECT r.`Advanced_ID`
      FROM `REQUIRES` r
      JOIN reach ON r.`Prerequisite_ID` = reach.cid
    )
    SELECT 1 FROM reach WHERE cid = NEW.`Prerequisite_ID` LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cycle detected in course prerequisites (requires must be acyclic).';
  END IF;
END$$

CREATE TRIGGER `trg_requires_bu_no_cycle`
BEFORE UPDATE ON `REQUIRES`
FOR EACH ROW
BEGIN
  IF NEW.`Prerequisite_ID` <> NEW.`Advanced_ID` AND EXISTS (
    WITH RECURSIVE reach AS (
      SELECT NEW.`Advanced_ID` AS cid
      UNION
      SELECT r.`Advanced_ID`
      FROM `REQUIRES` r
      JOIN reach ON r.`Prerequisite_ID` = reach.cid
      WHERE NOT (r.`Prerequisite_ID` = OLD.`Prerequisite_ID` AND r.`Advanced_ID` = OLD.`Advanced_ID`)
    )
    SELECT 1 FROM reach WHERE cid = NEW.`Prerequisite_ID` LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cycle detected while updating course prerequisites.';
  END IF;
END$$

-- 3) Question Topic, Freeze & Covers Alignment Guards (BR-21, BR-30, BR-43)
CREATE TRIGGER `trg_question_bu_freeze_and_topic`
BEFORE UPDATE ON `QUESTION`
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
    FROM `PAPER_QUESTION` pq
    JOIN `ATTEMPT` a ON a.`PaperKey` = pq.`PaperKey`
    WHERE pq.`Quest_ID` = OLD.`Quest_ID`
  ) THEN
    IF NEW.`Content` <> OLD.`Content` OR NEW.`Topic_ID` <> OLD.`Topic_ID` THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot modify Content or Topic_ID of a question already attempted.';
    END IF;
  END IF;
  IF NEW.`Topic_ID` <> OLD.`Topic_ID` THEN
    IF EXISTS (
      SELECT 1
      FROM `EXAM_QUESTION_MARKS` eqm
      JOIN `EXAMINATION` e ON e.`Exam_ID` = eqm.`Exam_ID`
      WHERE eqm.`Quest_ID` = OLD.`Quest_ID`
        AND NOT EXISTS (
          SELECT 1 FROM `COVERS` cv
          WHERE cv.`Course_ID` = e.`Course_ID` AND cv.`Topic_ID` = NEW.`Topic_ID`
        )
    ) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'New Topic_ID is not covered by the course of an examination using this question.';
    END IF;
  END IF;
END$$

CREATE TRIGGER `trg_covers_bd_in_use`
BEFORE DELETE ON `COVERS`
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
    FROM `EXAM_QUESTION_MARKS` eqm
    JOIN `EXAMINATION` e ON e.`Exam_ID` = eqm.`Exam_ID`
    JOIN `QUESTION` q    ON q.`Quest_ID` = eqm.`Quest_ID`
    WHERE e.`Course_ID` = OLD.`Course_ID` AND q.`Topic_ID` = OLD.`Topic_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot remove covers link: examination questions still rely on this course-topic mapping.';
  END IF;
END$$

CREATE TRIGGER `trg_covers_bu_in_use`
BEFORE UPDATE ON `COVERS`
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
    FROM `EXAM_QUESTION_MARKS` eqm
    JOIN `EXAMINATION` e ON e.`Exam_ID` = eqm.`Exam_ID`
    JOIN `QUESTION` q    ON q.`Quest_ID` = eqm.`Quest_ID`
    WHERE e.`Course_ID` = OLD.`Course_ID` AND q.`Topic_ID` = OLD.`Topic_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot update covers link while referenced by examination questions.';
  END IF;
END$$

-- 4) Options Integrity & Historical Freeze Guards (BR-20, BR-43)
CREATE TRIGGER `trg_options_bi_freeze`
BEFORE INSERT ON `OPTIONS`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `ANSWER` WHERE `Quest_ID` = NEW.`Quest_ID`)
     OR EXISTS (
       SELECT 1 FROM `PAPER_QUESTION` pq
       JOIN `ATTEMPT` a ON a.`PaperKey` = pq.`PaperKey`
       WHERE pq.`Quest_ID` = NEW.`Quest_ID`
     ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot add options to an already attempted MCQ.';
  END IF;
  IF NEW.`Bool_Correct` = 1
     AND EXISTS (SELECT 1 FROM `PAPER_QUESTION` WHERE `Quest_ID` = NEW.`Quest_ID`)
     AND EXISTS (SELECT 1 FROM `OPTIONS` WHERE `Quest_ID` = NEW.`Quest_ID` AND `Bool_Correct` = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Single-answer MCQ in an active paper cannot have more than one correct option.';
  END IF;
END$$

CREATE TRIGGER `trg_options_bu_checks`
BEFORE UPDATE ON `OPTIONS`
FOR EACH ROW
BEGIN
  DECLARE v_correct INT;
  IF EXISTS (SELECT 1 FROM `ANSWER` WHERE `Quest_ID` = OLD.`Quest_ID`)
     OR EXISTS (
       SELECT 1 FROM `PAPER_QUESTION` pq
       JOIN `ATTEMPT` a ON a.`PaperKey` = pq.`PaperKey`
       WHERE pq.`Quest_ID` = OLD.`Quest_ID`
     ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot modify options of an already attempted MCQ.';
  END IF;
  IF EXISTS (SELECT 1 FROM `PAPER_QUESTION` WHERE `Quest_ID` = OLD.`Quest_ID`) AND NEW.`Bool_Correct` <> OLD.`Bool_Correct` THEN
    SELECT SUM(CASE WHEN `Bool_Correct` = 1 THEN 1 ELSE 0 END) INTO v_correct
    FROM `OPTIONS` WHERE `Quest_ID` = OLD.`Quest_ID`;
    SET v_correct = COALESCE(v_correct, 0) - OLD.`Bool_Correct` + NEW.`Bool_Correct`;
    IF v_correct <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MCQ in a paper must keep exactly one correct option (Bool_Correct = 1).';
    END IF;
  END IF;
END$$

CREATE TRIGGER `trg_options_bd_checks`
BEFORE DELETE ON `OPTIONS`
FOR EACH ROW
BEGIN
  DECLARE v_total INT;
  IF EXISTS (SELECT 1 FROM `ANSWER` WHERE `Quest_ID` = OLD.`Quest_ID`)
     OR EXISTS (
       SELECT 1 FROM `PAPER_QUESTION` pq
       JOIN `ATTEMPT` a ON a.`PaperKey` = pq.`PaperKey`
       WHERE pq.`Quest_ID` = OLD.`Quest_ID`
     ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot delete options of an already attempted MCQ.';
  END IF;
  IF EXISTS (SELECT 1 FROM `PAPER_QUESTION` WHERE `Quest_ID` = OLD.`Quest_ID`) THEN
    SELECT COUNT(*) INTO v_total FROM `OPTIONS` WHERE `Quest_ID` = OLD.`Quest_ID`;
    IF v_total - 1 < 2 OR OLD.`Bool_Correct` = 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete option: active paper MCQ requires >=2 options and 1 correct option.';
    END IF;
  END IF;
END$$

CREATE TRIGGER `trg_short_answer_bu_freeze`
BEFORE UPDATE ON `SHORT_ANSWER`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `ANSWER` WHERE `Quest_ID` = OLD.`Quest_ID`)
     OR EXISTS (
       SELECT 1 FROM `PAPER_QUESTION` pq
       JOIN `ATTEMPT` a ON a.`PaperKey` = pq.`PaperKey`
       WHERE pq.`Quest_ID` = OLD.`Quest_ID`
     ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot modify Answer_key after attempts exist.';
  END IF;
END$$

-- 5) exam_question_marks Guards (BR-27, BR-29, BR-30, BR-38, BR-43)
CREATE TRIGGER `trg_eqm_bi_topic`
BEFORE INSERT ON `EXAM_QUESTION_MARKS`
FOR EACH ROW
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM `EXAMINATION` e
    JOIN `QUESTION` q ON q.`Quest_ID` = NEW.`Quest_ID`
    JOIN `COVERS` cv  ON cv.`Course_ID` = e.`Course_ID` AND cv.`Topic_ID` = q.`Topic_ID`
    WHERE e.`Exam_ID` = NEW.`Exam_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Question topic is not covered by the course of this examination.';
  END IF;
END$$

CREATE TRIGGER `trg_eqm_bu_checks`
BEFORE UPDATE ON `EXAM_QUESTION_MARKS`
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
    FROM `PAPER` p
    JOIN `ATTEMPT` a ON a.`PaperKey` = p.`PaperKey`
    WHERE p.`Exam_ID` = OLD.`Exam_ID`
  ) AND (NEW.`Marks` <> OLD.`Marks` OR NEW.`Exam_ID` <> OLD.`Exam_ID` OR NEW.`Quest_ID` <> OLD.`Quest_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot alter Marks after attempts exist for this examination.';
  END IF;
  IF (NEW.`Exam_ID` <> OLD.`Exam_ID` OR NEW.`Quest_ID` <> OLD.`Quest_ID`) THEN
    IF EXISTS (
      SELECT 1
      FROM `PAPER_QUESTION` pq
      JOIN `PAPER` p ON p.`PaperKey` = pq.`PaperKey`
      WHERE p.`Exam_ID` = OLD.`Exam_ID` AND pq.`Quest_ID` = OLD.`Quest_ID`
    ) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot reassign exam_question_marks identity while a paper uses it.';
    END IF;
    IF NOT EXISTS (
      SELECT 1
      FROM `EXAMINATION` e
      JOIN `QUESTION` q ON q.`Quest_ID` = NEW.`Quest_ID`
      JOIN `COVERS` cv  ON cv.`Course_ID` = e.`Course_ID` AND cv.`Topic_ID` = q.`Topic_ID`
      WHERE e.`Exam_ID` = NEW.`Exam_ID`
    ) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Question topic is not covered by the course of this examination.';
    END IF;
  END IF;
  IF EXISTS (
    SELECT 1
    FROM `ANSWER` an
    JOIN `ATTEMPT` a ON a.`Attempt_no` = an.`Attempt_no` AND a.`RegisID` = an.`RegisID`
    JOIN `PAPER` p   ON p.`PaperKey` = a.`PaperKey`
    WHERE p.`Exam_ID` = OLD.`Exam_ID`
      AND an.`Quest_ID` = OLD.`Quest_ID`
      AND an.`MarkAwarded` > NEW.`Marks`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot lower Marks below already awarded answer marks.';
  END IF;
END$$

CREATE TRIGGER `trg_eqm_bd_in_use`
BEFORE DELETE ON `EXAM_QUESTION_MARKS`
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
    FROM `PAPER_QUESTION` pq
    JOIN `PAPER` p ON p.`PaperKey` = pq.`PaperKey`
    WHERE p.`Exam_ID` = OLD.`Exam_ID` AND pq.`Quest_ID` = OLD.`Quest_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete exam_question_marks while question is placed on a paper layout.';
  END IF;
END$$

-- 6) paper_question Layout, Marks, Topic & MCQ Readiness Guards (BR-20, BR-29, BR-30, BR-43)
CREATE TRIGGER `trg_pq_bi_checks`
BEFORE INSERT ON `PAPER_QUESTION`
FOR EACH ROW
BEGIN
  DECLARE v_exam_id INT;
  DECLARE v_opt_cnt INT;
  DECLARE v_cor_cnt INT;

  SELECT `Exam_ID` INTO v_exam_id FROM `PAPER` WHERE `PaperKey` = NEW.`PaperKey`;

  IF EXISTS (SELECT 1 FROM `ATTEMPT` WHERE `PaperKey` = NEW.`PaperKey`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot add questions to a paper with existing attempts.';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM `EXAM_QUESTION_MARKS`
    WHERE `Exam_ID` = v_exam_id AND `Quest_ID` = NEW.`Quest_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing exam_question_marks entry for (Exam_ID, Quest_ID) before paper placement.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM `EXAMINATION` e
    JOIN `QUESTION` q ON q.`Quest_ID` = NEW.`Quest_ID`
    JOIN `COVERS` cv  ON cv.`Course_ID` = e.`Course_ID` AND cv.`Topic_ID` = q.`Topic_ID`
    WHERE e.`Exam_ID` = v_exam_id
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Question topic is not covered by the examination course.';
  END IF;

  IF EXISTS (SELECT 1 FROM `MCQ` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
    SELECT COUNT(*), COALESCE(SUM(CASE WHEN `Bool_Correct` = 1 THEN 1 ELSE 0 END), 0)
      INTO v_opt_cnt, v_cor_cnt
    FROM `OPTIONS` WHERE `Quest_ID` = NEW.`Quest_ID`;
    IF v_opt_cnt < 2 OR v_cor_cnt <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MCQ must have at least 2 options and exactly 1 correct option before paper placement.';
    END IF;
  ELSEIF NOT EXISTS (SELECT 1 FROM `SHORT_ANSWER` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISA violation: Question has neither mcq nor short_answer subtype.';
  END IF;
END$$

CREATE TRIGGER `trg_pq_bu_checks`
BEFORE UPDATE ON `PAPER_QUESTION`
FOR EACH ROW
BEGIN
  DECLARE v_exam_id INT;
  DECLARE v_opt_cnt INT;
  DECLARE v_cor_cnt INT;

  IF EXISTS (SELECT 1 FROM `ATTEMPT` WHERE `PaperKey` = OLD.`PaperKey`)
     OR EXISTS (SELECT 1 FROM `ATTEMPT` WHERE `PaperKey` = NEW.`PaperKey`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot modify paper_question layout after attempts exist.';
  END IF;

  IF NEW.`PaperKey` <> OLD.`PaperKey` OR NEW.`Quest_ID` <> OLD.`Quest_ID` THEN
    SELECT `Exam_ID` INTO v_exam_id FROM `PAPER` WHERE `PaperKey` = NEW.`PaperKey`;

    IF NOT EXISTS (
      SELECT 1 FROM `EXAM_QUESTION_MARKS`
      WHERE `Exam_ID` = v_exam_id AND `Quest_ID` = NEW.`Quest_ID`
    ) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing exam_question_marks entry for (Exam_ID, Quest_ID) before paper placement.';
    END IF;

    IF NOT EXISTS (
      SELECT 1
      FROM `EXAMINATION` e
      JOIN `QUESTION` q ON q.`Quest_ID` = NEW.`Quest_ID`
      JOIN `COVERS` cv  ON cv.`Course_ID` = e.`Course_ID` AND cv.`Topic_ID` = q.`Topic_ID`
      WHERE e.`Exam_ID` = v_exam_id
    ) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Question topic is not covered by the examination course.';
    END IF;

    IF EXISTS (SELECT 1 FROM `MCQ` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
      SELECT COUNT(*), COALESCE(SUM(CASE WHEN `Bool_Correct` = 1 THEN 1 ELSE 0 END), 0)
        INTO v_opt_cnt, v_cor_cnt
      FROM `OPTIONS` WHERE `Quest_ID` = NEW.`Quest_ID`;
      IF v_opt_cnt < 2 OR v_cor_cnt <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MCQ must have at least 2 options and exactly 1 correct option before paper placement.';
      END IF;
    ELSEIF NOT EXISTS (SELECT 1 FROM `SHORT_ANSWER` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISA violation: Question has neither mcq nor short_answer subtype.';
    END IF;
  END IF;
END$$

CREATE TRIGGER `trg_pq_bd_freeze`
BEFORE DELETE ON `PAPER_QUESTION`
FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM `ATTEMPT` WHERE `PaperKey` = OLD.`PaperKey`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot remove questions from a paper after attempts exist.';
  END IF;
END$$

-- 7) Registration Date & Parent Update Guards (BR-32, BR-34, BR-35)
CREATE TRIGGER `trg_registration_bi_date`
BEFORE INSERT ON `REGISTRATION`
FOR EACH ROW
BEGIN
  DECLARE v_exam_date DATETIME;
  SELECT `Exam_date` INTO v_exam_date FROM `EXAMINATION` WHERE `Exam_ID` = NEW.`Exam_ID`;
  IF NEW.`RegisDate` > v_exam_date THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registration date must be on or before Exam_date.';
  END IF;
END$$

CREATE TRIGGER `trg_registration_bu_guard`
BEFORE UPDATE ON `REGISTRATION`
FOR EACH ROW
BEGIN
  DECLARE v_exam_date DATETIME;
  IF NEW.`Exam_ID` <> OLD.`Exam_ID` AND EXISTS (SELECT 1 FROM `ATTEMPT` WHERE `RegisID` = OLD.`RegisID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot change registration.Exam_ID when attempts already exist.';
  END IF;
  SELECT `Exam_date` INTO v_exam_date FROM `EXAMINATION` WHERE `Exam_ID` = NEW.`Exam_ID`;
  IF NEW.`RegisDate` > v_exam_date THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registration date must be on or before Exam_date.';
  END IF;
END$$

CREATE TRIGGER `trg_paper_bu_guard`
BEFORE UPDATE ON `PAPER`
FOR EACH ROW
BEGIN
  IF NEW.`Exam_ID` <> OLD.`Exam_ID` AND (
    EXISTS (SELECT 1 FROM `ATTEMPT` WHERE `PaperKey` = OLD.`PaperKey`)
    OR EXISTS (SELECT 1 FROM `PAPER_QUESTION` WHERE `PaperKey` = OLD.`PaperKey`)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot reassign paper.Exam_ID when layout or attempts already exist.';
  END IF;
END$$

CREATE TRIGGER `trg_examination_bu_guard`
BEFORE UPDATE ON `EXAMINATION`
FOR EACH ROW
BEGIN
  DECLARE v_max_used INT;
  DECLARE v_has_attempts INT DEFAULT 0;

  SELECT EXISTS (
    SELECT 1 FROM `ATTEMPT` a
    JOIN `REGISTRATION` r ON r.`RegisID` = a.`RegisID`
    WHERE r.`Exam_ID` = OLD.`Exam_ID`
  ) INTO v_has_attempts;

  IF v_has_attempts = 1 AND (NEW.`Course_ID` <> OLD.`Course_ID` OR NEW.`Pass_mark` <> OLD.`Pass_mark`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical freeze: cannot alter Course_ID or Pass_mark after attempts exist.';
  END IF;

  SELECT COALESCE(MAX(a.`Attempt_no`), 0) INTO v_max_used
  FROM `ATTEMPT` a
  JOIN `REGISTRATION` r ON r.`RegisID` = a.`RegisID`
  WHERE r.`Exam_ID` = OLD.`Exam_ID`;

  IF NEW.`Max_attempts` < v_max_used THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot lower Max_attempts below largest issued Attempt_no.';
  END IF;
  IF EXISTS (SELECT 1 FROM `REGISTRATION` WHERE `Exam_ID` = OLD.`Exam_ID` AND `RegisDate` > NEW.`Exam_date`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot move Exam_date earlier than existing registration dates.';
  END IF;
  IF NEW.`Course_ID` <> OLD.`Course_ID` AND EXISTS (
    SELECT 1 FROM `EXAM_QUESTION_MARKS` eqm
    JOIN `QUESTION` q ON q.`Quest_ID` = eqm.`Quest_ID`
    WHERE eqm.`Exam_ID` = OLD.`Exam_ID`
      AND NOT EXISTS (
        SELECT 1 FROM `COVERS` cv WHERE cv.`Course_ID` = NEW.`Course_ID` AND cv.`Topic_ID` = q.`Topic_ID`
      )
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot change examination Course_ID: existing exam questions fall outside new course topics.';
  END IF;
END$$

-- 8) Attempt Same-Exam, Non-Empty Paper & Cap Guards (BR-28, BR-33, BR-34, BR-35)
CREATE TRIGGER `trg_attempt_bi_checks`
BEFORE INSERT ON `ATTEMPT`
FOR EACH ROW
BEGIN
  DECLARE v_reg_exam   INT;
  DECLARE v_paper_exam INT;
  DECLARE v_max_att    INT;
  DECLARE v_cnt        INT;

  SELECT r.`Exam_ID`, e.`Max_attempts` INTO v_reg_exam, v_max_att
  FROM `REGISTRATION` r
  JOIN `EXAMINATION` e ON e.`Exam_ID` = r.`Exam_ID`
  WHERE r.`RegisID` = NEW.`RegisID`;

  SELECT `Exam_ID` INTO v_paper_exam FROM `PAPER` WHERE `PaperKey` = NEW.`PaperKey`;

  IF v_reg_exam <> v_paper_exam THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Assigned paper belongs to a different examination than the student registration.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM `PAPER_QUESTION` WHERE `PaperKey` = NEW.`PaperKey`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot assign an empty paper with zero questions to an attempt.';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM `ATTEMPT` WHERE `RegisID` = NEW.`RegisID`;
  IF v_cnt >= v_max_att OR NEW.`Attempt_no` > v_max_att THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Maximum allowed attempts reached for this registration.';
  END IF;
END$$

CREATE TRIGGER `trg_attempt_bu_checks`
BEFORE UPDATE ON `ATTEMPT`
FOR EACH ROW
BEGIN
  DECLARE v_max_att INT;
  IF NEW.`PaperKey` <> OLD.`PaperKey` OR NEW.`RegisID` <> OLD.`RegisID` THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Assigned PaperKey and RegisID are fixed once the attempt is created.';
  END IF;
  IF NEW.`Attempt_no` <> OLD.`Attempt_no` THEN
    SELECT e.`Max_attempts` INTO v_max_att
    FROM `REGISTRATION` r
    JOIN `EXAMINATION` e ON e.`Exam_ID` = r.`Exam_ID`
    WHERE r.`RegisID` = NEW.`RegisID`;
    IF NEW.`Attempt_no` > v_max_att THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attempt_no cannot exceed examination Max_attempts.';
    END IF;
  END IF;
END$$

-- 9) Answer Membership, Bounds & MCQ Text Guards (BR-37, BR-38)
CREATE TRIGGER `trg_answer_bi_checks`
BEFORE INSERT ON `ANSWER`
FOR EACH ROW
BEGIN
  DECLARE v_paper_key INT DEFAULT NULL;
  DECLARE v_exam_id   INT DEFAULT NULL;
  DECLARE v_max_marks DECIMAL(5,2) DEFAULT NULL;

  SELECT a.`PaperKey`, p.`Exam_ID` INTO v_paper_key, v_exam_id
  FROM `ATTEMPT` a
  JOIN `PAPER` p ON p.`PaperKey` = a.`PaperKey`
  WHERE a.`Attempt_no` = NEW.`Attempt_no` AND a.`RegisID` = NEW.`RegisID`;

  IF v_paper_key IS NULL OR NOT EXISTS (
    SELECT 1 FROM `PAPER_QUESTION`
    WHERE `PaperKey` = v_paper_key AND `Quest_ID` = NEW.`Quest_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Answered question does not belong to the attempt assigned paper.';
  END IF;

  IF EXISTS (SELECT 1 FROM `MCQ` WHERE `Quest_ID` = NEW.`Quest_ID`) AND NEW.`AnswerText` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AnswerText must be NULL for multiple-choice questions.';
  END IF;

  IF NEW.`MarkAwarded` IS NOT NULL THEN
    SELECT `Marks` INTO v_max_marks FROM `EXAM_QUESTION_MARKS`
    WHERE `Exam_ID` = v_exam_id AND `Quest_ID` = NEW.`Quest_ID`;
    IF v_max_marks IS NULL OR NEW.`MarkAwarded` > v_max_marks THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MarkAwarded cannot exceed exam_question_marks.Marks.';
    END IF;
  END IF;
END$$

CREATE TRIGGER `trg_answer_bu_checks`
BEFORE UPDATE ON `ANSWER`
FOR EACH ROW
BEGIN
  DECLARE v_paper_key INT DEFAULT NULL;
  DECLARE v_exam_id   INT DEFAULT NULL;
  DECLARE v_max_marks DECIMAL(5,2) DEFAULT NULL;

  SELECT a.`PaperKey`, p.`Exam_ID` INTO v_paper_key, v_exam_id
  FROM `ATTEMPT` a
  JOIN `PAPER` p ON p.`PaperKey` = a.`PaperKey`
  WHERE a.`Attempt_no` = NEW.`Attempt_no` AND a.`RegisID` = NEW.`RegisID`;

  IF v_paper_key IS NULL OR NOT EXISTS (
    SELECT 1 FROM `PAPER_QUESTION`
    WHERE `PaperKey` = v_paper_key AND `Quest_ID` = NEW.`Quest_ID`
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Answered question does not belong to the attempt assigned paper.';
  END IF;

  IF EXISTS (SELECT 1 FROM `MCQ` WHERE `Quest_ID` = NEW.`Quest_ID`) AND NEW.`AnswerText` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AnswerText must remain NULL for multiple-choice questions.';
  END IF;

  IF NEW.`MarkAwarded` IS NOT NULL THEN
    SELECT `Marks` INTO v_max_marks FROM `EXAM_QUESTION_MARKS`
    WHERE `Exam_ID` = v_exam_id AND `Quest_ID` = NEW.`Quest_ID`;
    IF v_max_marks IS NULL OR NEW.`MarkAwarded` > v_max_marks THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MarkAwarded cannot exceed exam_question_marks.Marks.';
    END IF;
  END IF;
END$$

-- 10) select_table MCQ-Only & Option Question Consistency Guard (BR-40)
CREATE TRIGGER `trg_select_bi_mcq_only`
BEFORE INSERT ON `SELECT_TABLE`
FOR EACH ROW
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `MCQ` WHERE `Quest_ID` = NEW.`Quest_ID`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only MCQ questions may have entries in select_table.';
  END IF;
END$$

-- ============================================================================
-- STORED PROCEDURES WITH TRANSACTIONS (BR-07, BR-35, BR-41)
-- ============================================================================
CREATE PROCEDURE `sp_create_student` (
  IN p_user_id INT,
  IN p_first   VARCHAR(50),
  IN p_last    VARCHAR(50),
  IN p_dob     DATE,
  IN p_email   VARCHAR(100),
  IN p_hash    VARCHAR(255),
  IN p_year    INT,
  IN p_phone   VARCHAR(20)
)
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
    INSERT INTO `USER` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`)
    VALUES (p_user_id, p_first, p_last, p_dob, LOWER(TRIM(p_email)), p_hash);

    INSERT INTO `STUDENT` (`User_ID`, `Enrollment_year`)
    VALUES (p_user_id, p_year);

    IF p_phone IS NOT NULL AND CHAR_LENGTH(TRIM(p_phone)) > 0 THEN
      INSERT INTO `USER_PHONE` (`Phone`, `User_ID`) VALUES (TRIM(p_phone), p_user_id);
    END IF;
  COMMIT;
END$$

CREATE PROCEDURE `sp_create_instructor` (
  IN p_user_id INT,
  IN p_first   VARCHAR(50),
  IN p_last    VARCHAR(50),
  IN p_dob     DATE,
  IN p_email   VARCHAR(100),
  IN p_hash    VARCHAR(255),
  IN p_dept    VARCHAR(100),
  IN p_phone   VARCHAR(20)
)
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
    INSERT INTO `USER` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`)
    VALUES (p_user_id, p_first, p_last, p_dob, LOWER(TRIM(p_email)), p_hash);

    INSERT INTO `INSTRUCTOR` (`User_ID`, `Department`)
    VALUES (p_user_id, p_dept);

    IF p_phone IS NOT NULL AND CHAR_LENGTH(TRIM(p_phone)) > 0 THEN
      INSERT INTO `USER_PHONE` (`Phone`, `User_ID`) VALUES (TRIM(p_phone), p_user_id);
    END IF;
  COMMIT;
END$$

CREATE PROCEDURE `sp_start_attempt` (
  IN  p_regis_id   INT,
  IN  p_paper_key  INT,
  IN  p_start_time DATETIME,
  OUT p_attempt_no INT
)
BEGIN
  DECLARE v_exam_id INT;
  DECLARE v_max_att INT;
  DECLARE v_cur_cnt INT;
  DECLARE v_status  VARCHAR(10);

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
    -- Consistent lock order: examination -> registration to prevent deadlocks
    SELECT `Exam_ID` INTO v_exam_id
    FROM `REGISTRATION`
    WHERE `RegisID` = p_regis_id;

    SELECT `Max_attempts`, `Status` INTO v_max_att, v_status
    FROM `EXAMINATION`
    WHERE `Exam_ID` = v_exam_id
    FOR UPDATE;

    IF v_status <> 'PUBLISHED' THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Examination is not published; attempts are not allowed.';
    END IF;

    SELECT `Exam_ID` INTO v_exam_id
    FROM `REGISTRATION`
    WHERE `RegisID` = p_regis_id
    FOR UPDATE;

    SELECT COUNT(*), COALESCE(MAX(`Attempt_no`), 0) INTO v_cur_cnt, p_attempt_no
    FROM `ATTEMPT`
    WHERE `RegisID` = p_regis_id
    FOR UPDATE;

    IF v_cur_cnt >= v_max_att THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Max_attempts exceeded under serialized lock.';
    END IF;

    SET p_attempt_no = p_attempt_no + 1;

    INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`)
    VALUES (p_attempt_no, p_regis_id, p_start_time, NULL, p_paper_key);
  COMMIT;
END$$

CREATE PROCEDURE `sp_submit_mcq_answer` (
  IN p_regis_id   INT,
  IN p_attempt_no INT,
  IN p_quest_id   INT,
  IN p_option_no  INT
)
BEGIN
  DECLARE v_exam_id   INT;
  DECLARE v_end_time  DATETIME;
  DECLARE v_is_right  TINYINT;
  DECLARE v_max_marks DECIMAL(5,2);
  DECLARE v_awarded   DECIMAL(5,2);

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
    SELECT a.`EndTime`, p.`Exam_ID` INTO v_end_time, v_exam_id
    FROM `ATTEMPT` a
    JOIN `PAPER` p ON p.`PaperKey` = a.`PaperKey`
    WHERE a.`RegisID` = p_regis_id AND a.`Attempt_no` = p_attempt_no
    FOR UPDATE;

    IF v_end_time IS NOT NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot change MCQ response after attempt has been submitted.';
    END IF;

    SELECT `Bool_Correct` INTO v_is_right
    FROM `OPTIONS`
    WHERE `Quest_ID` = p_quest_id AND `Option_no` = p_option_no;

    SELECT `Marks` INTO v_max_marks
    FROM `EXAM_QUESTION_MARKS`
    WHERE `Exam_ID` = v_exam_id AND `Quest_ID` = p_quest_id;

    SET v_awarded = CASE WHEN v_is_right = 1 THEN v_max_marks ELSE 0.00 END;

    INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
    VALUES (p_quest_id, p_attempt_no, p_regis_id, NULL, v_awarded)
    ON DUPLICATE KEY UPDATE
      `AnswerText`  = NULL,
      `MarkAwarded` = VALUES(`MarkAwarded`);

    INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`)
    VALUES (p_option_no, p_quest_id, p_attempt_no, p_regis_id)
    ON DUPLICATE KEY UPDATE
      `Option_no` = VALUES(`Option_no`);
  COMMIT;
END$$

CREATE PROCEDURE `sp_finalize_attempt` (
  IN p_regis_id   INT,
  IN p_attempt_no INT,
  IN p_end_time   DATETIME
)
proc_body: BEGIN
  DECLARE v_paper_key INT DEFAULT NULL;
  DECLARE v_exam_id   INT DEFAULT NULL;
  DECLARE v_end_time  DATETIME DEFAULT NULL;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF p_end_time IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Submission EndTime cannot be NULL.';
  END IF;

  START TRANSACTION;
    SELECT `PaperKey`, `EndTime` INTO v_paper_key, v_end_time
    FROM `ATTEMPT`
    WHERE `RegisID` = p_regis_id AND `Attempt_no` = p_attempt_no
    FOR UPDATE;

    IF v_paper_key IS NULL THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attempt not found for finalization.';
    END IF;

    -- Idempotent submission (BR-41): if already submitted, preserve existing EndTime & answers
    IF v_end_time IS NOT NULL THEN
      COMMIT;
      LEAVE proc_body;
    END IF;

    SELECT `Exam_ID` INTO v_exam_id FROM `PAPER` WHERE `PaperKey` = v_paper_key;

    UPDATE `ATTEMPT`
    SET `EndTime` = p_end_time
    WHERE `RegisID` = p_regis_id AND `Attempt_no` = p_attempt_no;

    -- Auto-grade any pre-existing ungraded MCQ rows that have a selection (BR-42)
    UPDATE `ANSWER` an
    JOIN `SELECT_TABLE` st
      ON st.`Quest_ID` = an.`Quest_ID` AND st.`Attempt_no` = an.`Attempt_no` AND st.`RegisID` = an.`RegisID`
    JOIN `OPTIONS` o
      ON o.`Quest_ID` = st.`Quest_ID` AND o.`Option_no` = st.`Option_no`
    JOIN `EXAM_QUESTION_MARKS` eqm
      ON eqm.`Exam_ID` = v_exam_id AND eqm.`Quest_ID` = an.`Quest_ID`
    SET an.`MarkAwarded` = CASE WHEN o.`Bool_Correct` = 1 THEN eqm.`Marks` ELSE 0.00 END
    WHERE an.`RegisID` = p_regis_id
      AND an.`Attempt_no` = p_attempt_no
      AND an.`MarkAwarded` IS NULL;

    -- Award 0.00 to pre-existing blank answers while keeping non-blank short answers NULL (BR-42)
    UPDATE `ANSWER` an
    SET an.`MarkAwarded` = 0.00
    WHERE an.`RegisID` = p_regis_id
      AND an.`Attempt_no` = p_attempt_no
      AND an.`MarkAwarded` IS NULL
      AND (an.`AnswerText` IS NULL OR NOT REGEXP_LIKE(an.`AnswerText`, '[^[:space:]]'))
      AND NOT EXISTS (
        SELECT 1 FROM `SELECT_TABLE` st
        WHERE st.`Quest_ID`   = an.`Quest_ID`
          AND st.`Attempt_no` = an.`Attempt_no`
          AND st.`RegisID`    = an.`RegisID`
      );

    -- Materialize explicit 0.00 marks for any unanswered missing questions (BR-41, BR-42)
    INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`)
    SELECT pq.`Quest_ID`, p_attempt_no, p_regis_id, NULL, 0.00
    FROM `PAPER_QUESTION` pq
    WHERE pq.`PaperKey` = v_paper_key
      AND NOT EXISTS (
        SELECT 1 FROM `ANSWER` an
        WHERE an.`Quest_ID`   = pq.`Quest_ID`
          AND an.`Attempt_no` = p_attempt_no
          AND an.`RegisID`    = p_regis_id
      );
  COMMIT;
END$$

DELIMITER ;

-- ============================================================================
-- REALISTIC SEED DATA (>= 15 ROWS IN EVERY SINGLE ONE OF THE 21 TABLES)
-- Executed with ALL triggers and constraints active (zero disabled checks).
-- ============================================================================

INSERT INTO `USER` (`User_ID`, `FirstName`, `LastName`, `DOB`, `Email`, `Password_hash`) VALUES
  (1, 'Lan', 'Nguyen', '1982-03-14', 'inst01@hcmiu.edu.vn', 'sha256:inst_hash_01'),
  (2, 'Minh', 'Tran', '1980-07-22', 'inst02@hcmiu.edu.vn', 'sha256:inst_hash_02'),
  (3, 'Hoa', 'Le', '1985-11-02', 'inst03@hcmiu.edu.vn', 'sha256:inst_hash_03'),
  (4, 'Dung', 'Pham', '1979-05-19', 'inst04@hcmiu.edu.vn', 'sha256:inst_hash_04'),
  (5, 'Tuan', 'Vo', '1984-09-08', 'inst05@hcmiu.edu.vn', 'sha256:inst_hash_05'),
  (6, 'Thao', 'Hoang', '1987-01-27', 'inst06@hcmiu.edu.vn', 'sha256:inst_hash_06'),
  (7, 'Khoa', 'Dang', '1981-12-10', 'inst07@hcmiu.edu.vn', 'sha256:inst_hash_07'),
  (8, 'Huong', 'Bui', '1986-04-05', 'inst08@hcmiu.edu.vn', 'sha256:inst_hash_08'),
  (9, 'Nam', 'Do', '1983-06-30', 'inst09@hcmiu.edu.vn', 'sha256:inst_hash_09'),
  (10, 'Vy', 'Huynh', '1989-08-15', 'inst10@hcmiu.edu.vn', 'sha256:inst_hash_10'),
  (11, 'Son', 'Truong', '1978-10-12', 'inst11@hcmiu.edu.vn', 'sha256:inst_hash_11'),
  (12, 'Ngoc', 'Ly', '1990-02-18', 'inst12@hcmiu.edu.vn', 'sha256:inst_hash_12'),
  (13, 'Phuc', 'Ngo', '1985-07-07', 'inst13@hcmiu.edu.vn', 'sha256:inst_hash_13'),
  (14, 'Hanh', 'Duong', '1988-09-25', 'inst14@hcmiu.edu.vn', 'sha256:inst_hash_14'),
  (15, 'Kien', 'Trinh', '1982-11-30', 'inst15@hcmiu.edu.vn', 'sha256:inst_hash_15'),
  (101, 'An', 'Nguyen', '2005-01-12', 'sv101@student.hcmiu.edu.vn', 'sha256:stu_hash_101'),
  (102, 'Binh', 'Tran', '2005-03-21', 'sv102@student.hcmiu.edu.vn', 'sha256:stu_hash_102'),
  (103, 'Chi', 'Le', '2005-06-08', 'sv103@student.hcmiu.edu.vn', 'sha256:stu_hash_103'),
  (104, 'Dat', 'Pham', '2005-09-17', 'sv104@student.hcmiu.edu.vn', 'sha256:stu_hash_104'),
  (105, 'Em', 'Hoang', '2006-02-14', 'sv105@student.hcmiu.edu.vn', 'sha256:stu_hash_105'),
  (106, 'Giang', 'Vu', '2006-04-29', 'sv106@student.hcmiu.edu.vn', 'sha256:stu_hash_106'),
  (107, 'Hai', 'Dang', '2005-08-03', 'sv107@student.hcmiu.edu.vn', 'sha256:stu_hash_107'),
  (108, 'Khanh', 'Bui', '2006-11-11', 'sv108@student.hcmiu.edu.vn', 'sha256:stu_hash_108'),
  (109, 'Linh', 'Do', '2005-05-25', 'sv109@student.hcmiu.edu.vn', 'sha256:stu_hash_109'),
  (110, 'Manh', 'Nguyen', '2005-07-19', 'sv110@student.hcmiu.edu.vn', 'sha256:stu_hash_110'),
  (111, 'Ngan', 'Tran', '2006-10-04', 'sv111@student.hcmiu.edu.vn', 'sha256:stu_hash_111'),
  (112, 'Oanh', 'Vo', '2006-01-28', 'sv112@student.hcmiu.edu.vn', 'sha256:stu_hash_112'),
  (113, 'Phat', 'Nguyen', '2004-12-09', 'sv113@student.hcmiu.edu.vn', 'sha256:stu_hash_113'),
  (114, 'Quynh', 'Le', '2005-02-16', 'sv114@student.hcmiu.edu.vn', 'sha256:stu_hash_114'),
  (115, 'Son', 'Huynh', '2006-07-23', 'sv115@student.hcmiu.edu.vn', 'sha256:stu_hash_115'),
  (116, 'Trang', 'Pham', '2005-09-30', 'sv116@student.hcmiu.edu.vn', 'sha256:stu_hash_116'),
  (117, 'Uyen', 'Mai', '2006-03-11', 'sv117@student.hcmiu.edu.vn', 'sha256:stu_hash_117'),
  (118, 'Vinh', 'Ly', '2005-04-06', 'sv118@student.hcmiu.edu.vn', 'sha256:stu_hash_118'),
  (119, 'Xuan', 'Cao', '2006-08-19', 'sv119@student.hcmiu.edu.vn', 'sha256:stu_hash_119'),
  (120, 'Yen', 'Truong', '2005-11-27', 'sv120@student.hcmiu.edu.vn', 'sha256:stu_hash_120');
INSERT INTO `INSTRUCTOR` (`User_ID`, `Department`) VALUES
  (1, 'Computer Science'),
  (2, 'Computer Science'),
  (3, 'Software Engineering'),
  (4, 'Information Systems'),
  (5, 'Computer Engineering'),
  (6, 'Data Science'),
  (7, 'Computer Science'),
  (8, 'Software Engineering'),
  (9, 'Network Security'),
  (10, 'Data Science'),
  (11, 'Information Systems'),
  (12, 'Computer Science'),
  (13, 'Software Engineering'),
  (14, 'Network Security'),
  (15, 'Computer Engineering');
INSERT INTO `STUDENT` (`User_ID`, `Enrollment_year`) VALUES
  (101, 2023),
  (102, 2023),
  (103, 2023),
  (104, 2023),
  (105, 2024),
  (106, 2024),
  (107, 2023),
  (108, 2024),
  (109, 2023),
  (110, 2023),
  (111, 2024),
  (112, 2024),
  (113, 2022),
  (114, 2023),
  (115, 2024),
  (116, 2023),
  (117, 2024),
  (118, 2023),
  (119, 2024),
  (120, 2023);
INSERT INTO `USER_PHONE` (`Phone`, `User_ID`) VALUES
  ('+84901000001', 1),
  ('+84901000002', 2),
  ('+84901000003', 3),
  ('+84901000004', 4),
  ('+84901000005', 5),
  ('+84901000006', 6),
  ('+84901000007', 7),
  ('+84901000008', 8),
  ('+84901000009', 9),
  ('+84901000010', 10),
  ('+84901000011', 11),
  ('+84901000012', 12),
  ('+84901000013', 13),
  ('+84901000014', 14),
  ('+84901000015', 15),
  ('0912000101', 101),
  ('0912000102', 102),
  ('0912000103', 103),
  ('0912000104', 104),
  ('0912000105', 105),
  ('0912000106', 106),
  ('0912000107', 107),
  ('0912000108', 108),
  ('0912000109', 109),
  ('0912000110', 110),
  ('0912000111', 111),
  ('0912000112', 112),
  ('0912000113', 113),
  ('0912000114', 114),
  ('0912000115', 115),
  ('0912000116', 116),
  ('0912000117', 117),
  ('0912000118', 118),
  ('0912000119', 119),
  ('0912000120', 120),
  ('02837244001', 1),
  ('02837244002', 2),
  ('+84988888101', 101);

INSERT INTO `COURSE` (`Course_ID`, `CName`, `Credits`) VALUES
  (1, 'Principles of Database Management', 4),
  (2, 'Object-Oriented Programming', 4),
  (3, 'Data Structures and Algorithms', 4),
  (4, 'Computer Networks', 4),
  (5, 'Operating Systems', 4),
  (6, 'Software Engineering', 4),
  (7, 'Discrete Mathematics', 3),
  (8, 'Web Application Development', 4),
  (9, 'Artificial Intelligence', 3),
  (10, 'Machine Learning', 3),
  (11, 'Information Security', 3),
  (12, 'Cloud Computing', 3),
  (13, 'Mobile Application Development', 3),
  (14, 'Distributed Systems', 3),
  (15, 'Data Mining', 3);
INSERT INTO `REQUIRES` (`Prerequisite_ID`, `Advanced_ID`) VALUES
  (7, 3),
  (2, 3),
  (3, 1),
  (3, 5),
  (3, 9),
  (5, 4),
  (1, 8),
  (2, 6),
  (6, 13),
  (9, 10),
  (4, 11),
  (5, 14),
  (4, 12),
  (1, 15),
  (10, 15);
INSERT INTO `TOPIC` (`Topic_ID`, `Content`) VALUES
  (1, 'ER & EER Conceptual Modeling'),
  (2, 'Relational Algebra & Calculus'),
  (3, 'SQL Joins, Grouping & Subqueries'),
  (4, 'Functional Dependencies & BCNF Normalization'),
  (5, 'Transactions, ACID & Concurrency'),
  (6, 'OOP Encapsulation & Inheritance'),
  (7, 'Polymorphism & Interfaces'),
  (8, 'Trees, Heaps & Graph Traversal'),
  (9, 'Dynamic Programming & Sorting'),
  (10, 'TCP/IP, DNS & Routing Protocols'),
  (11, 'CPU Scheduling & Deadlocks'),
  (12, 'Software Architecture & Testing'),
  (13, 'Boolean Logic & Graph Theory'),
  (14, 'REST APIs & Database Integration'),
  (15, 'Search & Knowledge Representation'),
  (16, 'Supervised & Unsupervised Learning'),
  (17, 'Symmetric & Public-Key Cryptography'),
  (18, 'Distributed Consensus & Cloud Scaling');
INSERT INTO `COVERS` (`Course_ID`, `Topic_ID`) VALUES
  (1, 1),
  (1, 2),
  (1, 3),
  (1, 4),
  (2, 6),
  (2, 7),
  (3, 8),
  (3, 9),
  (4, 10),
  (5, 11),
  (5, 5),
  (6, 12),
  (7, 13),
  (8, 14),
  (8, 3),
  (9, 15),
  (10, 16),
  (11, 17),
  (12, 18),
  (13, 12),
  (13, 14),
  (14, 5),
  (14, 18),
  (15, 4),
  (15, 16);
INSERT INTO `TEACH` (`User_ID`, `Course_ID`, `Semester`) VALUES
  (1, 1, '2025-S2'),
  (1, 1, '2026-S1'),
  (2, 1, '2026-S1'),
  (2, 2, '2026-S1'),
  (3, 3, '2026-S1'),
  (4, 4, '2026-S1'),
  (5, 5, '2026-S1'),
  (6, 6, '2026-S1'),
  (7, 7, '2026-S1'),
  (8, 8, '2026-S1'),
  (9, 9, '2026-S1'),
  (10, 10, '2026-S1'),
  (11, 11, '2026-S1'),
  (12, 12, '2026-S1'),
  (13, 13, '2026-S1'),
  (14, 14, '2026-S1'),
  (15, 15, '2026-S1'),
  (3, 6, '2025-S1'),
  (4, 5, '2025-S2'),
  (6, 10, '2025-S2');

INSERT INTO `QUESTION` (`Quest_ID`, `Content`, `Difficulty`, `Topic_ID`, `User_ID`) VALUES
  (1, 'Which SQL clause filters grouped rows after aggregation?', 2, 3, 1),
  (2, 'Which normal form eliminates partial dependencies on composite keys?', 2, 4, 1),
  (3, 'In an EER diagram, what does a double line to a relationship represent?', 2, 1, 2),
  (4, 'Which relational algebra operator expresses division (for all)?', 4, 2, 2),
  (5, 'Which FD property proves a binary decomposition is lossless join?', 3, 4, 1),
  (6, 'Which SQL join retains unmatched rows from the left table?', 2, 3, 2),
  (7, 'What characterizes a weak entity set in EER modeling?', 2, 1, 1),
  (8, 'In BCNF, every nontrivial functional dependency X -> Y requires what?', 3, 4, 1),
  (9, 'Which OOP mechanism binds method calls at runtime?', 2, 7, 2),
  (10, 'What is the worst-case time complexity of Heap Sort?', 3, 9, 3),
  (11, 'Which protocol provides reliable connection-oriented byte streams?', 2, 10, 4),
  (12, 'Which scheduling policy can cause starvation without aging?', 3, 11, 5),
  (13, 'What does the ACID property Atomicity guarantee?', 2, 5, 5),
  (14, 'Which HTTP status code denotes a created resource?', 2, 14, 8),
  (15, 'Which search algorithm guarantees an optimal path with admissible heuristic?', 4, 15, 9),
  (16, 'Which metric handles imbalanced binary classification better than accuracy?', 3, 16, 10),
  (17, 'Which algorithm is asymmetric public-key encryption?', 2, 17, 11),
  (18, 'Which consensus protocol uses leader election and log replication?', 4, 18, 12),
  (19, 'In a simple undirected graph, the sum of vertex degrees equals what?', 2, 13, 7),
  (20, 'Which design pattern ensures only one database pool instance exists?', 2, 12, 6),
  (21, 'State the two conditions required for a candidate key.', 3, 4, 1),
  (22, 'Explain why Phone is mapped into a separate user_phone relation.', 2, 1, 2),
  (23, 'Write the SQL keyword pattern used for universal quantification (for all).', 4, 3, 1),
  (24, 'Define two-phase locking (2PL) in transaction management.', 4, 5, 5),
  (25, 'Differentiate method overloading from method overriding.', 2, 7, 2),
  (26, 'Explain how BFS detects shortest path in unweighted graphs.', 3, 8, 3),
  (27, 'Describe the three-way handshake in TCP connection setup.', 2, 10, 4),
  (28, 'List Coffman four necessary conditions for deadlock.', 3, 11, 5),
  (29, 'Compare unit testing and integration testing.', 2, 12, 6),
  (30, 'Prove by contradiction that sqrt(2) is irrational (outline key step).', 4, 13, 7),
  (31, 'Explain why PreparedStatement prevents SQL injection.', 2, 14, 8),
  (32, 'Distinguish heuristic evaluation function from path cost in A*.', 4, 15, 9),
  (33, 'Explain overfitting and two techniques to mitigate it.', 3, 16, 10),
  (34, 'Compare symmetric AES and asymmetric RSA key distribution.', 3, 17, 11),
  (35, 'Explain the CAP theorem trade-off during network partitions.', 4, 18, 12);
INSERT INTO `MCQ` (`Quest_ID`) VALUES
  (1),
  (2),
  (3),
  (4),
  (5),
  (6),
  (7),
  (8),
  (9),
  (10),
  (11),
  (12),
  (13),
  (14),
  (15),
  (16),
  (17),
  (18),
  (19),
  (20);
INSERT INTO `SHORT_ANSWER` (`Quest_ID`, `Answer_key`) VALUES
  (21, 'Superkey closure equals R and no proper subset is a superkey.'),
  (22, 'Phone is multivalued; extracting it preserves 1NF atomicity.'),
  (23, 'Double nested NOT EXISTS.'),
  (24, 'Growing phase acquires locks; shrinking phase releases locks.'),
  (25, 'Overloading differs by parameter signature; overriding replaces subclass behavior.'),
  (26, 'Queue visits vertices by non-decreasing edge distance.'),
  (27, 'SYN, SYN-ACK, ACK.'),
  (28, 'Mutual exclusion, hold and wait, no preemption, circular wait.'),
  (29, 'Unit tests isolate components; integration tests verify combined interfaces.'),
  (30, 'Assume p/q in lowest terms -> both p and q even -> contradiction.'),
  (31, 'SQL structure is compiled before binding parameter literals.'),
  (32, 'g(n) is cost so far; h(n) estimates remaining goal cost.'),
  (33, 'Model memorizes noise; use regularization or cross-validation.'),
  (34, 'AES uses shared secret key; RSA uses public/private key pair.'),
  (35, 'Partition forces choosing between Consistency and Availability.');
INSERT INTO `OPTIONS` (`Option_no`, `Quest_ID`, `Op_Content`, `Bool_Correct`) VALUES
  (1, 1, 'WHERE', 0),
  (2, 1, 'HAVING', 1),
  (3, 1, 'ORDER BY', 0),
  (4, 1, 'DISTINCT', 0),
  (1, 2, 'Second Normal Form (2NF)', 1),
  (2, 2, 'First Normal Form (1NF)', 0),
  (3, 2, 'Unnormalized Form', 0),
  (4, 2, 'Fifth Normal Form', 0),
  (1, 3, 'Optional participation', 0),
  (2, 3, 'Weak attribute', 0),
  (3, 3, 'Total participation', 1),
  (4, 3, 'Derived attribute', 0),
  (1, 4, 'Cartesian product', 0),
  (2, 4, 'Natural join', 0),
  (3, 4, 'Set union', 0),
  (4, 4, 'Relational division', 1),
  (1, 5, '(R1 intersect R2)+ contains R1 or R2', 1),
  (2, 5, 'Equal row counts', 0),
  (3, 5, 'Empty intersection', 0),
  (4, 5, 'Single attribute PK', 0),
  (1, 6, 'INNER JOIN', 0),
  (2, 6, 'LEFT OUTER JOIN', 1),
  (3, 6, 'CROSS JOIN', 0),
  (4, 6, 'NATURAL JOIN', 0),
  (1, 7, 'Has no attributes', 0),
  (2, 7, 'Always ternary', 0),
  (3, 7, 'Identified via owner entity plus partial key', 1),
  (4, 7, 'Never has FKs', 0),
  (1, 8, 'Y is prime', 0),
  (2, 8, 'X is a superkey', 1),
  (3, 8, 'X has size 1', 0),
  (4, 8, 'Y is numeric', 0),
  (1, 9, 'Option 1 for Q9 (Correct)', 1),
  (2, 9, 'Option 2 for Q9', 0),
  (3, 9, 'Option 3 for Q9', 0),
  (4, 9, 'Option 4 for Q9', 0),
  (1, 10, 'Option 1 for Q10', 0),
  (2, 10, 'Option 2 for Q10 (Correct)', 1),
  (3, 10, 'Option 3 for Q10', 0),
  (4, 10, 'Option 4 for Q10', 0),
  (1, 11, 'Option 1 for Q11', 0),
  (2, 11, 'Option 2 for Q11', 0),
  (3, 11, 'Option 3 for Q11 (Correct)', 1),
  (4, 11, 'Option 4 for Q11', 0),
  (1, 12, 'Option 1 for Q12', 0),
  (2, 12, 'Option 2 for Q12', 0),
  (3, 12, 'Option 3 for Q12', 0),
  (4, 12, 'Option 4 for Q12 (Correct)', 1),
  (1, 13, 'Option 1 for Q13 (Correct)', 1),
  (2, 13, 'Option 2 for Q13', 0),
  (3, 13, 'Option 3 for Q13', 0),
  (4, 13, 'Option 4 for Q13', 0),
  (1, 14, 'Option 1 for Q14', 0),
  (2, 14, 'Option 2 for Q14 (Correct)', 1),
  (3, 14, 'Option 3 for Q14', 0),
  (4, 14, 'Option 4 for Q14', 0),
  (1, 15, 'Option 1 for Q15', 0),
  (2, 15, 'Option 2 for Q15', 0),
  (3, 15, 'Option 3 for Q15 (Correct)', 1),
  (4, 15, 'Option 4 for Q15', 0),
  (1, 16, 'Option 1 for Q16', 0),
  (2, 16, 'Option 2 for Q16', 0),
  (3, 16, 'Option 3 for Q16', 0),
  (4, 16, 'Option 4 for Q16 (Correct)', 1),
  (1, 17, 'Option 1 for Q17 (Correct)', 1),
  (2, 17, 'Option 2 for Q17', 0),
  (3, 17, 'Option 3 for Q17', 0),
  (4, 17, 'Option 4 for Q17', 0),
  (1, 18, 'Option 1 for Q18', 0),
  (2, 18, 'Option 2 for Q18 (Correct)', 1),
  (3, 18, 'Option 3 for Q18', 0),
  (4, 18, 'Option 4 for Q18', 0),
  (1, 19, 'Option 1 for Q19', 0),
  (2, 19, 'Option 2 for Q19', 0),
  (3, 19, 'Option 3 for Q19 (Correct)', 1),
  (4, 19, 'Option 4 for Q19', 0),
  (1, 20, 'Option 1 for Q20', 0),
  (2, 20, 'Option 2 for Q20', 0),
  (3, 20, 'Option 3 for Q20', 0),
  (4, 20, 'Option 4 for Q20 (Correct)', 1);

INSERT INTO `EXAMINATION` (`Exam_ID`, `Title`, `Exam_date`, `Duration`, `Pass_mark`, `Max_attempts`, `Course_ID`, `Status`) VALUES
  (1, 'PDM Midterm Examination', '2026-10-20 09:00:00', 60, 50.00, 3, 1, 'PUBLISHED'),
  (2, 'PDM Normalization Quiz', '2026-11-10 09:00:00', 45, 60.00, 2, 1, 'PUBLISHED'),
  (3, 'PDM Final Comprehensive', '2026-12-15 09:00:00', 90, 50.00, 2, 1, 'PUBLISHED'),
  (4, 'OOP Midterm', '2026-10-22 13:30:00', 60, 50.00, 2, 2, 'PUBLISHED'),
  (5, 'DSA Midterm', '2026-10-24 08:00:00', 75, 50.00, 2, 3, 'PUBLISHED'),
  (6, 'Computer Networks Quiz', '2026-10-26 10:00:00', 45, 55.00, 2, 4, 'PUBLISHED'),
  (7, 'Operating Systems Midterm', '2026-10-28 14:00:00', 60, 50.00, 2, 5, 'PUBLISHED'),
  (8, 'Software Engineering Test', '2026-11-01 09:00:00', 60, 50.00, 2, 6, 'PUBLISHED'),
  (9, 'Discrete Math Exam', '2026-11-03 08:00:00', 90, 50.00, 1, 7, 'PUBLISHED'),
  (10, 'Web Dev Practical Quiz', '2026-11-05 15:00:00', 60, 60.00, 2, 8, 'PUBLISHED'),
  (11, 'AI Search Assessment', '2026-11-07 09:00:00', 60, 50.00, 2, 9, 'PUBLISHED'),
  (12, 'Machine Learning Exam', '2026-11-09 13:00:00', 75, 50.00, 2, 10, 'PUBLISHED'),
  (13, 'InfoSec Midterm', '2026-11-11 10:00:00', 60, 55.00, 2, 11, 'PUBLISHED'),
  (14, 'Cloud Architecture Quiz', '2026-11-13 14:00:00', 45, 50.00, 2, 12, 'PUBLISHED'),
  (15, 'Mobile Dev Evaluation', '2026-11-15 09:00:00', 60, 50.00, 2, 13, 'PUBLISHED');
INSERT INTO `PAPER` (`PaperKey`, `PaperID`, `Exam_ID`) VALUES
  (1, 1, 1),
  (2, 2, 1),
  (3, 1, 2),
  (4, 2, 2),
  (5, 1, 3),
  (6, 2, 3),
  (7, 1, 4),
  (8, 1, 5),
  (9, 1, 6),
  (10, 1, 7),
  (11, 1, 8),
  (12, 1, 9),
  (13, 1, 10),
  (14, 1, 11),
  (15, 1, 12),
  (16, 1, 13),
  (17, 1, 14),
  (18, 1, 15);
INSERT INTO `EXAM_QUESTION_MARKS` (`Exam_ID`, `Quest_ID`, `Marks`) VALUES
  (1, 1, 10.00),
  (1, 2, 10.00),
  (1, 3, 10.00),
  (1, 4, 10.00),
  (1, 21, 20.00),
  (1, 22, 20.00),
  (2, 1, 15.00),
  (2, 2, 10.00),
  (2, 4, 10.00),
  (2, 21, 15.00),
  (3, 2, 10.00),
  (3, 3, 10.00),
  (3, 5, 10.00),
  (3, 23, 20.00),
  (4, 9, 10.00),
  (4, 25, 20.00),
  (5, 10, 10.00),
  (5, 26, 20.00),
  (6, 11, 10.00),
  (6, 27, 20.00),
  (7, 12, 10.00),
  (7, 13, 10.00),
  (7, 28, 20.00),
  (8, 20, 10.00),
  (8, 29, 20.00),
  (9, 19, 10.00),
  (9, 30, 20.00),
  (10, 14, 10.00),
  (10, 31, 20.00),
  (11, 15, 10.00),
  (11, 32, 20.00),
  (12, 16, 10.00),
  (12, 33, 20.00),
  (13, 17, 10.00),
  (13, 34, 20.00),
  (14, 18, 10.00),
  (14, 35, 20.00),
  (15, 20, 10.00),
  (15, 29, 20.00);
INSERT INTO `PAPER_QUESTION` (`PaperKey`, `Quest_ID`, `QuestionOrder`) VALUES
  (1, 1, 1),
  (1, 2, 2),
  (1, 3, 3),
  (1, 4, 4),
  (1, 21, 5),
  (2, 1, 1),
  (2, 2, 2),
  (2, 3, 3),
  (2, 4, 4),
  (2, 22, 5),
  (3, 1, 1),
  (3, 2, 2),
  (3, 4, 3),
  (3, 21, 4),
  (4, 1, 1),
  (4, 2, 2),
  (4, 21, 3),
  (5, 2, 1),
  (5, 3, 2),
  (5, 5, 3),
  (5, 23, 4),
  (6, 2, 1),
  (6, 5, 2),
  (6, 23, 3),
  (7, 9, 1),
  (7, 25, 2),
  (8, 10, 1),
  (8, 26, 2),
  (9, 11, 1),
  (9, 27, 2),
  (10, 12, 1),
  (10, 13, 2),
  (10, 28, 3),
  (11, 20, 1),
  (11, 29, 2),
  (12, 19, 1),
  (12, 30, 2),
  (13, 14, 1),
  (13, 31, 2),
  (14, 15, 1),
  (14, 32, 2),
  (15, 16, 1),
  (15, 33, 2),
  (16, 17, 1),
  (16, 34, 2),
  (17, 18, 1),
  (17, 35, 2),
  (18, 20, 1),
  (18, 29, 2);

INSERT INTO `REGISTRATION` (`RegisID`, `RegisDate`, `User_ID`, `Exam_ID`) VALUES
  (1, '2026-10-15 10:01:00', 101, 1),
  (2, '2026-10-15 10:02:00', 102, 1),
  (3, '2026-10-15 10:03:00', 103, 1),
  (4, '2026-10-15 10:04:00', 104, 1),
  (5, '2026-10-15 10:05:00', 105, 1),
  (6, '2026-10-15 10:06:00', 106, 1),
  (7, '2026-10-15 10:07:00', 107, 1),
  (8, '2026-10-15 10:08:00', 108, 1),
  (9, '2026-10-15 10:09:00', 109, 1),
  (10, '2026-10-15 10:10:00', 110, 1),
  (11, '2026-10-15 10:11:00', 111, 1),
  (12, '2026-10-15 10:12:00', 112, 1),
  (13, '2026-11-05 11:01:00', 101, 2),
  (14, '2026-11-05 11:02:00', 102, 2),
  (15, '2026-11-05 11:03:00', 103, 2),
  (16, '2026-11-05 11:04:00', 104, 2),
  (17, '2026-12-01 09:01:00', 101, 3),
  (18, '2026-12-01 09:02:00', 102, 3),
  (19, '2026-12-01 09:03:00', 103, 3),
  (20, '2026-12-01 09:05:00', 105, 3),
  (21, '2026-10-18 08:00:00', 105, 4),
  (22, '2026-10-18 09:00:00', 115, 4),
  (23, '2026-10-18 08:00:00', 106, 5),
  (24, '2026-10-18 09:00:00', 111, 5),
  (25, '2026-10-18 08:00:00', 107, 6),
  (26, '2026-10-18 09:00:00', 112, 6),
  (27, '2026-10-18 08:00:00', 108, 7),
  (28, '2026-10-18 09:00:00', 113, 7),
  (29, '2026-10-18 08:00:00', 109, 8),
  (30, '2026-10-18 08:00:00', 110, 9),
  (31, '2026-10-18 08:00:00', 101, 10),
  (32, '2026-10-18 08:00:00', 102, 11),
  (33, '2026-10-18 08:00:00', 103, 12),
  (34, '2026-10-18 08:00:00', 104, 13),
  (35, '2026-10-18 08:00:00', 105, 14),
  (36, '2026-10-18 08:00:00', 106, 15);
INSERT INTO `ATTEMPT` (`Attempt_no`, `RegisID`, `StartTime`, `EndTime`, `PaperKey`) VALUES
  (1, 1, '2026-10-20 09:00:00', '2026-10-20 09:40:00', 1),
  (2, 1, '2026-10-20 10:00:00', '2026-10-20 10:41:00', 1),
  (1, 2, '2026-10-20 10:00:00', '2026-10-20 10:42:00', 1),
  (1, 3, '2026-10-20 10:00:00', '2026-10-20 10:43:00', 1),
  (1, 4, '2026-10-20 10:00:00', '2026-10-20 10:44:00', 1),
  (1, 5, '2026-10-20 10:00:00', '2026-10-20 10:45:00', 1),
  (1, 6, '2026-10-20 10:00:00', '2026-10-20 10:46:00', 2),
  (1, 7, '2026-10-20 10:00:00', '2026-10-20 10:47:00', 2),
  (1, 8, '2026-10-20 10:00:00', '2026-10-20 10:48:00', 2),
  (1, 9, '2026-10-20 10:00:00', '2026-10-20 10:49:00', 2),
  (1, 10, '2026-10-20 10:00:00', '2026-10-20 10:50:00', 2),
  (1, 11, '2026-10-20 10:50:00', NULL, 1),
  (1, 13, '2026-11-10 09:05:00', '2026-11-10 09:35:00', 3),
  (1, 14, '2026-11-10 09:05:00', '2026-11-10 09:36:00', 3),
  (1, 15, '2026-11-10 09:05:00', '2026-11-10 09:37:00', 4),
  (1, 16, '2026-11-10 09:05:00', '2026-11-10 09:38:00', 4),
  (1, 17, '2026-12-15 09:05:00', '2026-12-15 10:10:00', 5),
  (1, 18, '2026-12-15 09:05:00', '2026-12-15 10:11:00', 5),
  (1, 19, '2026-12-15 09:05:00', '2026-12-15 10:12:00', 6),
  (1, 21, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 7),
  (1, 22, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 7),
  (1, 23, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 8),
  (1, 24, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 8),
  (1, 25, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 9),
  (1, 26, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 9),
  (1, 27, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 10),
  (1, 28, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 10),
  (1, 29, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 11),
  (1, 30, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 12),
  (1, 31, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 13),
  (1, 32, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 14),
  (1, 33, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 15),
  (1, 34, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 16),
  (1, 35, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 17),
  (1, 36, '2026-11-12 09:00:00', '2026-11-12 09:45:00', 18);
INSERT INTO `ANSWER` (`Quest_ID`, `Attempt_no`, `RegisID`, `AnswerText`, `MarkAwarded`) VALUES
  (1, 1, 1, NULL, 0.00),
  (2, 1, 1, NULL, 0.00),
  (3, 1, 1, NULL, 0.00),
  (4, 1, 1, NULL, 0.00),
  (21, 1, 1, 'Student response for Q21 on R1', 12.00),
  (1, 2, 1, NULL, 0.00),
  (2, 2, 1, NULL, 0.00),
  (3, 2, 1, NULL, 0.00),
  (4, 2, 1, NULL, 0.00),
  (21, 2, 1, 'Student response for Q21 on R1', 16.00),
  (1, 1, 2, NULL, 0.00),
  (2, 1, 2, NULL, 0.00),
  (3, 1, 2, NULL, 0.00),
  (4, 1, 2, NULL, 0.00),
  (21, 1, 2, 'Student response for Q21 on R2', 16.00),
  (1, 1, 3, NULL, 0.00),
  (2, 1, 3, NULL, 0.00),
  (3, 1, 3, NULL, 10.00),
  (4, 1, 3, NULL, 0.00),
  (21, 1, 3, 'Student response for Q21 on R3', 16.00),
  (1, 1, 4, NULL, 0.00),
  (2, 1, 4, NULL, 0.00),
  (3, 1, 4, NULL, 10.00),
  (4, 1, 4, NULL, 0.00),
  (21, 1, 4, 'Student response for Q21 on R4', 16.00),
  (1, 1, 5, NULL, 0.00),
  (2, 1, 5, NULL, 0.00),
  (3, 1, 5, NULL, 10.00),
  (4, 1, 5, NULL, 0.00),
  (21, 1, 5, 'Student response for Q21 on R5', 16.00),
  (1, 1, 6, NULL, 0.00),
  (2, 1, 6, NULL, 0.00),
  (3, 1, 6, NULL, 10.00),
  (4, 1, 6, NULL, 0.00),
  (22, 1, 6, 'Student response for Q22 on R6', 16.00),
  (1, 1, 7, NULL, 0.00),
  (2, 1, 7, NULL, 0.00),
  (3, 1, 7, NULL, 10.00),
  (4, 1, 7, NULL, 0.00),
  (22, 1, 7, 'Student response for Q22 on R7', 16.00),
  (1, 1, 8, NULL, 0.00),
  (2, 1, 8, NULL, 10.00),
  (3, 1, 8, NULL, 10.00),
  (4, 1, 8, NULL, 0.00),
  (22, 1, 8, 'Student response for Q22 on R8', 16.00),
  (1, 1, 9, NULL, 10.00),
  (2, 1, 9, NULL, 10.00),
  (3, 1, 9, NULL, 10.00),
  (4, 1, 9, NULL, 0.00),
  (22, 1, 9, 'Student response for Q22 on R9', 16.00),
  (1, 1, 10, NULL, 10.00),
  (2, 1, 10, NULL, 10.00),
  (3, 1, 10, NULL, 10.00),
  (4, 1, 10, NULL, 10.00),
  (22, 1, 10, 'Student response for Q22 on R10', 16.00),
  (1, 1, 11, NULL, 10.00),
  (21, 1, 11, 'Draft short answer not graded yet', NULL),
  (1, 1, 13, NULL, 15.00),
  (2, 1, 13, NULL, 10.00),
  (4, 1, 13, NULL, 10.00),
  (21, 1, 13, 'Student response for Q21 on R13', 14.00),
  (1, 1, 14, NULL, 15.00),
  (2, 1, 14, NULL, 10.00),
  (4, 1, 14, NULL, 10.00),
  (21, 1, 14, 'Student response for Q21 on R14', 14.00),
  (1, 1, 15, NULL, 0.00),
  (2, 1, 15, NULL, 10.00),
  (21, 1, 15, 'Student response for Q21 on R15', 14.00),
  (1, 1, 16, NULL, 0.00),
  (2, 1, 16, NULL, 10.00),
  (21, 1, 16, 'Student response for Q21 on R16', 14.00),
  (2, 1, 17, NULL, 10.00),
  (3, 1, 17, NULL, 10.00),
  (5, 1, 17, NULL, 10.00),
  (23, 1, 17, 'Student response for Q23 on R17', 18.00),
  (2, 1, 18, NULL, 10.00),
  (3, 1, 18, NULL, 10.00),
  (5, 1, 18, NULL, 10.00),
  (23, 1, 18, 'Student response for Q23 on R18', 18.00),
  (2, 1, 19, NULL, 10.00),
  (5, 1, 19, NULL, 10.00),
  (23, 1, 19, 'Student response for Q23 on R19', 18.00),
  (9, 1, 21, NULL, 10.00),
  (25, 1, 21, 'Student response for Q25 on R21', 17.00),
  (9, 1, 22, NULL, 10.00),
  (25, 1, 22, 'Student response for Q25 on R22', 17.00),
  (10, 1, 23, NULL, 10.00),
  (26, 1, 23, 'Student response for Q26 on R23', 17.00),
  (10, 1, 24, NULL, 10.00),
  (26, 1, 24, 'Student response for Q26 on R24', 17.00),
  (11, 1, 25, NULL, 10.00),
  (27, 1, 25, 'Student response for Q27 on R25', 17.00),
  (11, 1, 26, NULL, 10.00),
  (27, 1, 26, 'Student response for Q27 on R26', 17.00),
  (12, 1, 27, NULL, 10.00),
  (13, 1, 27, NULL, 10.00),
  (28, 1, 27, 'Student response for Q28 on R27', 17.00),
  (12, 1, 28, NULL, 10.00),
  (13, 1, 28, NULL, 10.00),
  (28, 1, 28, 'Student response for Q28 on R28', 17.00),
  (20, 1, 29, NULL, 10.00),
  (29, 1, 29, 'Student response for Q29 on R29', 17.00),
  (19, 1, 30, NULL, 10.00),
  (30, 1, 30, 'Student response for Q30 on R30', 17.00),
  (14, 1, 31, NULL, 10.00),
  (31, 1, 31, 'Student response for Q31 on R31', 17.00),
  (15, 1, 32, NULL, 10.00),
  (32, 1, 32, 'Student response for Q32 on R32', 17.00),
  (16, 1, 33, NULL, 10.00),
  (33, 1, 33, 'Student response for Q33 on R33', 17.00),
  (17, 1, 34, NULL, 10.00),
  (34, 1, 34, 'Student response for Q34 on R34', 17.00),
  (18, 1, 35, NULL, 10.00),
  (35, 1, 35, 'Student response for Q35 on R35', 17.00),
  (20, 1, 36, NULL, 10.00),
  (29, 1, 36, 'Student response for Q29 on R36', 17.00);
INSERT INTO `SELECT_TABLE` (`Option_no`, `Quest_ID`, `Attempt_no`, `RegisID`) VALUES
  (1, 1, 1, 1),
  (2, 2, 1, 1),
  (1, 3, 1, 1),
  (1, 4, 1, 1),
  (1, 1, 2, 1),
  (2, 2, 2, 1),
  (1, 3, 2, 1),
  (1, 4, 2, 1),
  (1, 1, 1, 2),
  (2, 2, 1, 2),
  (1, 3, 1, 2),
  (1, 4, 1, 2),
  (1, 1, 1, 3),
  (2, 2, 1, 3),
  (3, 3, 1, 3),
  (1, 4, 1, 3),
  (1, 1, 1, 4),
  (2, 2, 1, 4),
  (3, 3, 1, 4),
  (1, 4, 1, 4),
  (1, 1, 1, 5),
  (2, 2, 1, 5),
  (3, 3, 1, 5),
  (1, 4, 1, 5),
  (1, 1, 1, 6),
  (2, 2, 1, 6),
  (3, 3, 1, 6),
  (1, 4, 1, 6),
  (1, 1, 1, 7),
  (2, 2, 1, 7),
  (3, 3, 1, 7),
  (1, 4, 1, 7),
  (1, 1, 1, 8),
  (1, 2, 1, 8),
  (3, 3, 1, 8),
  (1, 4, 1, 8),
  (2, 1, 1, 9),
  (1, 2, 1, 9),
  (3, 3, 1, 9),
  (1, 4, 1, 9),
  (2, 1, 1, 10),
  (1, 2, 1, 10),
  (3, 3, 1, 10),
  (4, 4, 1, 10),
  (2, 1, 1, 11),
  (2, 1, 1, 13),
  (1, 2, 1, 13),
  (4, 4, 1, 13),
  (2, 1, 1, 14),
  (1, 2, 1, 14),
  (4, 4, 1, 14),
  (1, 1, 1, 15),
  (1, 2, 1, 15),
  (1, 1, 1, 16),
  (1, 2, 1, 16),
  (1, 2, 1, 17),
  (3, 3, 1, 17),
  (1, 5, 1, 17),
  (1, 2, 1, 18),
  (3, 3, 1, 18),
  (1, 5, 1, 18),
  (1, 2, 1, 19),
  (1, 5, 1, 19),
  (1, 9, 1, 21),
  (1, 9, 1, 22),
  (2, 10, 1, 23),
  (2, 10, 1, 24),
  (3, 11, 1, 25),
  (3, 11, 1, 26),
  (4, 12, 1, 27),
  (1, 13, 1, 27),
  (4, 12, 1, 28),
  (1, 13, 1, 28),
  (4, 20, 1, 29),
  (3, 19, 1, 30),
  (2, 14, 1, 31),
  (3, 15, 1, 32),
  (4, 16, 1, 33),
  (1, 17, 1, 34),
  (2, 18, 1, 35),
  (4, 20, 1, 36);
