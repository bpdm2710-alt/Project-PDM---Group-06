# Schema handoff and acceptance tests

Status: proposal pending the decision log. Owners below are proposed teams, not reports of work already completed.

## Contract changes to hand to Database

| Area | Proposed representation | Consumer impact |
| --- | --- | --- |
| Paper | New PaperKey PK; keep Exam_ID, local PaperID; UNIQUE(Exam_ID,PaperID) | Modelers retain conceptual weak identity; Java uses PaperKey as technical identifier |
| Attempt | PK(Attempt_no,RegisID); PaperKey FK; remove Exam_ID; nullable EndTime | Query joins registration for exam, paper for local paper number |
| Contains | PK(PaperKey,Quest_ID); UNIQUE(PaperKey,QuestionOrder); per-paper Marks | Exam paper query uses PaperKey. Change this if D07 chooses exam-level marks |
| Selection | PK(Quest_ID,Attempt_no,RegisID); FK(Quest_ID,Option_no) to options; FK(Quest_ID,Attempt_no,RegisID) to answer | Save answer and selection atomically; one selection per question; do not use Option_no alone |
| Teach | PK(User_ID,Course_ID,Semester), with year/term convention | DAO identifiers and duplicate handling change |
| Registration | UNIQUE(User_ID,Exam_ID), existing RegisID PK | Catch duplicate enrollment in Java |
| User | UNIQUE Email; rename Password_hash_ to Password_hash | Login query and model names align; hash storage implementation belongs to Java |
| Answer | Nullable AnswerText/MarkAwarded with explicit state semantics | MCQ has no artificial text; NULL marks mean ungraded, not zero |
| Requires | Rename Course_ID_1/2 to Prerequisite_ID/Advanced_ID after verifying direction | Modelers check role labels; Query/Java use explicit direction |

Keep phone as VARCHAR, not a number, to retain '+' and leading zeroes. Keep DECIMAL for awarded/max marks. Define positive bounds, MarkAwarded >= 0 when present, Bool_Correct IN(0,1), QuestionOrder/Attempt_no/Option_no positive, EndTime >= StartTime when present. The exact Difficulty range 1–5 is a proposal from the decision document and should be recorded as a rule.

Referential actions must be written on every FK. Prefer RESTRICT for assessed/history-bearing data and a documented atomic deletion routine where needed; choose cascades only when the dependent data should truly be removed and no skipped-trigger rule is required. Do not blanket-CASCADE all relations.

## Data migration and model synchronization

Database should first work on an isolated copy, export schema/data and record the source revision. If the shared database contains meaningful rows, do not run a DROP/CREATE submission script against it.

1. Audit the baseline with [audit_baseline.sql](../../database/review/audit_baseline.sql). Resolve duplicate logical registrations, emails, ordering, subtype overlap/orphans, wrong-exam attempts and invalid selections before adding constraints. Do not silently delete one of several conflicting values.
2. Build the approved target schema separately. Create exactly one PaperKey per old `(Exam_ID,PaperID)` and retain a mapping table/file for migration only.
3. Move papers and contains through that mapping; resolve each attempt through its validated registration examination and old paper identity. Do not use a PaperID-only join.
4. Move selections using `(RegisID,Attempt_no,Quest_ID)`; flag lost/colliding legacy rows for human resolution. Missing selections cannot be recovered from a rejected earlier INSERT.
5. Compare row counts, logical-key sets and marks/totals before/after; verify reverse joins and every FK. Row counts alone do not prove correctness.
6. Update schema diagram, EER design explanations, Workbench model, data dictionary, FD JSON, query scripts and Java in one versioned release.

The assessment script that rebuilds an empty demonstration database is a separate deliverable from a data-preserving migration. The original CREATE IF NOT EXISTS export is neither an automatic schema upgrade nor a complete submission script.

## Enforcement ownership

| Rule | Database mechanism to evaluate | Java/Query duty |
| --- | --- | --- |
| Unique identifiers/pairs/order, valid scalar ranges | PK/FK/UNIQUE/NOT NULL/CHECK | Readable validation errors, no SQL concatenation |
| Assigned paper is in registration exam | Trigger/procedure checking both parents; protect parent exam changes | Display only eligible papers; do not bypass DB checks |
| Answer question belongs to paper; awarded mark within max | DB routine/trigger joining attempt/contains; protect contains/marks updates | Atomic submit/grade; validate numeric range |
| Max attempts | Lock registration during allocation; enforce cap; reject lowering cap below existing attempts | Reuse transaction; retry conflicts without duplicating attempts |
| ISA total/disjoint | Atomic create/delete/retype; disjoint guards; control direct writes; post-operation audit | No partial parent-only create; rollback failures |
| At least two MCQ options, exactly one correct if single-answer | Transactional authoring/finalization procedure; guard deletion/reclassification | Validate before publishing a question |
| Prerequisite no cycles | Transactional graph check, or documented application validation with limits | Reject self-edge and longer cycles; test concurrent changes if supported |
| Paper topic belongs to examination course | Validate with Course–Topic join, protect changes | Filter question bank by course and check again at save |
| Historical stability | Freeze/version referenced questions/options/papers after first attempt | Separate corrections from normal editing; no silent regrade |

