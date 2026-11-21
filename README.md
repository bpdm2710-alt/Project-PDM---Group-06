# Online Examination System — Project PDM, Group 06

> 🇻🇳 Tiếng Việt: [README.vi.md](README.vi.md)

A **Java Swing + MySQL** desktop application for online multiple-choice and short-answer exams:
students register for and take timed exams, instructors build exams, grade short answers, and view reports and statistics.

| | |
|---|---|
| Language / UI | Java 21, Swing |
| Database | MySQL 8.4 on **Aiven** (SSL `VERIFY_CA`), schema `Online_Examination`, **21 tables in BCNF** |
| Database access | JDBC + HikariCP (connection pool) |
| Build | Maven (`maven-shade` → one runnable jar) |

**Main features**
* Role-based sign-in (passwords hashed with PBKDF2-SHA256).
* Students: browse courses, register for exams (with a **prerequisite** check), take timed exams with every answer saved as they go, **Resume** after leaving by accident, view results.
* Exam times follow the **database clock** (+07:00), not the user's computer clock; attempts that run out of time are closed and graded automatically.
* One exam can have **several papers**; each new attempt rotates to another paper.
* MCQs and short answers that match the answer key are graded automatically; the rest go to the instructor's **Grading** tab.
* Instructors: manage exams (Draft → Publish), Build paper, Question bank, **Reports Q1–Q11**, Statistics.

👉 **Step-by-step demo script: [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)**

---

