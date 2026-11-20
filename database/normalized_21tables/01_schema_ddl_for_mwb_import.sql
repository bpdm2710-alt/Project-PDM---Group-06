-- ============================================================================
-- IT079IU - Principles of Database Management (Sem 1, 2026-2027)
-- Group 06 | Topic 20: Online Examination System
-- Target DBMS: MySQL 8.0.16+ / MySQL 8.4 (InnoDB, utf8mb4_0900_ai_ci)
-- Schema Design: 21 Normalized Relations (All in BCNF; Business Rules Master v2 BR-01..BR-50)
-- Dialect Notes (per Assignment Sec 2.3):
--   * REGEXP_LIKE() and named CHECK constraints require MySQL 8.0.16+.
--   * 'c' match_type in REGEXP_LIKE enforces case-sensitive Semester format.
--   * utf8mb4_0900_ai_ci provides case-insensitive Email uniqueness.
--   * AUTO_INCREMENT is MySQL syntax for surrogate PaperKey.
-- ============================================================================

DROP DATABASE IF EXISTS `online_examination`;
CREATE DATABASE `online_examination`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `online_examination`;

-- ----------------------------------------------------------------------------
-- 1. user (Superclass; Email is an enforced alternate candidate key)
-- ----------------------------------------------------------------------------
CREATE TABLE `user` (
  `User_ID`       INT          NOT NULL,
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
-- 2. user_phone (1NF extraction of multivalued Phone; VARCHAR preserves '+' & 0)
-- ----------------------------------------------------------------------------
CREATE TABLE `user_phone` (
  `Phone`   VARCHAR(20) NOT NULL,
  `User_ID` INT         NOT NULL,
  CONSTRAINT `pk_user_phone` PRIMARY KEY (`Phone`, `User_ID`),
  CONSTRAINT `chk_user_phone_nonempty` CHECK (CHAR_LENGTH(TRIM(`Phone`)) >= 7),
  CONSTRAINT `fk_user_phone_user`
    FOREIGN KEY (`User_ID`) REFERENCES `user` (`User_ID`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 3. student (ISA subtype of user; total + disjoint guarded by procs/triggers)
-- ----------------------------------------------------------------------------
CREATE TABLE `student` (
  `User_ID`         INT NOT NULL,
  `Enrollment_year` INT NOT NULL,
  CONSTRAINT `pk_student` PRIMARY KEY (`User_ID`),
  CONSTRAINT `chk_student_enroll_year` CHECK (`Enrollment_year` BETWEEN 1990 AND 2100),
  CONSTRAINT `fk_student_user`
    FOREIGN KEY (`User_ID`) REFERENCES `user` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. instructor (ISA subtype of user; total + disjoint guarded by procs/triggers)
-- ----------------------------------------------------------------------------
CREATE TABLE `instructor` (
  `User_ID`    INT          NOT NULL,
  `Department` VARCHAR(100) NOT NULL,
  CONSTRAINT `pk_instructor` PRIMARY KEY (`User_ID`),
  CONSTRAINT `chk_instructor_dept_nonempty` CHECK (CHAR_LENGTH(TRIM(`Department`)) > 0),
  CONSTRAINT `fk_instructor_user`
    FOREIGN KEY (`User_ID`) REFERENCES `user` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 5. course
-- ----------------------------------------------------------------------------
CREATE TABLE `course` (
  `Course_ID` INT          NOT NULL,
  `CName`     VARCHAR(100) NOT NULL,
  `Credits`   INT          NOT NULL,
  CONSTRAINT `pk_course` PRIMARY KEY (`Course_ID`),
  CONSTRAINT `chk_course_name_nonempty` CHECK (CHAR_LENGTH(TRIM(`CName`)) > 0),
  CONSTRAINT `chk_course_credits_pos`   CHECK (`Credits` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 6. requires (Course prerequisite M:N self-relationship with explicit roles)
-- ----------------------------------------------------------------------------
CREATE TABLE `requires` (
  `Prerequisite_ID` INT NOT NULL,
  `Advanced_ID`     INT NOT NULL,
  CONSTRAINT `pk_requires` PRIMARY KEY (`Prerequisite_ID`, `Advanced_ID`),
  CONSTRAINT `chk_requires_no_self` CHECK (`Prerequisite_ID` <> `Advanced_ID`),
  CONSTRAINT `fk_requires_prereq`
    FOREIGN KEY (`Prerequisite_ID`) REFERENCES `course` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_requires_advanced`
    FOREIGN KEY (`Advanced_ID`) REFERENCES `course` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 7. topic
-- ----------------------------------------------------------------------------
CREATE TABLE `topic` (
  `Topic_ID` INT  NOT NULL,
  `Content`  TEXT NOT NULL,
  CONSTRAINT `pk_topic` PRIMARY KEY (`Topic_ID`),
  CONSTRAINT `chk_topic_content_nonempty` CHECK (CHAR_LENGTH(TRIM(`Content`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 8. covers (Course <-> Topic M:N relationship)
-- ----------------------------------------------------------------------------
CREATE TABLE `covers` (
  `Course_ID` INT NOT NULL,
  `Topic_ID`  INT NOT NULL,
  CONSTRAINT `pk_covers` PRIMARY KEY (`Course_ID`, `Topic_ID`),
  CONSTRAINT `fk_covers_course`
    FOREIGN KEY (`Course_ID`) REFERENCES `course` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_covers_topic`
    FOREIGN KEY (`Topic_ID`) REFERENCES `topic` (`Topic_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 9. teach (Instructor <-> Course M:N with Semester in PK per Decision #3)
-- ----------------------------------------------------------------------------
CREATE TABLE `teach` (
  `User_ID`   INT         NOT NULL,
  `Course_ID` INT         NOT NULL,
  `Semester`  VARCHAR(15) NOT NULL,
  CONSTRAINT `pk_teach` PRIMARY KEY (`User_ID`, `Course_ID`, `Semester`),
  CONSTRAINT `chk_teach_semester_fmt` CHECK (REGEXP_LIKE(`Semester`, '^20[0-9]{2}-(S[12]|SU)$', 'c')),
  CONSTRAINT `fk_teach_instructor`
    FOREIGN KEY (`User_ID`) REFERENCES `instructor` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_teach_course`
    FOREIGN KEY (`Course_ID`) REFERENCES `course` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 10. question (Superclass; Topic_ID and authoring Instructor User_ID)
-- ----------------------------------------------------------------------------
CREATE TABLE `question` (
  `Quest_ID`   INT  NOT NULL,
  `Content`    TEXT NOT NULL,
  `Difficulty` INT  NOT NULL,
  `Topic_ID`   INT  NOT NULL,
  `User_ID`    INT  NOT NULL,
  CONSTRAINT `pk_question` PRIMARY KEY (`Quest_ID`),
  CONSTRAINT `chk_question_content_nonempty` CHECK (CHAR_LENGTH(TRIM(`Content`)) > 0),
  CONSTRAINT `chk_question_difficulty`       CHECK (`Difficulty` BETWEEN 1 AND 5),
  CONSTRAINT `fk_question_topic`
    FOREIGN KEY (`Topic_ID`) REFERENCES `topic` (`Topic_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_question_instructor`
    FOREIGN KEY (`User_ID`) REFERENCES `instructor` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 11. mcq (ISA subtype of question)
-- ----------------------------------------------------------------------------
CREATE TABLE `mcq` (
  `Quest_ID` INT NOT NULL,
  CONSTRAINT `pk_mcq` PRIMARY KEY (`Quest_ID`),
  CONSTRAINT `fk_mcq_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `question` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 12. short_answer (ISA subtype of question)
-- ----------------------------------------------------------------------------
CREATE TABLE `short_answer` (
  `Quest_ID`   INT  NOT NULL,
  `Answer_key` TEXT NOT NULL,
  CONSTRAINT `pk_short_answer` PRIMARY KEY (`Quest_ID`),
  CONSTRAINT `chk_short_answer_key_nonempty` CHECK (CHAR_LENGTH(TRIM(`Answer_key`)) > 0),
  CONSTRAINT `fk_short_answer_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `question` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 13. options (Weak entity of mcq; Bool_Correct uses standard TINYINT + CHECK)
-- ----------------------------------------------------------------------------
CREATE TABLE `options` (
  `Option_no`    INT     NOT NULL,
  `Quest_ID`     INT     NOT NULL,
  `Op_Content`   TEXT    NOT NULL,
  `Bool_Correct` TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT `pk_options` PRIMARY KEY (`Option_no`, `Quest_ID`),
  CONSTRAINT `chk_options_no_pos`      CHECK (`Option_no` >= 1),
  CONSTRAINT `chk_options_content`     CHECK (CHAR_LENGTH(TRIM(`Op_Content`)) > 0),
  CONSTRAINT `chk_options_bool_domain` CHECK (`Bool_Correct` IN (0, 1)),
  CONSTRAINT `fk_options_mcq`
    FOREIGN KEY (`Quest_ID`) REFERENCES `mcq` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 14. examination (Pass_mark stored as percentage 0.00 - 100.00 per Decision #9)
-- ----------------------------------------------------------------------------
CREATE TABLE `examination` (
  `Exam_ID`      INT           NOT NULL,
  `Title`        VARCHAR(150)  NOT NULL,
  `Exam_date`    DATETIME      NOT NULL,
  `Duration`     INT           NOT NULL,
  `Pass_mark`    DECIMAL(5,2)  NOT NULL,
  `Max_attempts` INT           NOT NULL DEFAULT 1,
  `Course_ID`    INT           NOT NULL,
  CONSTRAINT `pk_examination` PRIMARY KEY (`Exam_ID`),
  CONSTRAINT `chk_exam_title_nonempty` CHECK (CHAR_LENGTH(TRIM(`Title`)) > 0),
  CONSTRAINT `chk_exam_duration_pos`   CHECK (`Duration` > 0),
  CONSTRAINT `chk_exam_pass_mark_pct`  CHECK (`Pass_mark` BETWEEN 0.00 AND 100.00),
  CONSTRAINT `chk_exam_max_attempts`   CHECK (`Max_attempts` >= 1),
  CONSTRAINT `fk_examination_course`
    FOREIGN KEY (`Course_ID`) REFERENCES `course` (`Course_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 15. paper (Surrogate PaperKey PK + UNIQUE(Exam_ID, PaperID) per Decision #2 B1)
-- ----------------------------------------------------------------------------
CREATE TABLE `paper` (
  `PaperKey` INT NOT NULL AUTO_INCREMENT,
  `PaperID`  INT NOT NULL,
  `Exam_ID`  INT NOT NULL,
  CONSTRAINT `pk_paper` PRIMARY KEY (`PaperKey`),
  CONSTRAINT `uq_paper_exam_paperid` UNIQUE (`Exam_ID`, `PaperID`),
  CONSTRAINT `chk_paper_id_pos` CHECK (`PaperID` >= 1),
  CONSTRAINT `fk_paper_examination`
    FOREIGN KEY (`Exam_ID`) REFERENCES `examination` (`Exam_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 16. exam_question_marks (Marks per Exam_ID + Quest_ID per Topic 20 / #7B)
-- ----------------------------------------------------------------------------
CREATE TABLE `exam_question_marks` (
  `Exam_ID`  INT          NOT NULL,
  `Quest_ID` INT          NOT NULL,
  `Marks`    DECIMAL(5,2) NOT NULL,
  CONSTRAINT `pk_exam_question_marks` PRIMARY KEY (`Exam_ID`, `Quest_ID`),
  CONSTRAINT `chk_eqm_marks_pos` CHECK (`Marks` > 0.00),
  CONSTRAINT `fk_eqm_examination`
    FOREIGN KEY (`Exam_ID`) REFERENCES `examination` (`Exam_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_eqm_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `question` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 17. paper_question (Layout and question ordering per paper; BCNF)
-- ----------------------------------------------------------------------------
CREATE TABLE `paper_question` (
  `PaperKey`      INT NOT NULL,
  `Quest_ID`      INT NOT NULL,
  `QuestionOrder` INT NOT NULL,
  CONSTRAINT `pk_paper_question` PRIMARY KEY (`PaperKey`, `Quest_ID`),
  CONSTRAINT `uq_pq_paper_order` UNIQUE (`PaperKey`, `QuestionOrder`),
  CONSTRAINT `chk_pq_order_pos`  CHECK (`QuestionOrder` >= 1),
  CONSTRAINT `fk_pq_paper`
    FOREIGN KEY (`PaperKey`) REFERENCES `paper` (`PaperKey`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_pq_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `question` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 18. registration (One registration per student-examination pair)
-- ----------------------------------------------------------------------------
CREATE TABLE `registration` (
  `RegisID`   INT      NOT NULL,
  `RegisDate` DATETIME NOT NULL,
  `User_ID`   INT      NOT NULL,
  `Exam_ID`   INT      NOT NULL,
  CONSTRAINT `pk_registration` PRIMARY KEY (`RegisID`),
  CONSTRAINT `uq_registration_student_exam` UNIQUE (`User_ID`, `Exam_ID`),
  CONSTRAINT `fk_registration_student`
    FOREIGN KEY (`User_ID`) REFERENCES `student` (`User_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_registration_exam`
    FOREIGN KEY (`Exam_ID`) REFERENCES `examination` (`Exam_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 19. attempt (Exam_ID removed -> BCNF; EndTime NULL = in-progress attempt)
-- ----------------------------------------------------------------------------
CREATE TABLE `attempt` (
  `Attempt_no` INT      NOT NULL,
  `RegisID`    INT      NOT NULL,
  `StartTime`  DATETIME NOT NULL,
  `EndTime`    DATETIME NULL DEFAULT NULL,
  `PaperKey`   INT      NOT NULL,
  CONSTRAINT `pk_attempt` PRIMARY KEY (`Attempt_no`, `RegisID`),
  CONSTRAINT `chk_attempt_no_pos`    CHECK (`Attempt_no` >= 1),
  CONSTRAINT `chk_attempt_time_order` CHECK (`EndTime` IS NULL OR `EndTime` >= `StartTime`),
  CONSTRAINT `fk_attempt_registration`
    FOREIGN KEY (`RegisID`) REFERENCES `registration` (`RegisID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_attempt_paper`
    FOREIGN KEY (`PaperKey`) REFERENCES `paper` (`PaperKey`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 20. answer (NULL AnswerText for MCQ/blank; NULL MarkAwarded = ungraded)
-- ----------------------------------------------------------------------------
CREATE TABLE `answer` (
  `Quest_ID`    INT          NOT NULL,
  `Attempt_no`  INT          NOT NULL,
  `RegisID`     INT          NOT NULL,
  `AnswerText`  TEXT         NULL DEFAULT NULL,
  `MarkAwarded` DECIMAL(5,2) NULL DEFAULT NULL,
  CONSTRAINT `pk_answer` PRIMARY KEY (`Quest_ID`, `Attempt_no`, `RegisID`),
  CONSTRAINT `chk_answer_mark_nonneg` CHECK (`MarkAwarded` IS NULL OR `MarkAwarded` >= 0.00),
  CONSTRAINT `fk_answer_question`
    FOREIGN KEY (`Quest_ID`) REFERENCES `question` (`Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_answer_attempt`
    FOREIGN KEY (`Attempt_no`, `RegisID`) REFERENCES `attempt` (`Attempt_no`, `RegisID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 21. select_table (Single-select MCQ PK = (Quest_ID, Attempt_no, RegisID))
-- ----------------------------------------------------------------------------
CREATE TABLE `select_table` (
  `Option_no`  INT NOT NULL,
  `Quest_ID`   INT NOT NULL,
  `Attempt_no` INT NOT NULL,
  `RegisID`    INT NOT NULL,
  CONSTRAINT `pk_select_table` PRIMARY KEY (`Quest_ID`, `Attempt_no`, `RegisID`),
  CONSTRAINT `fk_select_option`
    FOREIGN KEY (`Option_no`, `Quest_ID`) REFERENCES `options` (`Option_no`, `Quest_ID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_select_answer`
    FOREIGN KEY (`Quest_ID`, `Attempt_no`, `RegisID`) REFERENCES `answer` (`Quest_ID`, `Attempt_no`, `RegisID`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- PURPOSEFUL SECONDARY INDEXES (Assignment Sec 2.3 requirement)
-- ============================================================================
-- Speeds up Instructor Question Bank filtering by topic & difficulty:
CREATE INDEX `idx_question_topic_diff`   ON `question` (`Topic_ID`, `Difficulty`);
-- Speeds up Student "My Exams" / course exam schedule lookups:
CREATE INDEX `idx_examination_course_date` ON `examination` (`Course_ID`, `Exam_date`);
-- Speeds up Exam->Registration->Attempt analytical joins (Topic 20 Queries #3-#5):
CREATE INDEX `idx_registration_exam_user` ON `registration` (`Exam_ID`, `User_ID`);
CREATE INDEX `idx_attempt_paper`          ON `attempt` (`PaperKey`);
-- Speeds up retrieving all answers for a single attempt (Query #2 & Results screen):
CREATE INDEX `idx_answer_attempt`         ON `answer` (`RegisID`, `Attempt_no`);
