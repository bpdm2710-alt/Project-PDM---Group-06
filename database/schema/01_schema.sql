-- =====================================================================
-- Online_Examination - dung lai schema 20 bang (baseline, ten bang VIET HOA)
-- Khop ten bang/cot voi code Java hien tai + 01_seed_test.sql.
--
-- !!! CANH BAO: file nay DROP ca database Online_Examination roi tao lai.
-- !!! Toan bo bang + du lieu cua CA NHOM tren Aiven se bi xoa.
-- !!! Chi chay khi chac chan, hoac doi ten DB sang ban dev rieng.
--
-- Chay xong thi chay tiep database/student_results/01_seed_test.sql:
--   student.test@example.com / test-hash
--   instructor.test@example.com / test-hash
--
-- Tren Aiven (Linux) ten DB va ten bang PHAN BIET HOA/THUONG;
-- ten DB phai la Online_Examination (khop db.url).
-- =====================================================================

DROP DATABASE IF EXISTS `Online_Examination`;
CREATE DATABASE IF NOT EXISTS `Online_Examination`
  CHARACTER SET utf8mb4;
USE `Online_Examination`;

CREATE TABLE IF NOT EXISTS `USER` (
  User_ID        INT          NOT NULL AUTO_INCREMENT,
  FirstName      VARCHAR(50)  NOT NULL,
  LastName       VARCHAR(50)  NOT NULL,
  DOB            DATE         NULL,
  Email          VARCHAR(100) NOT NULL,
  Password_hash_ VARCHAR(255) NOT NULL,
  PRIMARY KEY (User_ID),
  UNIQUE KEY uq_user_email (Email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS STUDENT (
  User_ID         INT NOT NULL,
  Enrollment_year INT NOT NULL,
  PRIMARY KEY (User_ID),
  CONSTRAINT fk_student_user FOREIGN KEY (User_ID) REFERENCES `USER` (User_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS INSTRUCTOR (
  User_ID    INT          NOT NULL,
  Department VARCHAR(100) NOT NULL,
  PRIMARY KEY (User_ID),
  CONSTRAINT fk_instructor_user FOREIGN KEY (User_ID) REFERENCES `USER` (User_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS USER_PHONE (
  Phone   VARCHAR(20) NOT NULL,
  User_ID INT         NOT NULL,
  PRIMARY KEY (Phone, User_ID),
  CONSTRAINT fk_phone_user FOREIGN KEY (User_ID) REFERENCES `USER` (User_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS COURSE (
  Course_ID INT          NOT NULL AUTO_INCREMENT,
  CName     VARCHAR(150) NOT NULL,
  Credits   INT          NOT NULL,
  PRIMARY KEY (Course_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS TOPIC (
  Topic_ID INT          NOT NULL AUTO_INCREMENT,
  Content  VARCHAR(255) NOT NULL,
  PRIMARY KEY (Topic_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS COVERS (
  Course_ID INT NOT NULL,
  Topic_ID  INT NOT NULL,
  PRIMARY KEY (Course_ID, Topic_ID),
  CONSTRAINT fk_covers_course FOREIGN KEY (Course_ID) REFERENCES COURSE (Course_ID),
  CONSTRAINT fk_covers_topic  FOREIGN KEY (Topic_ID)  REFERENCES TOPIC (Topic_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS REQUIRES (
  Course_ID_1 INT NOT NULL,
  Course_ID_2 INT NOT NULL,
  PRIMARY KEY (Course_ID_1, Course_ID_2),
  CONSTRAINT fk_requires_1 FOREIGN KEY (Course_ID_1) REFERENCES COURSE (Course_ID),
  CONSTRAINT fk_requires_2 FOREIGN KEY (Course_ID_2) REFERENCES COURSE (Course_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS TEACH (
  User_ID   INT         NOT NULL,
  Course_ID INT         NOT NULL,
  Semester  VARCHAR(30) NOT NULL,
  PRIMARY KEY (User_ID, Course_ID),
  CONSTRAINT fk_teach_instructor FOREIGN KEY (User_ID)   REFERENCES INSTRUCTOR (User_ID),
  CONSTRAINT fk_teach_course     FOREIGN KEY (Course_ID) REFERENCES COURSE (Course_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS QUESTION (
  Quest_ID   INT      NOT NULL AUTO_INCREMENT,
  Content    TEXT     NOT NULL,
  Difficulty TINYINT  NOT NULL,
  Topic_ID   INT      NOT NULL,
  User_ID    INT      NOT NULL,
  PRIMARY KEY (Quest_ID),
  CONSTRAINT fk_question_topic      FOREIGN KEY (Topic_ID) REFERENCES TOPIC (Topic_ID),
  CONSTRAINT fk_question_instructor FOREIGN KEY (User_ID)  REFERENCES INSTRUCTOR (User_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS MCQ (
  Quest_ID INT NOT NULL,
  PRIMARY KEY (Quest_ID),
  CONSTRAINT fk_mcq_question FOREIGN KEY (Quest_ID) REFERENCES QUESTION (Quest_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS SHORT_ANSWER (
  Quest_ID   INT  NOT NULL,
  Answer_key TEXT NOT NULL,
  PRIMARY KEY (Quest_ID),
  CONSTRAINT fk_sa_question FOREIGN KEY (Quest_ID) REFERENCES QUESTION (Quest_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS OPTIONS (
  Option_no    INT        NOT NULL,
  Quest_ID     INT        NOT NULL,
  Op_Content   TEXT       NOT NULL,
  Bool_Correct TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (Option_no, Quest_ID),
  CONSTRAINT fk_options_mcq FOREIGN KEY (Quest_ID) REFERENCES MCQ (Quest_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS EXAMINATION (
  Exam_ID      INT          NOT NULL AUTO_INCREMENT,
  Title        VARCHAR(200) NOT NULL,
  Exam_date    DATETIME     NULL,             -- NULL = chua len lich
  Duration     INT          NOT NULL,            -- phut
  Pass_mark    DECIMAL(6,2) NOT NULL,            -- diem tho
  Max_attempts INT          NOT NULL DEFAULT 1,
  Course_ID    INT          NOT NULL,
  Status       ENUM('DRAFT','PUBLISHED') NOT NULL DEFAULT 'DRAFT',
  PRIMARY KEY (Exam_ID),
  CONSTRAINT fk_exam_course FOREIGN KEY (Course_ID) REFERENCES COURSE (Course_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS PAPER (
  PaperID INT NOT NULL,
  Exam_ID INT NOT NULL,
  PRIMARY KEY (PaperID, Exam_ID),
  CONSTRAINT fk_paper_exam FOREIGN KEY (Exam_ID) REFERENCES EXAMINATION (Exam_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS CONTAINS (
  Quest_ID      INT          NOT NULL,
  PaperID       INT          NOT NULL,
  Exam_ID       INT          NOT NULL,
  QuestionOrder INT          NOT NULL,
  Marks         DECIMAL(6,2) NOT NULL,
  PRIMARY KEY (Quest_ID, PaperID, Exam_ID),
  CONSTRAINT fk_contains_question FOREIGN KEY (Quest_ID)         REFERENCES QUESTION (Quest_ID),
  CONSTRAINT fk_contains_paper    FOREIGN KEY (PaperID, Exam_ID) REFERENCES PAPER (PaperID, Exam_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS REGISTRATION (
  RegisID   INT      NOT NULL AUTO_INCREMENT,
  RegisDate DATETIME NOT NULL,
  User_ID   INT      NOT NULL,
  Exam_ID   INT      NOT NULL,
  PRIMARY KEY (RegisID),
  UNIQUE KEY uq_regis_student_exam (User_ID, Exam_ID),
  CONSTRAINT fk_regis_student FOREIGN KEY (User_ID) REFERENCES STUDENT (User_ID),
  CONSTRAINT fk_regis_exam    FOREIGN KEY (Exam_ID) REFERENCES EXAMINATION (Exam_ID)
) ENGINE=InnoDB;

-- Quy uoc: dang lam = EndTime = StartTime; nop xong = EndTime > StartTime.
CREATE TABLE IF NOT EXISTS ATTEMPT (
  Attempt_no INT      NOT NULL,
  RegisID    INT      NOT NULL,
  PaperID    INT      NOT NULL,
  Exam_ID    INT      NOT NULL,
  StartTime  DATETIME NOT NULL,
  EndTime    DATETIME NOT NULL,
  PRIMARY KEY (Attempt_no, RegisID),
  CONSTRAINT fk_attempt_regis FOREIGN KEY (RegisID)         REFERENCES REGISTRATION (RegisID),
  CONSTRAINT fk_attempt_paper FOREIGN KEY (PaperID, Exam_ID) REFERENCES PAPER (PaperID, Exam_ID)
) ENGINE=InnoDB;

-- MCQ: AnswerText = so thu tu option da chon.
CREATE TABLE IF NOT EXISTS ANSWER (
  Quest_ID    INT          NOT NULL,
  Attempt_no  INT          NOT NULL,
  RegisID     INT          NOT NULL,
  AnswerText  TEXT         NOT NULL,
  MarkAwarded DECIMAL(6,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (Quest_ID, Attempt_no, RegisID),
  CONSTRAINT fk_answer_question FOREIGN KEY (Quest_ID)            REFERENCES QUESTION (Quest_ID),
  CONSTRAINT fk_answer_attempt  FOREIGN KEY (Attempt_no, RegisID) REFERENCES ATTEMPT (Attempt_no, RegisID)
) ENGINE=InnoDB;

-- Hien chua dung (option C). PK giu nguyen baseline (xem finding F01).
CREATE TABLE IF NOT EXISTS SELECT_TABLE (
  Option_no  INT NOT NULL,
  Quest_ID   INT NOT NULL,
  Attempt_no INT NOT NULL,
  RegisID    INT NOT NULL,
  PRIMARY KEY (Option_no, Attempt_no, RegisID),
  CONSTRAINT fk_select_option FOREIGN KEY (Option_no, Quest_ID)           REFERENCES OPTIONS (Option_no, Quest_ID),
  CONSTRAINT fk_select_answer FOREIGN KEY (Quest_ID, Attempt_no, RegisID) REFERENCES ANSWER (Quest_ID, Attempt_no, RegisID)
) ENGINE=InnoDB;

-- Kiem tra: phai ra 20 bang.
SELECT COUNT(*) AS so_bang FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'Online_Examination' AND TABLE_TYPE = 'BASE TABLE';
