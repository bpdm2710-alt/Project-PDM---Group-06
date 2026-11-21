# Normalization summary — 21-table BCNF schema (Group 06 / Topic 20)

* **Status & scope (20/11/2026):** This is the SQL contribution and technical summary, verified on an isolated MySQL `8.4.11`, for the proposed **21-table BCNF** design (`#7B` + `#2B1`). The detailed analysis (the 21 relations & FDs, the 1NF → BCNF proof, anomaly examples and the 50 rules `BR-01`–`BR-50` for Chapter 3 of the Final Report) is kept in the group's Word documents, to keep the repository focused on Java and SQL code.

---

## 1. The 21-table BCNF architecture (`#7B` + `#2B1`)

* **DDL objects & constraints:** `21` base tables (all in BCNF), `21` PKs, `28` FKs (all `ON UPDATE RESTRICT`; `27` with `ON DELETE RESTRICT`, only `user_phone.User_ID` with `ON DELETE CASCADE`), `4` `UNIQUE`, `28` `CHECK`, `5` indexes, `3` views (`v_paper_total_marks`, `v_attempt_scores`, `v_representative_attempts`), `28` two-way protection triggers and `5` ACID stored procedures (`sp_create_student`, `sp_create_instructor`, `sp_start_attempt`, `sp_submit_mcq_answer`, `sp_finalize_attempt`).
* **5 main changes from the old 20-table version (`script.sql`):**
  1. **`select_table`:** PK changed to `(Quest_ID, Attempt_no, RegisID)` (one choice per MCQ in one attempt), with 2 composite FKs to `options(Option_no, Quest_ID)` and `answer(Quest_ID, Attempt_no, RegisID)`.
  2. **`paper` & `attempt`:** a surrogate key `PaperKey` (`AUTO_INCREMENT`, `UNIQUE(Exam_ID, PaperID)`) was added and the redundant column `attempt.Exam_ID` removed, eliminating the partial dependency `RegisID -> Exam_ID`; a trigger ensures `paper.Exam_ID = registration.Exam_ID`.
  3. **`contains` split into `exam_question_marks` + `paper_question`:** `exam_question_marks(Exam_ID, Quest_ID, Marks)` fixes the maximum marks per exam (`#7B`), while `paper_question(PaperKey, Quest_ID, QuestionOrder)` holds the paper structure and question order (`UNIQUE(PaperKey, QuestionOrder)`).
  4. **`teach`:** a 3-column PK `(User_ID, Course_ID, Semester)` with a case-sensitive `CHECK` `^20[0-9]{2}-(S[12]|SU)$`.
  5. **Consistent column names & in-progress / waiting-for-grading states:** `Password_hash`, `Prerequisite_ID`, `Advanced_ID` everywhere; `attempt.EndTime`, `answer.AnswerText` and `answer.MarkAwarded` may be `NULL` while an attempt is in progress or waiting for grading. When `sp_finalize_attempt` runs, every blank answer or short answer containing only whitespace (`[[:space:]]`) gets `0.00`; `v_attempt_scores` only publishes `Total_Score`, `ScorePercent` and `IsPassed` when `IsFullyGraded = 1`.

---

## 2. Using the scripts in [`database/normalized_21tables/`](../../database/normalized_21tables/)