An `Attempt_no <= Max_attempts` check alone is insufficient if numbering is zero/negative, duplicated improperly or allocated concurrently. Specify positive local numbering and a lock/transaction policy. Avoid relying solely on an unlocked `MAX(Attempt_no)+1` read.

## Acceptance matrix

Use synthetic data and a separate test database. Each negative test must assert the expected reason/code, not treat every SQL error as success. Run INSERT and UPDATE paths; parent-side updates matter too.

| ID | Owner | Test | Expected result |
| --- | --- | --- | --- |
| N01 | Khang/DB | Two questions select Option_no=1 in the same attempt | Both stored |
| N02 | Khang/DB | Two different options for the same question/attempt | Second rejected under D01 single-select |
| N03 | DB | Select nonexistent option or an option belonging to another question | Rejected |
| N04 | DB | Selection without matching answer row | Rejected or atomic routine creates both; no partial state |
| N05 | DB | Assign paper from a different examination | Rejected on INSERT and UPDATE |
| N06 | DB | Change registration.Exam_ID or paper.Exam_ID after attempts exist | Rejected or controlled operation maintains every affected attempt |
| N07 | DB/Java | Same student registers twice for an exam | Rejected with readable message |
| N08 | DB/Java | Duplicate school email under approved normalization/collation | Rejected; login has one account |
| N09 | DB | Duplicate order within a paper; same order on another paper | First rejected; second allowed |
| N10 | DB | Same instructor/course in two semesters; duplicate same triple | Two allowed; duplicate rejected |
| N11 | DB/Java | Both subtypes, no subtype, child deletion, retyping failure | Approved ISA policy holds after transaction; no partial committed user/question |
| N12 | DB/Java | Answer a question outside assigned paper | Rejected |
| N13 | DB | Award negative marks, marks above maximum; lower maximum below awarded score | Rejected unless an explicit atomic regrade rule is used |
| N14 | DB/Java | Concurrent attempts at last available slot; lower cap below used attempts | At most allowed number commits; parent update guarded |
| N15 | Java/Query | In-progress attempt and ungraded answer | NULL stays distinguishable from finished/zero; no false final total |
| N16 | DB/Java | Self-prerequisite and 3-course cycle | Both rejected under approved policy |
| N17 | DB/Java | Paper question topic not covered by exam course; later remove covers link | Invalid membership rejected or parent edit restricted |
| N18 | DB/Java | Change/delete referenced question, correct option, or paper membership | Historical policy enforced |
| N19 | DB | Missing FK, duplicate PK, Difficulty/Duration/Bool bounds, reversed time | Each rejected by intended constraint |
| N20 | DB + all | Build submission DB twice from scratch, >=15 rows per table, all mandatory queries non-empty | Reproducible result; no manual repair |
| N21 | Java | Submit answers/selection as a transaction with injected failure midway | Rollback restores initial data; no orphan answer/selection |
| N22 | Query | More attempts by one student; ungraded/blank/partial answers; paper totals differ | Denominators and results match approved D09/D11 policy |
| N23 | Khang | Normalization split/rejoin and PaperKey mapping | Original logical tuples recovered exactly; no missing/spurious rows |

The SQL lab covers limited demonstrations related to N01/N02/N05/N23. The matrix is the future full-schema acceptance plan, not a list of tests already passed.

## Contract for the five Topic 20 queries

| Required question | Schema path | Meaning to freeze |
| --- | --- | --- |
| Paper of an examination with marks | examination -> paper -> contains -> question | If several papers exist, choose PaperKey or show separate paper groups; order within each |
| Score of one student in one attempt | student -> registration -> attempt -> answer | The attempt key is RegisID + Attempt_no; SUM ignores NULL, so expose grading completeness separately |
| Questions incorrect for >70% of students | eligible assessed attempts -> answer/selection -> question/options | Choose first/latest/best or a defined student-level rule; count distinct students under that rule, define blank and partial correctness |
| Students attempting every exam of a course | student + nested NOT EXISTS over examination/registration/attempt | Registration alone is insufficient; decide treatment of courses with no exams and incomplete attempts |
| Average score per topic | question/topic + contains + answer/attempt | Define raw vs normalized score; use per-answer maximum from the correct paper; avoid duplication through M:N covers |

A LEFT JOIN that displays unanswered questions must start from the paper's question list, not only existing answer rows. At submission, decide whether blanks become explicit zero-mark answer rows; before submission, absence/NULL must not be treated as proof of an incorrect final answer.

## Final submission cross-check

The assignment additionally asks for 12 SQL queries, 4 relational-algebra versions, >=15 realistic rows in every table, constraints and referential actions, a meaningful index, a two-table transaction, and a runnable Java application with the required forms and at least 4/5 topic queries. These are cross-team obligations; the present normalization contribution does not claim to implement them.
