# UPDATE NOTES – Online Examination (Group 06)

Step-by-step history of the changes made to the project, with how each step was tested.
For how to set up and run the project today, see [README.md](README.md).

Test environment used throughout: JDK 21 + MySQL 8.0 on Linux (`lower_case_table_names = 0`, case-sensitive like Aiven).

> Some early steps were later changed again. Where that happened it is noted in the step
> (for example the old `DAO/` + `MODEL/` code removed in Step 6 was kept by the group, and the
> `04_migration` script from Step 9 was removed from the project once Aiven had been migrated).

---

## Step 0 – Fix the red errors in AttemptDAO

**Cause:** while copying the Step 1 fix, the new `AttemptDAO.java` (package `org.example.DAO.jdbc`) was copied by
mistake into `src/main/java/org/example/DAO/AttemptDAO.java`, overwriting the old file (package `org.example.DAO`). Result:
- `duplicate class: org.example.DAO.jdbc.AttemptDAO` (two files declaring the same class);
- `StudentResultService` could no longer see `org.example.DAO.AttemptDAO` → 10 `cannot find symbol` errors.

**Fix:** restored `DAO/AttemptDAO.java` to its previous version from git. The new version lives only in
`DAO/jdbc/AttemptDAO.java` (unchanged, already correct). `javac` compiles all of `src/main/java` cleanly.

---

## Step 2 – Align the SQL files (#5) – VERIFIED on MySQL 8.0 Linux

| File | Change | Result |
|---|---|---|
| `normalized_21tables/02_...full_submission.sql` | Unchanged (the reference file) | Runs cleanly: 21 tables, 4 views, 35 seed users |
| `normalized_21tables/01_schema_ddl_for_mwb_import.sql` | **Regenerated from the DDL part of 02** (tables + indexes): now has `Status`, nullable `Exam_date`, UPPERCASE table names | Runs cleanly, `EXAMINATION` has the right columns |
| `normalized_21tables/03_verify_..._N01_N24.sql` | 123 table names in backticks made UPPERCASE | After 02: **PASSED=39, FAILED=0** (previously 7 `Table ... doesn't exist` errors) |
| `database/queries/PDMProjectQuery.sql` (NEW) | The query file with UPPERCASE table names and `USE Online_Examination` | Every query runs without errors |

Note: when the schema changes later, edit only 02 and regenerate 01 (01 = from line 1 up to the "DERIVED VIEWS" part of 02).

---

## Step 3 – Real password hashing (#2) + demo seed with relative dates (#3) – VERIFIED

### Passwords
- NEW `src/main/java/org/example/util/PasswordHasher.java`: PBKDF2-HMAC-SHA256, 120,000 iterations, 16-byte salt,
  part of the JDK (no extra library). Stored format: `pbkdf2_sha256$120000$<salt>$<hash>`.
  Generate a hash from the command line: `java -cp target/classes org.example.util.PasswordHasher "Password"`.
- `AuthServiceImpl.passwordMatches`: PBKDF2 hash → verify; an old unhashed value → plain comparison ONCE, then it is
  **replaced by a hash** right after a successful sign-in (`upgradeHashIfNeeded`).
- `UserDAO.updatePasswordHash(...)` (new).
- **Demo passwords: instructors (ID 1-15) `Inst@123`, students (ID 101-120) `Stu@123`.**
- NEW `normalized_21tables/05_upgrade_password_hashes.sql`: for a database built with the old seed (`sha256:...`).
  Only updates rows that still hold an old value; safe to re-run.
- `DatabaseConnection`: only sets the truststore when one is configured (a missing key used to cause a
  NullPointerException; a local MySQL without SSL now works too).

