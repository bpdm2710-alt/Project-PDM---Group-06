# Demo script — Online Examination System (Group 06)

> 🇻🇳 Tiếng Việt: [DEMO_SCRIPT.vi.md](DEMO_SCRIPT.vi.md)

This script is for the code **after Step 11** (exam times from the database clock, several papers per exam, prerequisite
check, connection pool). The whole script has been run automatically (`DemoScenarioIT`, 17/17 PASS).

Length: about 12–15 minutes. One computer running the app is enough (open two app windows to switch roles quickly).

---

## 0. Preparation (5–30 minutes before the demo)

1. `git pull` to get the latest code, then in IntelliJ click **Maven → Reload project**.
2. Open MySQL Workbench, connect to Aiven, open and run the **whole** file
   `database/demo/03_demo_seed.sql` (the lightning icon ⚡, not "execute current statement").
3. If auto-commit is off in Workbench, also run `COMMIT;`.
   *No Workbench?* Run `setup_demo.bat` from the submission folder, or in IntelliJ run `Main` with Program arguments `--setup-demo`.
4. The last result of the script must show 4 rows:

   | Title | Status | registrations | open_attempts |
   |---|---|---|---|
   | DEMO - Open now | PUBLISHED | 3 | 0 |
   | DEMO - Upcoming | PUBLISHED | 0 | 0 |
   | DEMO - Expired attempt | PUBLISHED | 1 | 1 |
   | DEMO - Draft | DRAFT | 0 | 0 |

5. Start the app: `src/main/java/org/example/Main.java` → **Run** (or run the jar).

> **Important**
> * The demo script **resets** the `DEMO - ...` exams on the shared database (Aiven) → tell the group first, and do not run it while someone else is demoing.
> * `DEMO - Open now` is only open for **180 minutes** from the moment the script runs. After that → run the script again.
> * Re-running the script is enough to restart the demo from scratch; there is no need to re-run `02` or `05`.
> * To clean up after the demo: `03_demo_cleanup.sql` (or `cleanup_demo.bat` / `--cleanup-demo`).
> * The file `03_verify_constraints_...` in `normalized_21tables/` is a **constraint test**, NOT a demo file.

Passwords: students `Stu@123`, instructors `Inst@123`.

---

## Part A — A student takes an exam (sv103) · ~4 min

**Sign in:** `sv103@student.hcmiu.edu.vn` / `Stu@123`

| # | Action | Expected result / talking point |
|---|---|---|
| A1 | Open the **My exams** tab | The `DEMO - Open now` card is **OPEN**, 0/2 attempts. Opening/closing times follow the **database clock (+07:00)**, the same on every computer. |
| A2 | Click **Start exam** | The exam screen: 5 questions, a **Time left** countdown (about 2 h 55 min). Attempt 1 gets **Paper 1**. |
| A3 | Answer the 4 MCQs: `HAVING`, `Second Normal Form (2NF)`, `Total participation`, `Relational division` | Every choice is saved to the database at once (`ANSWER` + `SELECT_TABLE`). |
| A4 | Short-answer question (Candidate key): type `a key must be unique and minimal` | Does not match the answer key → left for the instructor to grade by hand. |
| A5 | Click **Submit exam** | Confirmation that it was submitted. Status **GRADING**: the 4 MCQs are graded automatically (40 marks), the short answer waits for grading (BR-42). |
| A6 | Go back to **My exams** | The card is still **OPEN**, 1/2 attempts used (Max attempts = 2). |
| A7 | Click **Start exam** a 2nd time, pick any answer, then **close the window / Log out** (do NOT submit) | Attempt 2 gets **Paper 2** (same questions, different order) → shows **paper rotation**. |
| A8 | Sign in again as sv103 → **My exams** | The card shows **Resume exam**; clicking it keeps the chosen answer, and the timer continues from the real start time. |

You can submit attempt 2 or leave it (if it runs past the time limit, the system closes and grades it — see Part B).

---

## Part B — An attempt closed automatically at time-out (sv101) · ~1 min

**Sign in:** `sv101@student.hcmiu.edu.vn` / `Stu@123`

| # | Action | Expected result |
|---|---|---|
| B1 | **My exams** | `DEMO - Expired attempt` = **COMPLETED** (60-minute exam that opened 3 hours ago; sv101 started but never submitted). |
| B2 | **Results** tab | The system **closed that attempt when time ran out** and graded it: **10 / 40** (only one answer chosen, and it was correct). |
| B3 | (optional) | sv101 also sees `DEMO - Open now` as OPEN → take it to show two students getting two different papers. |