| Group / Purpose | File | Notes |
| --- | --- | --- |
| **D5 submission / build a new database** | [`02_online_examination_full_submission.sql`](../../database/normalized_21tables/02_online_examination_full_submission.sql) | Uses `Online_Examination` for `DROP`, `CREATE` and `USE`, matching `application.properties.example`. The seed has at least 15 rows per table. Run [`05_upgrade_password_hashes.sql`](../../database/normalized_21tables/05_upgrade_password_hashes.sql) right after it. |
| **Build the `.mwb` in Workbench** | [`01_schema_ddl_for_mwb_import.sql`](../../database/normalized_21tables/01_schema_ddl_for_mwb_import.sql) + [`WORKBENCH_MWB_AND_EXECUTION_GUIDE.md`](../../database/normalized_21tables/WORKBENCH_MWB_AND_EXECUTION_GUIDE.md) | Pure DDL creating the `Online_Examination` schema (no `DELIMITER`/triggers) for *Reverse Engineer MySQL Create Script*, giving a `.mwb` file and diagram without syntax errors. |
| **Query team & report Ch. 4** | [`03_verify_constraints_and_acceptance_N01_N24.sql`](../../database/normalized_21tables/03_verify_constraints_and_acceptance_N01_N24.sql) | Run right after `02` on `Online_Examination` to get the results of the 5 Topic 20 queries, the row counts and the 39 acceptance checks. |
| **Java DAO / Service team** | [`02_online_examination_full_submission.sql`](../../database/normalized_21tables/02_online_examination_full_submission.sql) | Use the 3 scoring/ranking views; the app keeps the attempt logic in Java (see [UPDATE_NOTES.md](../../UPDATE_NOTES.md), Step 4) with the triggers as the last line of defence. |

### MySQL schema

All scripts use the target schema **`Online_Examination`**, matching `application.properties.example`.

The results below come from an isolated MySQL 8.4.11 on Windows with `lower_case_table_names = 1`. The scripts have also
been run on MySQL 8.0 on Linux with `lower_case_table_names = 0` (case-sensitive, like Aiven): table names are UPPERCASE everywhere.

`01` and `02` drop and recreate the `Online_Examination` schema. Run them only in a separate test environment.

---

## 3. Verification status

* **Verified on an isolated MySQL `8.4.11`:**
  * `01_schema_ddl_for_mwb_import.sql` runs cleanly (`exit code 0`).
  * `02_online_examination_full_submission.sql` runs cleanly **twice in a row** (`exit code 0`); all `21/21` tables have between `15` and `116` rows.
  * `03_verify_constraints_and_acceptance_N01_N24.sql` returns all 5 Topic 20 queries and **`ACCEPTANCE_SUMMARY|PASSED=39|FAILED=0`**.
* **Tasks that were open when this summary was written, now done:**
  1. Export a new `.mwb` file and the 21-table relational diagram → see [`docs/design/`](../design/).
  2. Decide how to move the Aiven database to 21 tables → migrated in place, keeping the data.
  3. Move the Java `DAO` / `MODEL` / `Service` classes to the 21-table schema → done (see [UPDATE_NOTES.md](../../UPDATE_NOTES.md), Steps 8–10).

---

## 4. Checking the cardinality symbols on the EER (`erdplus_D.png`) against the business rules

Following the ERDPlus convention (the symbol **next to entity X** shows how many X relate to one entity at the other end), the current `erdplus_D.png` needs 3 ends next to `Question` changed from `|<` (`1..N`, mandatory `>= 1`) to `o<` (`0..N`, may be `0` while authoring):

| Relationship | Currently drawn | Should be | Business rule |
| --- | --- | --- | --- |
| `Has_ques` (`Topic` — `Question`) | `\|<` next to `Question` | `o<` | **BR-11:** a topic can exist before its first question (the `Topic` end stays `\|\|` per **BR-15**). |
| `Paper_Question` (`Paper` — `Question`) | `\|<` next to `Question` | `o<` | **BR-25, BR-28:** a paper is created before questions are added; having `>= 1` question is only required when the paper is assigned to an `Attempt` (trigger `trg_attempt_bi_checks`). |
| `Exam_Question_Marks` (`Examination` — `Question`) | `\|<` next to `Question` | `o<` | **BR-25:** an exam is set up before questions and their maximum marks are assigned. |

*The same "may exist before its children while authoring" principle (`Master v2` §3.1)* applies to the `o<` ends of `Register` (next to `Registration`, **BR-31**), `Has_paper` (next to `Paper`, **BR-25**) and `Option_of` (next to `Options`, **BR-20** — `>= 2` options and `1` correct answer are checked before an MCQ is put on a paper). These minimum-cardinality changes (`1` → `0`) on the EER diagram **do not change any table, PK or FK in the 21-table SQL DDL**.
