# Validation record and AI assistance

Review date: 17 November 2026 (Vietnam). Repository baseline: `main@3fb78481aa4c669ed836472d6bd68a1797ba7cae`.

## What was actually checked

| Check | Result | Scope / limit |
| --- | --- | --- |
| Repository tree and alternate branch | Read successfully | Main contains the Java/JDBC skeleton; other branch is 5 commits behind; shared DB was not inspected |
| Supplied SQL | All 20 table definitions read | No assertion that SQL and the binary Workbench model are identical |
| FD checker | 28/28 scenarios passed | 20 proposed relations are BCNF under the listed assumptions; eight baseline/variant/decomposition scenarios have the expected result |
| Python unit tests | 18 tests passed | Closure, alternate keys, minimal cover, 2NF/3NF/BCNF distinctions, lossless/preservation proofs, full spec and SQL runner error checking |
| Python compile checks | Passed | Syntax only, not a replacement for unit tests |
| Documentation relative links and whitespace | Checked before publication | Links target the contribution files |
| MySQL server installation | MySQL Community Server 8.4.11 found | Different from the planned MySQL 8.0 runtime; no compatibility claim based only on installation |
| SQL review lab | NOT EXECUTED SUCCESSFULLY | Local TCP client returned error 2003/10013; shared-memory client returned error 2038. No SQL outcomes obtained |
| Baseline SQL import and read-only audit | NOT EXECUTED SUCCESSFULLY | Same connection limitation; no claim that constraints or audit results passed |
| Binary `.mwb` | NOT INSPECTED | Modelers must open it and compare the logical/physical mapping |
| Java build, live Aiven, 15-row seed and final queries/UI | NOT VERIFIED | Outside the executed normalization checks; still required in the handoff plan |
| Publish branch / draft PR | BLOCKED | GitHub returned 403 `Resource not accessible by integration` on branch creation. No remote branch or PR was created by this review; use the contribution patch with an account that has write access |

The new tests of the SQL runner use synthetic output to ensure unexpected errors cannot count as success. They do not execute MySQL. Do not report 'all database tests passed' from these unit-test results.

## Reproduce the checks

From the repository root:

```text
python tools/normalization_check.py
python -m unittest discover -s tests -v
python -m py_compile tools/normalization_check.py tools/run_mysql_review.py
```

For the SQL review lab, first choose/create a disposable database in the team's own test environment and configure a MySQL login path. The runner does not create/drop databases and changes only temporary tables in its own session:

```text
python tools/run_mysql_review.py --login-path=pdm-review --database=pdm_normalization_review
```

Expected successful run: nine named numeric outcomes and exactly two deliberate duplicate-key errors (1062), from the old/new selection-key examples respectively. The runner verifies the codes and table names and fails if anything unexpected happens. Its `--force` setting is limited to this intentional-error lab; do not use it to hide errors in a final schema/migration.

To audit the original schema, select its actual name in MySQL Workbench and run `database/review/audit_baseline.sql`. It performs SELECTs only, but an empty result does not prove the database enforces a rule. Run the full acceptance matrix against the approved target schema afterward.

Do not put passwords in terminal arguments, the FD spec or the repository. Use the existing team's credential setup or a local login path. No shared database credentials were used in this review.

## Draft AI-assistance appendix for the final report

The assignment, section 2.6 (pages 9–10), requires disclosure when substantial AI assistance is used. The following text must be updated after the members' actual review; it is not a declaration that human sign-off has already happened.

> AI assistance was used to review the supplied EER/SQL design against the assignment, draft the normalization work plan, propose functional dependencies and candidate keys, draft decomposition explanations, and generate Python checking tools and SQL counterexamples. The automated mathematical checks covered closure, minimal-cover equivalence, candidate-key enumeration, normal-form classification, and two binary decomposition cases. The tools do not establish that the business assumptions are correct. The proposed schema has not yet been accepted by the whole team, and the MySQL lab was not successfully executed in the preparation environment. The group must verify the business rules, recalculate representative keys/proofs by hand, run the database tests, reconcile the Workbench model, and revise these sections before submission.

## Human review record to complete

| Member | Review evidence to add | Current status |
| --- | --- | --- |
| Nguyễn Mạnh Chiến | Approved decision IDs, reviewed lossless/preservation proofs, final section 5–6 edits | Pending human review |
| Trần Hồ Cao Huân | Handwritten closures/minimal-cover reductions and all-key checks; corrections to FD assumptions | Pending human review |
| Nguyễn Đặng Duy Khang | Anomaly cases and MySQL test logs on the approved schema; interpretation of failures | Pending human review |

After each person finishes, record the real date, commit/PR and result. Keep automated checks and personal understanding as separate evidence. Every member must be able to defend the complete design during the presentation.