---

## Part C — Instructor (inst01) · ~5 min

**Sign in:** `inst01@hcmiu.edu.vn` / `Inst@123` → choose the course **Principles of Database Management (PDM)**.

| # | Action | Expected result |
|---|---|---|
| C1 | **Exams** tab | The 4 `DEMO - ...` exams appear with the seed exams, as DRAFT/PUBLISHED. |
| C2 | **Grading** tab | sv103's short answer is listed (with the answer key beside it). Enter **15** (max 20) → **Save mark**. |
| C3 | **Reports** tab | Pick Q1 … Q11 one by one → **Run**. These are exactly the queries in `database/queries/PDMProjectQuery.sql`. |
| C4 | **Statistics** tab | Average score per topic, hardest questions. |
| C5 | **Exams** tab → `DEMO - Draft` → **Build paper**: add 2 questions (10 marks each) → **Save paper**; **Edit exam**: set the exam date to tomorrow → **Save**; click **Publish** | Status changes **DRAFT → PUBLISHED**. An exam without a paper/questions cannot be published. |
| C6 | **Question bank** tab → **New question**: create an MCQ with 2 options (1 correct) → **Save** | The new question gets ID = MAX + 1 and appears in the list. |

---

## Part D — The student sees the result after grading (sv103) · ~1 min

**Sign in again** as `sv103@student.hcmiu.edu.vn` → **Results** tab

* Attempt 1: **55 / 60 = 91.7 %**, **Passed** (pass mark 50 %). 40 MCQ marks + 15 marks given by the instructor.
* The representative score comes from the views `v_attempt_scores` / `v_representative_attempts`.

---

## Part E — Prerequisites (sv104 vs sv106) · ~2 min

PDM requires passing **Data Structures and Algorithms** first (table `REQUIRES`). In the seed data only **sv106** and **sv111** have passed DSA.

| # | Account | Action | Expected result |
|---|---|---|---|
| E1 | `sv104@student.hcmiu.edu.vn` | **Courses** → PDM → **View exams** → `DEMO - Upcoming` → **Register** | Blocked: *"You must pass Data Structures and Algorithms before registering for this exam (prerequisite)."* |
| E2 | `sv106@student.hcmiu.edu.vn` | Same as E1 | Registration succeeds; the card becomes **UPCOMING** (the exam opens in 2 days, cannot be started yet). |

---

## Part F (optional) — Other courses besides PDM · ~2 min

Requires `database/demo/04_extra_sample_data.sql` to have been run (once is enough, no need to re-run before each demo).

| # | Account | Action | Expected result |
|---|---|---|---|
| F1 | `inst02@hcmiu.edu.vn` (OOP) | **Exams**, **Question bank**, **Statistics**, **Reports** | `OOP Practice Test` and `OOP Progress Quiz` already taken; 8 questions; average score per topic; Reports have data |
| F2 | `sv115@student.hcmiu.edu.vn` | **Results** | About 25 results across 12 courses, some passed and some failed |

Students are only placed in a course after passing its prerequisites, so the courses at the end of the chain (Data Mining, Distributed Systems) have few students.

## Common questions during the presentation (suggested answers)

* **Why don't exam times depend on the computer?** The app reads the time with `SELECT NOW()` on the database, and every connection sets `time_zone = '+07:00'`.
* **How do several papers work?** Each exam has several `PAPER` rows; a new attempt gets the paper that registration has used least, rotating by the exam's attempt count.
* **Grading?** Blank → 0; matches the key → full marks; anything else → `NULL`, waiting for the instructor (BR-42).
* **Passwords?** Stored as PBKDF2-SHA256 (120,000 iterations, salted), never as plain text.
* **How are IDs generated?** Only `PAPER.PaperKey` is AUTO_INCREMENT in the schema; other IDs are MAX + 1 inside a transaction.
* **Database connection?** HikariCP pool (5 connections by default), SSL `VERIFY_CA` to Aiven.

## If something goes wrong during the demo

| Symptom | Fix |
|---|---|
| No `DEMO - Open now`, or every card shows COMPLETED | More than 180 minutes have passed / the seed was not run → run `03_demo_seed.sql` + `COMMIT;` again. Only sv101–sv103 are registered for this exam. |
| "Email or password incorrect" | The database has not had `05_upgrade_password_hashes.sql` run, or the code is old → `git pull`, Maven Reload. |
| "Cannot connect to the database" | Check `application.properties`, the truststore path and password, and the network. |
| Grading does not show sv103's answer | sv103 has not submitted attempt 1, or a different course is selected (choose PDM). |
