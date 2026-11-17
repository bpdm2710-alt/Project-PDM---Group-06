-- Review lab, NOT a schema migration or final submission script.
-- Select an existing scratch database before running this file.
-- Only session-scoped TEMPORARY TABLEs are changed. No project table is changed.
-- Run through tools/run_mysql_review.py, or mysql --force in a fresh session.
-- Exactly TWO deliberate 1062 duplicate-key errors are expected.
-- The temporary-table same-exam example checks that both referenced tuples
-- exist; MySQL does not support FKs on these temporary tables. It is a logical
-- counterexample satisfying the two independent references, not a trigger test.

SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';
SELECT CONCAT('PDM_SERVER|', VERSION());

DROP TEMPORARY TABLE IF EXISTS pdm_norm_old_select, pdm_norm_new_select,
  pdm_norm_registration, pdm_norm_paper, pdm_norm_attempt,
  pdm_norm_valid_attempt, pdm_norm_core, pdm_norm_reg_exam, pdm_norm_rejoined;

CREATE TEMPORARY TABLE pdm_norm_old_select (
  Option_no INT NOT NULL, Quest_ID INT NOT NULL,
  Attempt_no INT NOT NULL, RegisID INT NOT NULL,
  PRIMARY KEY (Option_no, Attempt_no, RegisID)
) ENGINE=InnoDB;
INSERT INTO pdm_norm_old_select VALUES (1,101,1,500);
-- Expected error 1062: different question, same local option number.
INSERT INTO pdm_norm_old_select VALUES (1,102,1,500);
SELECT CONCAT('PDM_TEST|old_key_rejects_valid|',COUNT(*)) FROM pdm_norm_old_select;

DELETE FROM pdm_norm_old_select;
-- Both succeed even though the same question now has two selected options.
INSERT INTO pdm_norm_old_select VALUES (1,101,1,500),(2,101,1,500);
SELECT CONCAT('PDM_TEST|old_key_accepts_two_options|',COUNT(*)) FROM pdm_norm_old_select;

CREATE TEMPORARY TABLE pdm_norm_new_select (
  Option_no INT NOT NULL, Quest_ID INT NOT NULL,
  Attempt_no INT NOT NULL, RegisID INT NOT NULL,
  PRIMARY KEY (Quest_ID, Attempt_no, RegisID)
) ENGINE=InnoDB;
INSERT INTO pdm_norm_new_select VALUES (1,101,1,500),(1,102,1,500);
SELECT CONCAT('PDM_TEST|new_key_accepts_two_questions|',COUNT(*)) FROM pdm_norm_new_select;
-- Expected error 1062: second choice for the same question and attempt.
INSERT INTO pdm_norm_new_select VALUES (2,101,1,500);
SELECT CONCAT('PDM_TEST|new_key_rejects_second_choice|',COUNT(*)) FROM pdm_norm_new_select;

CREATE TEMPORARY TABLE pdm_norm_registration (
  RegisID INT PRIMARY KEY, Exam_ID INT NOT NULL
);
CREATE TEMPORARY TABLE pdm_norm_paper (
  PaperID INT NOT NULL, Exam_ID INT NOT NULL, PRIMARY KEY(PaperID,Exam_ID)
);
CREATE TEMPORARY TABLE pdm_norm_attempt (
  Attempt_no INT NOT NULL, RegisID INT NOT NULL, PaperID INT NOT NULL,
  Exam_ID INT NOT NULL, PRIMARY KEY(Attempt_no,RegisID)
);
INSERT INTO pdm_norm_registration VALUES (500,10),(501,20);
INSERT INTO pdm_norm_paper VALUES (1,10),(1,20);
INSERT INTO pdm_norm_attempt VALUES (1,500,1,20),(2,500,1,10);
SELECT CONCAT('PDM_TEST|both_refs_exist_but_wrong_exam|',COUNT(*))
FROM pdm_norm_attempt a
JOIN pdm_norm_registration r ON r.RegisID=a.RegisID
JOIN pdm_norm_paper p ON p.PaperID=a.PaperID AND p.Exam_ID=a.Exam_ID
WHERE a.Exam_ID<>r.Exam_ID;
SELECT CONCAT('PDM_TEST|registration_has_two_exam_values|',COUNT(*))
FROM (SELECT RegisID FROM pdm_norm_attempt
      GROUP BY RegisID HAVING COUNT(DISTINCT Exam_ID)>1) violations;

-- Lossless witness must use rows satisfying R -> E.
CREATE TEMPORARY TABLE pdm_norm_valid_attempt LIKE pdm_norm_attempt;
INSERT INTO pdm_norm_valid_attempt VALUES
  (1,500,1,10),(2,500,1,10),(1,501,1,20),(2,501,1,20);
CREATE TEMPORARY TABLE pdm_norm_core AS
  SELECT Attempt_no,RegisID,PaperID FROM pdm_norm_valid_attempt;
CREATE TEMPORARY TABLE pdm_norm_reg_exam AS
  SELECT DISTINCT RegisID,Exam_ID FROM pdm_norm_valid_attempt;
CREATE TEMPORARY TABLE pdm_norm_rejoined AS
  SELECT a.Attempt_no,a.RegisID,a.PaperID,r.Exam_ID
  FROM pdm_norm_core a JOIN pdm_norm_reg_exam r ON r.RegisID=a.RegisID;
SELECT CONCAT('PDM_TEST|rejoined_rows|',COUNT(*)) FROM pdm_norm_rejoined;
SELECT CONCAT('PDM_TEST|missing_after_rejoin|',COUNT(*))
FROM pdm_norm_valid_attempt s LEFT JOIN pdm_norm_rejoined t
  ON s.Attempt_no=t.Attempt_no AND s.RegisID=t.RegisID
  AND s.PaperID=t.PaperID AND s.Exam_ID=t.Exam_ID
WHERE t.RegisID IS NULL;
SELECT CONCAT('PDM_TEST|spurious_after_rejoin|',COUNT(*))
FROM pdm_norm_rejoined t LEFT JOIN pdm_norm_valid_attempt s
  ON s.Attempt_no=t.Attempt_no AND s.RegisID=t.RegisID
  AND s.PaperID=t.PaperID AND s.Exam_ID=t.Exam_ID
WHERE s.RegisID IS NULL;

DROP TEMPORARY TABLE pdm_norm_old_select, pdm_norm_new_select,
  pdm_norm_registration, pdm_norm_paper, pdm_norm_attempt,
  pdm_norm_valid_attempt, pdm_norm_core, pdm_norm_reg_exam, pdm_norm_rejoined;