### Demo seed
- NEW `database/demo/03_demo_seed.sql` (run after 02, re-runnable; uses `SET time_zone = '+07:00'` because Aiven
  defaults to UTC):
  - `DEMO - Open now`: opened 5 minutes ago, 180 minutes, 2 attempts; sv101–sv103 registered → press Start.
  - `DEMO - Upcoming`: opens in 2 days, nobody registered → try Register.
  - `DEMO - Expired attempt`: sv101 has an unsubmitted attempt past its time limit → the app must close and grade it.
  - `DEMO - Draft`: a draft for the instructor to edit, build a paper for and publish.

### Test results (calling the services directly, MySQL 8 Linux)
- Sign-in: 6/6 PASS (right/wrong password, case-insensitive email, old hash upgraded).
- Student flow: 11/11 PASS – expired attempt closed and graded 10/40; Start → save 5 answers → re-enter keeps the
  answers → Submit 60/60 (100%, passed); register for an upcoming exam; starting early is blocked with
  "This exam is not open yet."; Overview OK.
- 03_verify after the seed change: still PASSED=39/39.

---

## Step 4 – Decide on `sp_*` and short-answer grading (#6) – VERIFIED

**Decisions (for the report):**
1. The app **does not call** `sp_start_attempt / sp_submit_mcq_answer / sp_finalize_attempt`. Reason: the procedures
   run their own `START TRANSACTION ... COMMIT`, which cannot be nested inside `Db.tx` (the Java transaction and row
   locks). The logic is in Java (`StudentServiceImpl`); the database triggers remain the last line of defence.
   The procedures stay in the schema as a database-level API and are tested by `03_verify` (39/39).
2. **Short-answer grading follows `sp_finalize_attempt` (BR-42):** blank → 0; matches `Answer_key` (trimmed,
   case-insensitive) → full marks; **different from the key → `MarkAwarded = NULL`, waiting for the instructor**
   (previously it got 0 immediately, so manual grading never happened). MCQs are still graded on submit.

**Code:**
- `StudentServiceImpl`: `gradeAndClose` uses the new `autoMark(...)` (null = waiting for grading).
- NEW `DAO/jdbc/GradingDAO.java`: `pendingForCourse`, `countPendingForCourse`, `findForUpdate` (locks the ANSWER row), `setMark`.
- `InstructorService` (+ Impl): `listPendingGrading(courseId)`, `gradeAnswer(AnswerRef, mark)`.
  Checks: the instructor teaches the course; short answers only; submitted attempts only; 0 <= mark <= Marks,
  at most 2 decimal places. A mark can be changed by grading again.
- NEW `ui/panel/GradingTab.java` + a **"Grading"** tab on the instructor's course page: table of answers waiting for
  grading, view question / answer / reference key, buttons "Full marks", "0", "Save mark".
  The student's Results screen shows "GRADING" until every question has a mark.

**Tests:** manual grading 8/8 PASS (pending answers listed, mark above max blocked, 3 decimal places blocked,
manual grading of an MCQ blocked, after grading the student's result is fully graded 15/60, an instructor of another
course is blocked with UNAUTHORIZED). Student flow 11/11 and sign-in 6/6 re-run: PASS.

---

## Step 5 – ReportDAO designed from the `.sql` file, wired into Service/UI (#7) – VERIFIED

