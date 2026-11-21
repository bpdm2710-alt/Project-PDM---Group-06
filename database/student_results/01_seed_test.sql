-- SEED TEST (schema 21 bang, theo 02_online_examination_full_submission.sql). Chon san schema Online_Examination truoc khi chay.
-- Moi cau lenh nam tren DUNG 1 DONG de client SQL khong tach sai. Chay lai nhieu lan duoc (xoa du lieu test cu truoc).
-- Mat khau tat ca tai khoan: test-hash | student.test@example.com (9001), second.student@example.com (9003), instructor.test@example.com (9002)
-- Khac schema 20 bang: CONTAINS -> EXAM_QUESTION_MARKS (diem) + PAPER_QUESTION (thu tu, theo PaperKey); ATTEMPT dung PaperKey; EndTime NULL = dang lam; MCQ: AnswerText NULL + lua chon o SELECT_TABLE; Pass_mark la %; Password_hash (khong co _); REQUIRES(Prerequisite_ID, Advanced_ID).
DELETE FROM SELECT_TABLE WHERE RegisID IN (SELECT RegisID FROM REGISTRATION WHERE User_ID IN (9001, 9003) OR Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002))) OR Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM ANSWER WHERE RegisID IN (SELECT RegisID FROM REGISTRATION WHERE User_ID IN (9001, 9003) OR Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002))) OR Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM ATTEMPT WHERE RegisID IN (SELECT RegisID FROM REGISTRATION WHERE User_ID IN (9001, 9003) OR Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002)));
DELETE FROM REGISTRATION WHERE User_ID IN (9001, 9003) OR Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002));
DELETE FROM PAPER_QUESTION WHERE PaperKey IN (SELECT PaperKey FROM PAPER WHERE Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002))) OR Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM EXAM_QUESTION_MARKS WHERE Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002)) OR Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM PAPER WHERE Exam_ID IN (SELECT Exam_ID FROM EXAMINATION WHERE Course_ID IN (9001, 9002));
DELETE FROM EXAMINATION WHERE Course_ID IN (9001, 9002);
DELETE FROM OPTIONS WHERE Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM MCQ WHERE Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM SHORT_ANSWER WHERE Quest_ID IN (SELECT Quest_ID FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002));
DELETE FROM QUESTION WHERE User_ID = 9002 OR Topic_ID IN (9001, 9002);
DELETE FROM TEACH WHERE User_ID = 9002 OR Course_ID IN (9001, 9002);
DELETE FROM COVERS WHERE Course_ID IN (9001, 9002) OR Topic_ID IN (9001, 9002);
DELETE FROM REQUIRES WHERE Prerequisite_ID IN (9001, 9002) OR Advanced_ID IN (9001, 9002);
DELETE FROM TOPIC WHERE Topic_ID IN (9001, 9002);
DELETE FROM COURSE WHERE Course_ID IN (9001, 9002);
DELETE FROM STUDENT WHERE User_ID IN (9001, 9003);
DELETE FROM INSTRUCTOR WHERE User_ID = 9002;
DELETE FROM USER_PHONE WHERE User_ID IN (9001, 9002, 9003);
DELETE FROM `USER` WHERE User_ID IN (9001, 9002, 9003);
INSERT INTO `USER` (User_ID, FirstName, LastName, DOB, Email, Password_hash) VALUES (9001, 'Test', 'Student', '2000-01-01', 'student.test@example.com', 'test-hash'), (9002, 'Test', 'Instructor', '1980-01-01', 'instructor.test@example.com', 'test-hash'), (9003, 'Second', 'Student', '2001-05-05', 'second.student@example.com', 'test-hash');
INSERT INTO STUDENT (User_ID, Enrollment_year) VALUES (9001, 2026), (9003, 2025);
INSERT INTO INSTRUCTOR (User_ID, Department) VALUES (9002, 'Test Department');
INSERT INTO COURSE (Course_ID, CName, Credits) VALUES (9001, 'Test Course', 3), (9002, 'Database Systems', 4);
INSERT INTO TOPIC (Topic_ID, Content) VALUES (9001, 'Test Topic'), (9002, 'SQL Basics');
INSERT INTO COVERS (Course_ID, Topic_ID) VALUES (9001, 9001), (9001, 9002), (9002, 9002);
INSERT INTO TEACH (User_ID, Course_ID, Semester) VALUES (9002, 9001, '2026-S1'), (9002, 9002, '2026-S1');
INSERT INTO QUESTION (Quest_ID, Content, Difficulty, Topic_ID, User_ID) VALUES (9001, 'What is 2 + 2?', 1, 9001, 9002), (9002, 'Define normalization.', 2, 9001, 9002), (9003, 'What is the capital of France?', 1, 9001, 9002), (9004, 'What does SQL stand for?', 2, 9001, 9002), (9005, 'Which command removes all rows quickly?', 3, 9002, 9002), (9006, 'Name the SQL keyword that removes duplicate rows.', 2, 9002, 9002);
INSERT INTO MCQ (Quest_ID) VALUES (9001), (9003), (9005);
INSERT INTO SHORT_ANSWER (Quest_ID, Answer_key) VALUES (9002, 'normalization'), (9004, 'structured query language'), (9006, 'distinct');
INSERT INTO OPTIONS (Option_no, Quest_ID, Op_Content, Bool_Correct) VALUES (1, 9001, '4', 1), (2, 9001, '5', 0), (3, 9001, '6', 0), (1, 9003, 'Paris', 1), (2, 9003, 'London', 0), (1, 9005, 'DELETE', 0), (2, 9005, 'TRUNCATE', 1), (3, 9005, 'DROP', 0);
-- Pass_mark la %: 9001 = 10/20 diem -> 50%, 9002 = 10/20 -> 50%, 9003 = 5/10 -> 50%, 9004 (chua co de) -> 50%.
INSERT INTO EXAMINATION (Exam_ID, Title, Exam_date, Duration, Pass_mark, Max_attempts, Course_ID, Status) VALUES (9001, 'Student Results Test Exam', (NOW() - INTERVAL 10 DAY), 60, 50.00, 2, 9001, 'PUBLISHED'), (9002, 'Open Exam', (NOW() - INTERVAL 12 HOUR), 1440, 50.00, 2, 9001, 'PUBLISHED'), (9003, 'Upcoming Exam', (NOW() + INTERVAL 3 DAY), 45, 50.00, 1, 9001, 'PUBLISHED'), (9004, 'Draft Exam', NULL, 30, 50.00, 1, 9001, 'DRAFT');
-- PaperKey tu tang (AUTO_INCREMENT); cac lenh sau tim PaperKey theo (PaperID, Exam_ID).
INSERT INTO PAPER (PaperID, Exam_ID) VALUES (1, 9001), (1, 9002), (1, 9003);
-- CONTAINS cu tach lam 2: diem theo exam (EXAM_QUESTION_MARKS, phai co TRUOC) va thu tu cau tren paper (PAPER_QUESTION).
INSERT INTO EXAM_QUESTION_MARKS (Exam_ID, Quest_ID, Marks) VALUES (9001, 9001, 5.00), (9001, 9002, 5.00), (9001, 9003, 5.00), (9001, 9004, 5.00), (9002, 9001, 5.00), (9002, 9003, 5.00), (9002, 9005, 10.00), (9003, 9002, 5.00), (9003, 9004, 5.00);
INSERT INTO PAPER_QUESTION (PaperKey, Quest_ID, QuestionOrder) SELECT p.PaperKey, v.Quest_ID, v.QuestionOrder FROM PAPER p JOIN (SELECT 9001 AS Exam_ID, 9001 AS Quest_ID, 1 AS QuestionOrder UNION ALL SELECT 9001, 9002, 2 UNION ALL SELECT 9001, 9003, 3 UNION ALL SELECT 9001, 9004, 4 UNION ALL SELECT 9002, 9001, 1 UNION ALL SELECT 9002, 9003, 2 UNION ALL SELECT 9002, 9005, 3 UNION ALL SELECT 9003, 9002, 1 UNION ALL SELECT 9003, 9004, 2) v ON v.Exam_ID = p.Exam_ID WHERE p.PaperID = 1;
-- RegisDate phai <= Exam_date (trigger): dang ky exam 9002 (mo tu 12 gio truoc) luc 13 gio truoc.
INSERT INTO REGISTRATION (RegisID, RegisDate, User_ID, Exam_ID) VALUES (9001, (NOW() - INTERVAL 10 DAY) - INTERVAL 1 DAY, 9001, 9001), (9002, (NOW() - INTERVAL 10 DAY) - INTERVAL 1 DAY, 9003, 9001), (9003, NOW() - INTERVAL 13 HOUR, 9003, 9002);
-- Luot 1 cua sinh vien 9001 (da nop): 10/20 diem = 50% -> dau.
INSERT INTO ATTEMPT (Attempt_no, RegisID, StartTime, EndTime, PaperKey) SELECT 1, 9001, (NOW() - INTERVAL 10 DAY) + INTERVAL 5 MINUTE, (NOW() - INTERVAL 10 DAY) + INTERVAL 35 MINUTE, PaperKey FROM PAPER WHERE PaperID = 1 AND Exam_ID = 9001;
INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) VALUES (9001, 1, 9001, NULL, 5.00), (9002, 1, 9001, 'normalization', 5.00), (9003, 1, 9001, NULL, 0.00), (9004, 1, 9001, 'sql', 0.00);
INSERT INTO SELECT_TABLE (Option_no, Quest_ID, Attempt_no, RegisID) VALUES (1, 9001, 1, 9001), (2, 9003, 1, 9001);
-- Luot 1 cua sinh vien 9003 (da nop): 15/20 diem = 75% -> dau.
INSERT INTO ATTEMPT (Attempt_no, RegisID, StartTime, EndTime, PaperKey) SELECT 1, 9002, (NOW() - INTERVAL 10 DAY) + INTERVAL 5 MINUTE, (NOW() - INTERVAL 10 DAY) + INTERVAL 40 MINUTE, PaperKey FROM PAPER WHERE PaperID = 1 AND Exam_ID = 9001;
INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) VALUES (9001, 1, 9002, NULL, 0.00), (9002, 1, 9002, 'normalization', 5.00), (9003, 1, 9002, NULL, 5.00), (9004, 1, 9002, 'structured query language', 5.00);
INSERT INTO SELECT_TABLE (Option_no, Quest_ID, Attempt_no, RegisID) VALUES (2, 9001, 1, 9002), (1, 9003, 1, 9002);
-- Luot dang lam cua sinh vien 9003 o Open Exam: EndTime NULL; cau 9001 da chon dap an 1, chua cham (MarkAwarded NULL).
INSERT INTO ATTEMPT (Attempt_no, RegisID, StartTime, EndTime, PaperKey) SELECT 1, 9003, NOW() - INTERVAL 10 MINUTE, NULL, PaperKey FROM PAPER WHERE PaperID = 1 AND Exam_ID = 9002;
INSERT INTO ANSWER (Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded) VALUES (9001, 1, 9003, NULL, NULL);
INSERT INTO SELECT_TABLE (Option_no, Quest_ID, Attempt_no, RegisID) VALUES (1, 9001, 1, 9003);
SELECT 'teach' AS bang, COUNT(*) AS so_dong FROM TEACH WHERE User_ID = 9002 UNION ALL SELECT 'covers', COUNT(*) FROM COVERS WHERE Course_ID IN (9001, 9002) UNION ALL SELECT 'users', COUNT(*) FROM `USER` WHERE User_ID IN (9001, 9002, 9003) UNION ALL SELECT 'questions', COUNT(*) FROM QUESTION WHERE Quest_ID BETWEEN 9001 AND 9006 UNION ALL SELECT 'exams', COUNT(*) FROM EXAMINATION WHERE Exam_ID BETWEEN 9001 AND 9004 UNION ALL SELECT 'paper_questions', COUNT(*) FROM PAPER_QUESTION pq JOIN PAPER p ON p.PaperKey = pq.PaperKey WHERE p.Exam_ID BETWEEN 9001 AND 9003 UNION ALL SELECT 'registrations', COUNT(*) FROM REGISTRATION WHERE RegisID BETWEEN 9001 AND 9003 UNION ALL SELECT 'attempts', COUNT(*) FROM ATTEMPT WHERE RegisID BETWEEN 9001 AND 9003;