## Contents
1. [Required software](#1-required-software)
2. [Clone the project](#2-clone-the-project)
3. [Create a truststore from the CA certificate](#3-create-a-truststore-from-the-ca-certificate)
4. [Configure `application.properties`](#4-configure-applicationproperties)
5. [Configure MySQL Workbench](#5-configure-mysql-workbench)
6. [Try the demo](#6-try-the-demo)
7. [Demo accounts](#7-demo-accounts)
8. [Troubleshooting](#8-troubleshooting)
9. [SQL files](#9-sql-files)
10. [Build, test and package](#10-build-test-and-package)
11. [Project structure](#11-project-structure)

---

## 1. Required software

* **Git**
* **IntelliJ IDEA** (Community or Ultimate) — Maven is built in, no separate install needed
* **JDK 21** or later (IntelliJ: *File → Project Structure → SDK → Download JDK* if you don't have it)
* **MySQL Workbench 8.x** (to run the demo SQL scripts)
* From the group (shared on **Zalo**): the `ca.pem` file and **your own Aiven username / password**

## 2. Clone the project

```bash
git clone https://github.com/bpdm2710-alt/Project-PDM---Group-06.git
```

Or in IntelliJ: *File → New → Project from Version Control* → paste the URL above → **Clone**.

After opening the project:
1. Wait for IntelliJ to load `pom.xml`. If the libraries are not downloaded automatically, open the **Maven** tab (right side) → click **Reload All Maven Projects** 🔄.
2. Check that *Project Structure → Project SDK* is 21.

Later you only need `git pull` (or *Git → Pull*) followed by **Maven Reload**.

## 3. Create a truststore from the CA certificate

Save `ca.pem` in a folder on your computer, for example `D:\IU\PDMLAB\CA_CER\`.

**Step 1** — Open the Terminal in IntelliJ and check that `keytool` is available:

```powershell
keytool -help
```

**Step 2** — Create a PKCS12 truststore (change the paths to match your computer):

```powershell
keytool -importcert -trustcacerts `
  -alias aiven-mysql-ca `
  -file "D:\IU\PDMLAB\CA_CER\ca.pem" `
  -keystore "D:\IU\PDMLAB\CA_CER\aiven-truststore.p12" `
  -storetype PKCS12
```

* When asked, **choose a truststore password** and remember it (you need it in step 4).
* When asked *Trust this certificate?* type `yes` → Enter.
* Check that `aiven-truststore.p12` has been created.

> `keytool` not found? Use the one inside the JDK: `"<JDK folder>\bin\keytool.exe" ...`

## 4. Configure `application.properties`

1. Copy `src/main/Sources/application.properties.example` → **`src/main/Sources/application.properties`** (same folder).
2. Fill in your details:

```properties
db.url=jdbc:mysql://mysql-224eb3e6-pn8935916-f2fb.h.aivencloud.com:20365/Online_Examination?sslMode=VERIFY_CA
db.username=<username shared on Zalo>
db.password=<password shared on Zalo>
db.ssl.truststore=D:/IU/PDMLAB/CA_CER/aiven-truststore.p12
db.ssl.truststore.password=<the truststore password you chose in step 3>

# optional (defaults shown)
# db.timezone=+07:00
# db.pool.size=5
```

* Use `/` (or `\\`) in the truststore path.
* Each member uses **the account given to them**.

> ⚠️ **Do not commit `application.properties`** (it is already in `.gitignore`). Before committing, check that the
> *Commit* tab does not list this file. The built jar does **not** contain this file (see section 10).

## 5. Configure MySQL Workbench

Create a new connection:
* **Hostname / Port:** the Aiven endpoint (`mysql-224eb3e6-pn8935916-f2fb.h.aivencloud.com`, port `20365`)
* **Username / Password:** the account given to you
* **SSL:** *Use SSL* = `Require and Verify CA`, **SSL CA File** = path to `ca.pem`

<img width="885" height="352" alt="Workbench connection" src="https://github.com/user-attachments/assets/731629a1-e3a7-4742-89b8-7e6a97a91689" />
<img width="830" height="363" alt="Workbench SSL" src="https://github.com/user-attachments/assets/93ce5b89-50f6-443d-9c50-324ffa60e55d" />

Click **Test Connection**. If it succeeds, open the **`Online_Examination`** schema (it must have 21 tables with UPPERCASE names).

Checklist:
- [ ] The project opens in IntelliJ and Maven Reload has no errors
- [ ] `application.properties` is filled in correctly
- [ ] The truststore exists and its path is correct
- [ ] Workbench connects successfully

## 6. Try the demo

The database on Aiven **is already built** (scripts `02` + `05`). You **only need to load the demo data**:

1. Workbench → *File → Open SQL Script* → `database/demo/03_demo_seed.sql` → click ⚡ (**Execute all**).
2. If auto-commit is off in Workbench, also run `COMMIT;`.
3. The final result must list 4 exams: `DEMO - Open now`, `DEMO - Upcoming`, `DEMO - Expired attempt`, `DEMO - Draft`.
4. IntelliJ: open `src/main/java/org/example/Main.java` → click ▶ **Run 'Main.main()'**.
5. Sign in as `sv103@student.hcmiu.edu.vn` / `Stu@123` → **My exams** tab → `DEMO - Open now` → **Start exam**.

Continue with **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)** (parts A → E: taking an exam, time-out, grading, reports, prerequisites).

> ⚠️ The database is **shared**: running `03_demo_seed.sql` **resets the `DEMO - ...` data for the whole group**. Tell the group before running it.
> `DEMO - Open now` stays open for **180 minutes** from the moment the script runs; after that, run the script again.
> Only `sv101`, `sv102` and `sv103` are pre-registered for this exam.

## 7. Demo accounts

| Role | Email | Password |
|---|---|---|
| Instructor (ID 1–15) | `inst01@hcmiu.edu.vn` … `inst15@hcmiu.edu.vn` | `Inst@123` |
| Student (ID 101–120) | `sv101@student.hcmiu.edu.vn` … `sv120@student.hcmiu.edu.vn` | `Stu@123` |

Accounts used in the demo script:

| Account | Used to demo |
|---|---|
| `sv103` | Take `DEMO - Open now`, submit, start a 2nd attempt and **Resume** it, view the result after grading (55/60) |
| `sv101` | The `DEMO - Expired attempt` attempt closed automatically by the system when time ran out (10/40) |
| `inst01` | PDM course: Grading, Reports Q1–Q11, Statistics, Publish `DEMO - Draft`, Question bank |
| `sv104` | Register for `DEMO - Upcoming` → **blocked** because the DSA prerequisite is not passed |
| `sv106` | Register for `DEMO - Upcoming` → succeeds (DSA passed) |

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Cannot connect to the database: ...` | Wrong `application.properties` (username, password, truststore path / password) or no network. The file must be in `src/main/Sources/`. |
| `Email or password incorrect` although typed correctly | Old code → `git pull` + Maven Reload. If the database was just rebuilt: run `05_upgrade_password_hashes.sql` + `COMMIT;`. |
| No exam to take / every card shows **COMPLETED** | The demo seed was not run or more than 180 minutes have passed → run `03_demo_seed.sql` + `COMMIT;` again. Sign in as sv101–sv103. |
| A script ran but the app shows no change | Auto-commit is off in Workbench → `COMMIT;`. |
| IntelliJ shows table/column names in red in `.sql` files or SQL strings | IntelliJ's data source still caches an old schema → *Database* tab → right-click the data source → **Refresh**. Does not affect running the app. |
| Red `import com.zaxxer...` / missing libraries | Maven Reload has not been done. |
| `Unsupported class file major version` / Java errors | The Project SDK is not 21. |

## 9. SQL files

| File | When to use it |
|---|---|
| `database/demo/03_demo_seed.sql` | **Before every demo** (safe to re-run, only touches the `DEMO - ...` exams). Remove with `03_demo_cleanup.sql` |
| `database/demo/04_extra_sample_data.sql` | (optional) Extra data for the 14 courses other than PDM: 84 questions, 28 finished exams, 147 registrations, 178 graded attempts. Re-runnable; remove with `04_extra_sample_data_cleanup.sql`. Does not touch PDM or the `DEMO - ...` exams |
| `database/normalized_21tables/02_online_examination_full_submission.sql` | Builds the whole database from scratch: 21 tables, views, triggers, procedures, seed data (**deletes existing data**; do not run on Aiven without agreeing first) |
| `database/normalized_21tables/05_upgrade_password_hashes.sql` | Right after `02`: turns the seed passwords into PBKDF2 hashes (`Inst@123` / `Stu@123`) |
| `database/normalized_21tables/03_verify_constraints_and_acceptance_N01_N24.sql` | Constraint tests (must show `PASSED=39 / FAILED=0`), run right after `02` on a copy. **Not a demo file** |
| `database/normalized_21tables/01_schema_ddl_for_mwb_import.sql` | DDL only, for Reverse Engineer into an `.mwb` in Workbench |
| `database/queries/PDMProjectQuery.sql` | Analysis queries Q1–Q11 (same as the app's **Reports** tab) + Q12, the view version of Q11 |
| `database/student_results/01_seed_test.sql`, `02_cleanup_test.sql` | Separate test data (IDs 9001+) and its cleanup |

Rebuilding the database from scratch (only when needed): `02` → (optional `03_verify` on a copy) → `05` → `demo/03_demo_seed.sql` → (optional) `demo/04_extra_sample_data.sql` → `COMMIT;`

Schema notes: table names are **UPPERCASE**; only `PAPER.PaperKey` is AUTO_INCREMENT, every other ID is generated by the app as MAX + 1
inside a transaction. Normalization details: [docs/normalization/README.md](docs/normalization/README.md),
diagrams: [docs/design/](docs/design/).

## 10. Build, test and package

```bash
mvn test        # unit tests, no database needed
mvn package     # builds target/Online_Examination_System-1.0-SNAPSHOT-all.jar
```

### The jar contains no password

The jar does **not** include `application.properties`. At start-up the app looks for its settings in this order:

1. The file given with `--config=<path>` (or `-Dapp.config=<path>`)
2. `application.properties` **next to the jar file**
3. `src/main/Sources/application.properties` (when running from IntelliJ, as before)

So **IntelliJ uses your own account for the demo**, while **the submitted build uses its own `application.properties`** placed next to the jar, with an Aiven account created just for the submission. The two files never interfere.
A relative truststore path (e.g. `aiven-truststore.p12`) is resolved from the folder that contains the settings file.

### Building the submission

1. IntelliJ → **Maven** tab → *Lifecycle* → `clean`, then `package`.
2. Run `make_submission.bat` (macOS/Linux: `./make_submission.sh`) → creates the `submission/` folder (already in `.gitignore`).
3. In `submission/`: fill in `application.properties` (the submission account) and copy `aiven-truststore.p12` into it.
4. Test: `check_connection.bat` → `setup_demo.bat` → `run.bat`. Zip the `submission/` folder to submit.

| File in `submission/` | What it does |
|---|---|
| `run.bat` | Opens the app |
| `check_connection.bat` | Tests the connection: settings file, database, table count, database clock |
| `setup_demo.bat` | **Starts a demo session**: recreates the `DEMO - ...` exams (`DEMO - Open now` stays open 180 minutes) |
| `cleanup_demo.bat` | **Ends a demo session**: removes the `DEMO - ...` exams and every attempt on them |
| `add_sample_data.bat` / `cleanup_sample_data.bat` | Adds / removes the sample data for 14 courses |
| `app.sh` | macOS/Linux: `./app.sh --setup-demo` … |
| `RUN_GUIDE.txt` | Short guide for the grader |

These commands run exactly the files in `database/demo/` (packed into the jar), **no Workbench needed**.
Each file runs in one transaction: if anything fails it is rolled back, so no half-done data is left behind.
The submission account needs `SELECT, INSERT, UPDATE, DELETE` on the `Online_Examination` schema.

They also work from IntelliJ: *Run → Edit Configurations → Main → Program arguments* = `--setup-demo`
(or `--check`, `--cleanup-demo`, `--add-sample-data`, `--cleanup-sample-data`).

### End-to-end test

`EndToEndTest` **writes data** → run it only on a **local MySQL**, NEVER on Aiven:
build a local database with `02` + `05` + `demo/03_demo_seed.sql`, create `src/test/resources/application.properties`
pointing to `localhost`, then run `mvn test -Dit.db=local`.

## 11. Project structure

```
src/main/java/org/example/
├── Main.java                 # app entry point (+ command-line commands, section 10)
├── ui/                       # Swing: LoginFrame, StudentDashBoard, InstructorDashBoard, panel/, components/
├── Service/                  # business logic: AuthServiceImpl, StudentServiceImpl, InstructorServiceImpl, Db (database clock)
├── DAO/jdbc/                 # SQL queries: AttemptDAO, ExamDAO, ReportDAO, StudentDAO, ...
├── DAO/, MODEL/              # earlier DAO layer, still used by StudentResultService
├── session/                  # Session (the signed-in user)
└── util/                     # DatabaseConnection (HikariCP), PasswordHasher, SqlScriptRunner
src/main/Sources/             # application.properties(.example)
packaging/                    # .bat files for the submission (make_submission copies them into submission/)
src/test/java/                # unit tests + it/EndToEndTest
database/                     # SQL files (section 9)
docs/                         # DEMO_SCRIPT, normalization, design, ui
```

Change history, step by step: [UPDATE_NOTES.md](UPDATE_NOTES.md).

---

**Group 06 — Principles of Database Management, International University (VNU-HCM)**
