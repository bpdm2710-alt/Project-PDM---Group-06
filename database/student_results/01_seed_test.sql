-- Test data for the Student & Results module.
-- Target schema: Online_Examination
-- IDs 9001+ are reserved for this test flow.
-- No schema changes are required.

START TRANSACTION;

-- Remove any previous run first (FK-safe order).
DELETE FROM SELECT_TABLE WHERE RegisID = 9001 OR Quest_ID IN (9001, 9002, 9003, 9004);
DELETE FROM ANSWER WHERE RegisID = 9001 OR Quest_ID IN (9001, 9002, 9003, 9004);
DELETE FROM ATTEMPT WHERE RegisID = 9001;
DELETE FROM REGISTRATION WHERE RegisID = 9001;
DELETE FROM CONTAINS WHERE Exam_ID = 9001;
DELETE FROM PAPER WHERE Exam_ID = 9001;
DELETE FROM EXAMINATION WHERE Exam_ID = 9001;
DELETE FROM OPTIONS WHERE Quest_ID IN (9001, 9003);
DELETE FROM MCQ WHERE Quest_ID IN (9001, 9003);
DELETE FROM SHORT_ANSWER WHERE Quest_ID IN (9002, 9004);
DELETE FROM QUESTION WHERE Quest_ID IN (9001, 9002, 9003, 9004);
DELETE FROM TEACH WHERE User_ID = 9002 OR Course_ID = 9001;
DELETE FROM COVERS WHERE Course_ID = 9001 OR Topic_ID = 9001;
DELETE FROM REQUIRES WHERE Course_ID_1 = 9001 OR Course_ID_2 = 9001;
DELETE FROM TOPIC WHERE Topic_ID = 9001;
DELETE FROM COURSE WHERE Course_ID = 9001;
DELETE FROM STUDENT WHERE User_ID = 9001;
DELETE FROM INSTRUCTOR WHERE User_ID = 9002;
DELETE FROM USER_PHONE WHERE User_ID IN (9001, 9002);
DELETE FROM `USER` WHERE User_ID IN (9001, 9002);

-- Users and roles.
INSERT INTO `USER` (User_ID, FirstName, LastName, DOB, Email, Password_hash_)
VALUES
    (9001, 'Test', 'Student', '2000-01-01', 'student.test@example.com', 'test-hash'),
    (9002, 'Test', 'Instructor', '1980-01-01', 'instructor.test@example.com', 'test-hash');

INSERT INTO STUDENT (User_ID, Enrollment_year) VALUES (9001, 2026);
INSERT INTO INSTRUCTOR (User_ID, Department) VALUES (9002, 'Test Department');

-- Course and topic.
INSERT INTO COURSE (Course_ID, CName, Credits) VALUES (9001, 'Test Course', 3);
INSERT INTO TOPIC (Topic_ID, Content) VALUES (9001, 'Test Topic');

-- Questions: two MCQ and two short-answer, 5 marks each.
INSERT INTO QUESTION (Quest_ID, Content, Difficulty, Topic_ID, User_ID) VALUES
    (9001, 'What is 2 + 2?', 1, 9001, 9002),
    (9002, 'Define normalization.', 1, 9001, 9002),
    (9003, 'What is the capital of France?', 1, 9001, 9002),
    (9004, 'What does SQL stand for?', 1, 9001, 9002);

INSERT INTO MCQ (Quest_ID) VALUES (9001), (9003);
INSERT INTO SHORT_ANSWER (Quest_ID, Answer_key) VALUES
    (9002, 'normalization'),
    (9004, 'structured query language');

INSERT INTO OPTIONS (Option_no, Quest_ID, Op_Content, Bool_Correct) VALUES
    (1, 9001, '4', 1),
    (2, 9001, '5', 0),
    (3, 9001, '6', 0),
    (1, 9003, 'Paris', 1),
    (2, 9003, 'London', 0);

-- Exam, paper, questions and registration.
INSERT INTO EXAMINATION
    (Exam_ID, Title, Exam_date, Duration, Pass_mark, Max_attempts, Course_ID, Status)
VALUES
    (9001, 'Student Results Test Exam', '2026-11-20 09:00:00', 60, 10.00, 2, 9001, 'PUBLISHED');

INSERT INTO PAPER (PaperID, Exam_ID) VALUES (1, 9001);

INSERT INTO CONTAINS (QuestionOrder, Marks, Quest_ID, PaperID, Exam_ID) VALUES
    (1, 5.00, 9001, 1, 9001),
    (2, 5.00, 9002, 1, 9001),
    (3, 5.00, 9003, 1, 9001),
    (4, 5.00, 9004, 1, 9001);

INSERT INTO REGISTRATION (RegisID, RegisDate, User_ID, Exam_ID)
VALUES (9001, '2026-11-20 09:00:00', 9001, 9001);

COMMIT;