**Query fixes (in both `database/queries/PDMProjectQuery.sql` and `ReportDAO`, kept identical):**
| Query | Fix |
|---|---|
| Q1 errors > 70% | counts submitted attempts only (`EndTime IS NOT NULL`) |
| Q4 score per attempt | only attempts that are submitted **and fully graded** (IN `v_attempt_scores.IsFullyGraded = 1`) → no more "partial" scores; `RegisID` added to GROUP BY |
| Q5 set operation | uses `EXCEPT` as in the query file. Because each question belongs to **one topic**, "topic A EXCEPT topic B" always equals topic A (and the two topics in the old file did not exist, so it was always empty) → changed to **"questions in exam A but not in exam B"**. Needs MySQL 8.0.31+ |
| Q10 division | `PUBLISHED` exams and submitted attempts only |
| Q11 average by topic | submitted attempts only; adds an `Average_Percent` column (% of the question's marks) |

**Code:**
- `DAO/jdbc/ReportDAO.java` (rewritten): each query is one SQL constant `Q1_...`–`Q11_...` + one typed method
  (record) + a shared `table(conn, sql, params)` returning columns/rows for display.
- `InstructorService.runReport(ReportKind, ReportParams)` (+ enum `ReportKind` with title/description,
  `ReportParams`, `ReportTable`). Only an instructor of the course can run it.
- NEW `ui/panel/ReportsTab.java` + a **"Reports"** tab on the instructor's course page: choose a query, enter the
  threshold N (Q1/Q6/Q8) or pick exams A/B (Q5), click Run; the result is shown as a sortable table.
- `StatsDAO` (Statistics tab): counts **fully graded** attempts only, avoiding falsely low scores while answers wait for grading.

**Tests:** all 11 reports through the service return the same row counts as running the `.sql` file directly
(Q1=2, Q2=49, Q3=6, Q4=34, Q5=2, Q6=14, Q7=14, Q8=11, Q9=15, Q10=3, Q11=17); typed methods match;
missing exam and non-teaching instructor are blocked; GradingTab/ReportsTab build (headless).
Re-run: student flow 11/11, manual grading 8/8, sign-in 6/6, 03_verify 39/39.

---

## Step 6 – Clean up old code, old scripts, documentation (#12–#15)

- Old documents (`PROGRESS_REPORT.md`, `CHANGELOG_PATCHES.md`, `CHANGES_STEP1.md`, `docs/ui/PROGRESS.md`) moved to
  `docs/legacy/`, each marked as outdated. `README.md` gained the SQL run order, demo accounts, build/test.
- NEW `.gitattributes` (LF for java/sql/md/xml, CRLF for .bat) → removes ~40 files showing as "modified" only because of CRLF/LF.
  After copying, run once: `git add --renormalize .` and commit.
- *Later changed:* the old `DAO/*`, `MODEL/*`, `StudentResultService` and `StudentResultsTestRunner` were first removed
  here, but the group kept them; Step 10 updated them for the 21-table schema instead.

## Step 7 – pom.xml, JUnit, full-flow test (#4, #17, #18)

- `pom.xml`: resource folder `src/main/Sources` (correct case, works on Linux/Mac), `maven.compiler.release = 21`
  (instead of 26), JUnit 5.10.2, surefire, **maven-shade** → `mvn package` builds
  `target/Online_Examination_System-1.0-SNAPSHOT-all.jar`, runnable with `java -jar`.
- Tests (`src/test/java`):
  - `util/PasswordHasherTest`, `Service/GradingRulesTest` – unit tests, no database needed.
  - `it/EndToEndTest` – full flow (sign-in → expired attempt closed → take/save/re-enter/submit → register →
    short answer waiting → instructor grades → 11 reports + statistics): **6/6 PASS**, also on a second run after
    re-seeding the demo. Runs only with `-Dit.db=local` AND a `db.url` on localhost (refuses anything else, so it never
    writes to Aiven).
  - How to run E2E: build a local MySQL with 02 + `demo/03_demo_seed.sql`; copy
    `src/test/resources/application.properties.example` to `application.properties`; `mvn test -Dit.db=local`.
- Note: the development sandbox could not reach Maven Central, so tests were run with javac + the JUnit console
  (same JUnit 5 version); run `mvn clean package` once on a group computer to confirm the plugins.

---

## Step 8 – Follow the group's 02 file + no more red errors in AttemptDAO

**The reference 02 is the group's file** (copied over `normalized_21tables/02_...sql`, `01` regenerated from it).
Differences from the earlier version: NO AUTO_INCREMENT (except `PAPER.PaperKey`), NO view `vw_AverageScoreByTopic`,
seed passwords are `sha256:...`. Table names are UPPERCASE – the code matches (every SQL string in Java was scanned:
every table after FROM/JOIN/INTO/UPDATE exists in 02, every `x.Column` exists).

**No more red in AttemptDAO:**
- `FOR UPDATE OF r` / `FOR UPDATE OF a` (MySQL 8 syntax that IntelliJ underlines) → lock exactly one row with the
  standard `SELECT RegisID FROM REGISTRATION WHERE RegisID = ? FOR UPDATE`, then read the join. Same in `GradingDAO`.
- `ON DUPLICATE KEY UPDATE x = VALUES(x)` (deprecated since MySQL 8.0.20) → `x = ?` (AttemptDAO, PaperDAO).

**Fixes for a schema without AUTO_INCREMENT** (creating an exam / question / registration used to fail with
`Field 'Exam_ID' doesn't have a default value`):
- NEW `Jdbc.nextId(table, column)` = `SELECT COALESCE(MAX(id),0)+1 ... FOR UPDATE` (inside the transaction).
- `ExamDAO.insert`, `QuestionDAO.insert`, `StudentDAO.register` pass the generated ID (still works if AUTO_INCREMENT is added later).
- `demo/03_demo_seed.sql`: Exam_ID / RegisID = MAX + 1.

**No dependency on the missing view:** `ReportDAO` Q11 is written directly on the tables; `PDMProjectQuery.sql` Q12 creates its own view.

**SQL run order on Aiven:** 02 → `05_upgrade_password_hashes.sql` → (demo) `demo/03_demo_seed.sql`.

**Tests on the group's own 02 (MySQL 8 Linux):** 03_verify 39/39; create exam/question/paper/publish/register 7/7;
student flow 11/11; manual grading 8/8; sign-in 6/6; Reports 11/11 matching the .sql file; JUnit 14/14.

---

## Step 9 – Align 03/04 with the group's 02, in-place migration

- `03_verify_...sql`: UPPERCASE table names (123 places). Right after the group's 02: **PASSED=39 | FAILED=0**.
- `04_migration_...sql` rewritten to migrate the old 20-table `Online_Examination` in place (old tables renamed to
  `OLD_*`, 21 new tables created, data copied in one checked transaction, `OLD_*` removed only when everything succeeded).
  Verified: an old database with data → 21 tables, structure **identical** to a database built from 02.
- *Later changed:* once Aiven had been migrated, the group removed `04_migration` from the project.

---

## Step 10 – Review of the group's project zip (21/11) and update of what was still old

**Why `sv102` got "wrong password":** the previous update package had been extracted into a nested folder
`Project-PDM---Group-06_cub`, so the main code was still the original – whose old `AuthServiceImpl` compared passwords
as plain strings and could not read the PBKDF2 hashes (after running 05). This package copies over the right place.

**Old files replaced:** `AuthServiceImpl`, `StudentServiceImpl`, `InstructorService(+Impl)`, `DatabaseConnection`,
`DAO/jdbc/*` (Attempt, Exam, Jdbc, Paper, Question, Stats, Student, User), `InstructorCoursesPage`, `pom.xml`,
`.gitignore`, README/documents, `01`/`02` SQL (02 = the group's version).
**Added:** `GradingDAO`, `GradingTab`, `ReportsTab`, JUnit tests, `demo/03_demo_seed.sql`, `05_upgrade_password_hashes.sql`,
`queries/PDMProjectQuery.sql`, `.gitattributes`.

**Old code `org.example.DAO` + `MODEL` + `StudentResultService` (kept by the group) – updated for 21 tables:**
- `CourseDAO`, `TopicDAO`, `ExaminationDAO`: correct column names.
- `QuestionDAO.SELECT_BASE`: UPPERCASE `QUESTION/MCQ/SHORT_ANSWER`, aliases matching what `mapRow` reads.
- `ScoreDAO`: `CONTAINS` → `PAPER_QUESTION` + `EXAM_QUESTION_MARKS`; "submitted" = `EndTime IS NOT NULL`.
- `MODEL/Attempt.isSubmitted()`: `EndTime != null`. `MODEL/Score`: passed when **%** >= `Pass_mark` (Pass_mark is a %).
- `StudentResultService`: starts an attempt with `EndTime NULL`; MCQs saved as `AnswerText NULL` + `SELECT_TABLE`; not graded = `NULL`.
- `database/student_results/01_seed_test.sql`, `02_cleanup_test.sql`: moved to 21 tables.
- `StudentResultsTestRunner`: expectations follow the 21-table conventions.

**Tests on the merged project (database = the group's 02, MySQL 8 Linux):** all main + test code compiles;
`StudentResultsTestRunner` 56/56; sign-in 6/6; student flow 11/11; manual grading 8/8; create exam/question/paper 7/7;
Reports 11/11 matching the .sql file; JUnit 14/14; the old DAOs read the data correctly (15 courses, 18 topics, 19 exams, ...).

---

## Step 11 – Database clock, paper rotation, prerequisites, connection pool

**#8 – Exam times follow the DATABASE clock, not the user's computer.**
- NEW `Db.now(conn)` = `SELECT NOW()`; every place that used `LocalDateTime.now()` in `StudentServiceImpl` /
  `InstructorServiceImpl` (exam open/close, register, start, save, submit, closing expired attempts, exam status) uses the database time.
- Every connection runs `SET time_zone = '+07:00'` (key `db.timezone`, default +07:00) → `NOW()` is in the same time
  zone as `Exam_date` / `StartTime` even though the Aiven server runs in UTC. The exam countdown already used the seconds returned by the server.
- Verified: server in UTC + computer set to New York time → **old code**: students saw `DEMO - Open now` as UPCOMING,
  "This exam is not open yet"; **new code**: the whole demo script PASSES.

**#9 – Several papers per exam.** `AttemptDAO.pickPaperKey` (replaces `firstPaperKey`): only papers with questions;
prefers the paper that student has used least (a retake gets another paper); ties rotate by the exam's attempt count
(the next student gets a different paper). Marks per question still come from the exam (`EXAM_QUESTION_MARKS`) – as
normalized: the same question in an exam has the same marks on every paper.
Verified: 3 students → papers 1, 2, 1; retake → paper 2; an empty paper is never assigned.

**#11 – Prerequisites (REQUIRES) at registration.** `StudentDAO.missingPrerequisites` + a check in
`StudentServiceImpl.registerForExam`: the student must have **passed** (fully graded, % >= Pass_mark, view `v_attempt_scores`)
at least one exam of **each direct prerequisite course**; a prerequisite course with no PUBLISHED exam is ignored.
Message: "You must pass Data Structures and Algorithms before registering for this exam (prerequisite)."
In the seed: PDM (course 1) requires DSA (course 3); only **sv106, sv111** have passed DSA.

**Connection pool – HikariCP 5.1.0** (+ SLF4J in `pom.xml`). `DatabaseConnection.getConnection()` is used as before
(try-with-resources returns the connection to the pool). At most 5 connections (`db.pool.size`), 10 s wait, refreshed
every 20 minutes; a wrong URL/password fails at once with "Cannot connect to the database: ...".
**After copying: Maven → Reload project** to download HikariCP.

`StudentResultService` (old code) also reads the time from the database.
Tests updated for the prerequisite rule: `EndToEndTest` (sv104 blocked, sv106 can register).

**Tests (MySQL 8, server in UTC like Aiven):** demo script 17/17 (also with the computer set to New York time);
new features 11/11; sign-in 6/6; student flow 11/11; manual grading 8/8; instructor writes 7/7; Reports 11/11;
03_verify 39/39; `StudentResultsTestRunner` 56/56; JUnit 14/14.

Still open from the progress report: #10 – the permission checks in the services are skipped when there is no Session
(accepted for tests; the app always signs in first).

## Step 12 – New demo script + README for members

- `README.md` rewritten: clone → truststore → `application.properties` → Workbench → run the demo seed → run the app,
  accounts table, troubleshooting, list of SQL files, build/test, project structure.
- `docs/DEMO_SCRIPT.md`: demo parts A–E (sv103 takes the exam/Resume, sv101 time-out closed automatically, inst01
  grading/Reports/Statistics/Publish/Question bank, sv103 sees 55/60, sv104 blocked by the prerequisite – sv106 can register).
- `database/demo/03_demo_seed.sql`: `DEMO - Open now` gets a **Paper 2** (same questions, different order) to show paper rotation.
  Tests: seed run twice in a row OK; demo script 17/17; new features 11/11.

## Step 13 – Extra sample data for courses other than PDM (optional)

- `database/demo/04_extra_sample_data.sql`: 14 courses (2–15), each with 6 questions (4 MCQ + 2 short answers) and 2
  finished exams (`<course> Practice Test` 40 days ago, 2 attempts; `<course> Progress Quiz` 12 days ago, 1 attempt).
  In total 84 questions, 28 exams, 147 registrations, 178 attempts, 820 answers, all graded.
- Fixed IDs: Quest_ID 2001–2084, Exam_ID 2001–2028, RegisID 20001–20147 (the app generates MAX + 1, so rows created later never fall inside).
  Re-runnable (removes its own block before inserting); `04_extra_sample_data_cleanup.sql` removes it.
- Follows the app rules: students are only placed in a course after passing its prerequisites (`REQUIRES`); sv104 never
  takes DSA; PDM (course 1, topics 1–4) and the `DEMO - ...` exams are untouched → the demo script is unchanged.
- Tests with the sample data loaded: demo 17/17, new features 11/11, student flow, grading, instructor writes and
  sign-in all pass; Reports Q1–Q11 match the SQL file; smoke test of every screen for 14 instructors + 20 students 34/34.
  Two runs in a row give identical results.

## Step 14 – Submission build without a password + running the demo SQL from the app

- `pom.xml`: the jar **no longer** contains `application.properties`; `database/demo/*.sql` is packed into `sql/` in the jar.
  `slf4j-api` pinned to 2.0.13 (same line as `slf4j-nop`) to remove the `StaticLoggerBinder` warning.
- `DatabaseConnection`: reads the settings from `--config=` / `-Dapp.config` → `application.properties` next to the jar →
  the classpath (IntelliJ, as before). A relative truststore path is resolved from the settings file's folder.
- `Main`: commands `--check`, `--setup-demo`, `--cleanup-demo`, `--add-sample-data`, `--cleanup-sample-data`
  (no command opens the app as before; missing settings are reported in a clear dialog).
- `util/SqlScriptRunner`: runs the SQL files packed in the jar over the app's connection, each file in one transaction
  (error → rollback), printing the result table of SELECT statements.
- `database/demo/03_demo_cleanup.sql` (new): removes the `DEMO - ...` exams.
- `04_extra_sample_data.sql`: multi-row INSERTs (2163 → 351 statements, same data, compared row by row).
- `packaging/` + `make_submission.bat|.sh`: build the `submission/` folder (jar, run.bat, check_connection.bat,
  setup_demo.bat, cleanup_demo.bat, add/cleanup_sample_data.bat, app.sh, RUN_GUIDE.txt, application.properties template).
  `submission/` is in `.gitignore`.
- Tests: jar run with the settings next to it – every command OK, repeated runs OK, a failure half-way rolls back
  cleanly; data loaded by the jar is identical to data loaded by MySQL; demo 17/17, sample-data smoke test 34/34, unit tests 0 failures.

## Step 15 – English documentation

- `README.md`, `docs/DEMO_SCRIPT.md`, this file and the other guides translated to English.
  Vietnamese backups: [README.vi.md](README.vi.md), [docs/DEMO_SCRIPT.vi.md](docs/DEMO_SCRIPT.vi.md).
- `packaging/HUONG_DAN.txt` → `packaging/RUN_GUIDE.txt` (English); `make_submission.bat|.sh` messages in English.
