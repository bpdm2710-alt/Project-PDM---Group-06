-- Patch 03: them cot Status cho EXAMINATION va cho phep Exam_date = NULL ("Not scheduled").
-- CHAY DUNG 1 LAN tren schema Online_Examination hien co (chay lai se bao "Duplicate column").
-- Cac ky thi da co san duoc dat PUBLISHED (de sinh vien van thay nhu truoc);
-- ky thi tao moi mac dinh la DRAFT.

USE `Online_Examination`;

ALTER TABLE EXAMINATION
  ADD COLUMN Status ENUM('DRAFT','PUBLISHED') NOT NULL DEFAULT 'PUBLISHED',
  MODIFY COLUMN Exam_date DATETIME NULL;

ALTER TABLE EXAMINATION ALTER COLUMN Status SET DEFAULT 'DRAFT';

-- Kiem tra:
SELECT Exam_ID, Title, Exam_date, Status FROM EXAMINATION;
