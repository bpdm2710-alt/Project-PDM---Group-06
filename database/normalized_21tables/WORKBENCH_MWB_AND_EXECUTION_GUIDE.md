# Creating a new `.mwb`, running the normalized SQL & testing in MySQL Workbench 8.0 CE

This folder contains the complete normalized SQL scripts for the **21-table BCNF** design (see the summary of rules and the mapping for Java/Query in [docs/normalization/README.md](../../docs/normalization/README.md)).

---

## Files in this folder

| File | Purpose |
| --- | --- |
| `01_schema_ddl_for_mwb_import.sql` | **Pure DDL (only CREATE TABLE + constraints + indexes)**, no `DELIMITER`/triggers/seed — used to import into MySQL Workbench and generate a clean `.mwb` file without syntax errors. |
| `02_online_examination_full_submission.sql` | **Complete submission script (Deliverable D5)**: `DROP/CREATE DATABASE Online_Examination`, 21 tables, 28 FKs (all with `ON DELETE`/`ON UPDATE`), `UNIQUE`, `CHECK`, 5 indexes, 3 views, two-way protection triggers, 5 ACID stored procedures and sample data with **`>= 15` rows in all 21/21 tables**. |
| `03_verify_constraints_and_acceptance_N01_N24.sql` | Automatic tests: row counts `>= 15`, the 5 required queries of Topic 20, and 39 checks `N01–N24` + `BR-29/37/41/42/45/49` (duplicate PK, wrong FK, failed CHECK, circular trigger, wrong exam, history freeze, grading of blank/tab/CRLF answers...). |
| `05_upgrade_password_hashes.sql` | Run right after `02`: turns the seed passwords into PBKDF2 hashes (`Inst@123` / `Stu@123`). Safe to re-run. |

> The in-place migration script `04_migration_from_old_20tables_to_21tables.sql` was used once to move the shared
> Aiven database from the old 20-table schema to 21 tables, and has since been removed from the project.

> **Independent verification on MySQL 8.4.11:** `01` runs cleanly (exit code `0`); `02` runs **twice in a row** without errors, with `>= 15` rows in all `21/21` tables; `03` returns `ACCEPTANCE_SUMMARY|PASSED=39|FAILED=0`.

---

## 1. Creating a new `.mwb` file & exporting the schema diagram in MySQL Workbench

### Method A — Import directly from `01_schema_ddl_for_mwb_import.sql` (fastest, no database connection needed)
1. Open **MySQL Workbench 8.0 CE**.
2. Choose **File → Import → Reverse Engineer MySQL Create Script...**
3. Select `01_schema_ddl_for_mwb_import.sql`, tick **`Place imported objects on a diagram`**, then click **Execute → Next → Finish**.
4. Use **Arrange → Autolayout** (tidy the positions of the 21 tables, drag the connectors so they do not cross the text).
5. Click **File → Save Model As...** and save as `tables_script_v2_21tables.mwb`.
6. Click **File → Export → Export as PNG...** to export the new schema diagram (showing `Quest_ID`, `User_ID`, `Semester`, `PaperKey`, `exam_question_marks`, `paper_question`) for the Final Report.

### Method B — Reverse Engineer from the MySQL server after running `02` (also gets views, triggers, procedures)
1. Open a MySQL server connection in Workbench (local, or a separate test schema) and run the whole `02_online_examination_full_submission.sql`.
2. From the main menu, choose **Database → Reverse Engineer...** (`Ctrl+R`).
3. Pick the connection → select the schema **`Online_Examination`** → tick Tables, Views, Routines and Triggers → **Execute → Finish**.
4. Save the `.mwb` file and export the relational diagram.

---

## 2. Running & re-checking the D5 script (Assignment section 2.3)

1. Open `02_online_examination_full_submission.sql` in MySQL Workbench (or the `mysql` CLI) and click **Execute All (`Ctrl+Shift+Enter`)** once.
2. Click **Execute All** again — **a second time in a row** — the script must run cleanly without editing any line by hand.
3. Open and run `03_verify_constraints_and_acceptance_N01_N24.sql` to see the row count of every table, the results of the 5 Topic 20 queries and `ACCEPTANCE_SUMMARY|PASSED=39|FAILED=0` — take a screenshot of this for Chapter 4 of the Final Report.

---

## 3. Safety notes for the shared Aiven database

* **DO NOT run** `02_online_examination_full_submission.sql` (it contains `DROP DATABASE`) on the Aiven server while the shared database holds the group's data, unless it has been backed up first.
* Back up first (Workbench → Server → Data Export). For a demo, the files in `database/demo/` are enough: they only touch their own rows.
