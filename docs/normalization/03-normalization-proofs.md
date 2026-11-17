# Normalization and decomposition proofs

Draft material for final-report section 6. The proposed key/FD inventory is in [section 5](02-fd-keys.md). Normal forms are relative to declared business dependencies, not to one sample dataset or the presence of a generated PK.

## 1. What is already well separated

The current design maps the multivalued Phone into `user_phone`, decomposes composite FullName into atomic name fields, stores options separately from questions, models M:N relationships as bridge tables, and derives Paper.TotalMarks/Attempt.Total_Score rather than duplicating them. These are useful choices to retain.

One row per phone is 1NF; a Phone string holding a comma-separated list would not represent the intended atomic domain. Splitting Phone is also motivated by its multivalued nature; do not invent `User_ID -> Phone` as an FD. Multiple values per user are explicitly allowed.

No decomposition is needed merely to demonstrate effort. Under the approved rules, a relation already in BCNF should stay as it is. Adding a surrogate key also does not remove dependencies among other attributes.

## 2. Definitions used

- 1NF: atomic values with no repeating groups under the intended domains.
- 2NF: 1NF, and no non-prime attribute depends on a proper subset of any candidate key.
- 3NF: for every nontrivial FD X -> A, X is a superkey or A is prime (belongs to some candidate key).
- BCNF: for every nontrivial FD X -> A, X is a superkey.

For a binary decomposition R into R1/R2 under F, losslessness holds when the common attributes determine R1 or R2 in F+. Dependency preservation is a separate question: the union of the projected dependencies must imply the original F. A lossless join does not automatically preserve every FD.

## 3. Attempt: partial dependency in the baseline

Write the baseline relation as:

```text
Attempt(A, R, S, T, P, E)
A = Attempt_no; R = RegisID; S = StartTime; T = EndTime
P = PaperID; E = Exam_ID
Fmin = { AR -> S, AR -> T, AR -> P, R -> E }
Only candidate key = AR
```

`R -> E` follows from one registration belonging to one examination and the rule that its attempts use papers of that examination. R alone does not determine the attempt number, times or paper. Therefore R is not a superkey, E is non-prime, and this dependency violates 2NF (and hence 3NF/BCNF).

Important distinction: the supplied independent FKs permit rows inconsistent with `R -> E`. The proof uses the intended business rule, while the SQL counterexample demonstrates that the rule is not yet enforced.

### Formal decomposition

```text
RegistrationExam(R, E)
AttemptCore(A, R, S, T, P)
```

The intersection is `{R}`. Since `R -> RE`, the decomposition is lossless. Projected dependencies are `R -> E` in RegistrationExam and `AR -> S,T,P` in AttemptCore, so their union preserves the complete minimal cover. RegistrationExam is BCNF because R is its key. AttemptCore is BCNF because AR is its only key and the only nontrivial determinants are superkeys.

Registration already contains `(R,E)` with R as its key. Thus implementation can reuse its projection instead of introducing a redundant second table. Registrations without attempts remain representable. Rejoining attempts to their existing registration reconstructs the original examination value for all valid rows.

This decomposition needs an inclusion/business constraint: `(registration.Exam_ID, attempt.PaperID)` must identify an existing paper. Removing a column must not silently remove the assigned-paper rule.

### Physical proposal using PaperKey

```text
Paper(PaperKey, Exam_ID, PaperID)
PK PaperKey; UNIQUE(Exam_ID, PaperID)

Attempt(Attempt_no, RegisID, StartTime, EndTime, PaperKey)
PK(Attempt_no, RegisID)
FK RegisID -> Registration
FK PaperKey -> Paper
```

The PaperKey alternative is an implementation redesign following the decomposition, not literally the same attribute-preserving decomposition. The bijection between PaperKey and `(Exam_ID, PaperID)` must be populated and verified during migration. Keep both the new PK and the old natural UNIQUE key.

Do not keep Exam_ID in Attempt after adding PaperKey: the old partial dependency remains, and PaperKey would also determine Exam_ID. The proposal removes it. It still needs `Registration.Exam_ID = Paper.Exam_ID` enforced by a controlled transaction/trigger path, including parent updates and concurrency.

Dependency preservation of the mathematical split does not mean every cross-table rule becomes a declarative MySQL FK. Document this distinction in the presentation.

## 4. Contains: two possible business interpretations

### Per-paper Marks, as stated in midterm

Before surrogate mapping:

```text
C(Exam_ID, PaperID, Quest_ID, QuestionOrder, Marks)
EPQ -> QuestionOrder
EPQ -> Marks
EPQuestionOrder -> Quest_ID
```

Both `EPQ` and `EPQuestionOrder` are candidate keys if order is unique within a paper. Every nontrivial determinant is a superkey, so C is BCNF. Marks must stay in Contains: `Quest_ID -> Marks` is not a valid dependency when a question may carry different marks in different papers.

With PaperKey, replace `(E,P)` by its global key in Contains. The proposed keys are `(PaperKey, Quest_ID)` and `(PaperKey, QuestionOrder)`; the same reasoning applies.

### Examination-level Marks, if that is the approved rule

If a question always has the same mark within an examination, add `EQ -> Marks`. EQ is a proper subset of EPQ; Marks is non-prime. The baseline C then violates 2NF.

Decompose:

```text
ExamQuestionMarks(E, Q, Marks)
PaperQuestion(E, P, Q, QuestionOrder)
```

The intersection EQ determines all of ExamQuestionMarks, so the split is lossless. The projected dependencies `EQ -> Marks`, `EPQ -> QuestionOrder`, `EPQuestionOrder -> Q` preserve the original cover. Both resulting relations are BCNF. The full schema has an extra marks relation; do not retain the 20-table count as a requirement.

If PaperKey is also adopted, preserve the exam/question pricing membership rule through the Paper mapping and controlled writes. Do not reintroduce duplicate Marks in each paper or assume a lossless proof alone enforces that membership.

## 5. Remaining proposed relations and 3NF versus BCNF

The complete table in section 5 covers all 20 proposed relations under the per-paper variant. Single-identifier relations have only key determinants. Registration, User, Paper and Contains have alternate keys; each determinant in their minimal covers is a key. Bridge/membership relations with no nontrivial FDs are BCNF. Selection is BCNF under the single-select dependency `(Quest_ID, Attempt_no, RegisID) -> Option_no`.

Thus no proposed relation needs to stop at 3NF under the stated FD set. Do not invent a 3NF-only exception to satisfy the report format. If a new rule creates one, recompute all keys and show the dependency-preservation tradeoff.

For explanation only, not an added project relation: in R(A,B,C) with `AB -> C` and `C -> B`, the keys are AB and AC. R is 3NF because B is prime, but not BCNF because C is not a superkey. Splitting into AC and BC is lossless (common C determines BC) but loses local enforcement of `AB -> C`. The checker tests this example to distinguish the two properties. It is not a justification for leaving Attempt below 2NF.

## 6. Anomaly examples: two cases of each type

The wide relation below is a teaching example reconstructed from the domain, not a claim that all these columns are currently stored in one SQL table. It is already in 1NF but not normalized; an UNF precursor would store an `Attempts = {...}` repeating group in each exam row before flattening.

```text
ExamAttemptFlat(Exam_ID, ExamTitle, Course_ID, CName, RegisID, Attempt_no)
F includes Exam_ID -> ExamTitle,Course_ID; Course_ID -> CName;
           RegisID -> Exam_ID; key (RegisID,Attempt_no)
```

| Exam_ID | ExamTitle | Course_ID | CName | RegisID | Attempt_no |
| --- | --- | --- | --- | --- | --- |
| 10 | PDM Midterm | 1 | PDM | 500 | 1 |
| 10 | PDM Midterm | 1 | PDM | 500 | 2 |
| 20 | Java Quiz | 2 | Java | 600 | 1 |
| 20 | Java Quiz | 2 | Java | 600 | 2 |

Each scenario starts from an independent fresh copy of these four rows.

| Type / case | Concrete operation | Before normalization | After separating Course, Examination, Registration and Attempt |
| --- | --- | --- | --- |
| Update U1 | Change exam 10 title, updating only `(500,1)` | Two rows for exam 10 disagree | One Examination row contains the title |
| Update U2 | Rename course 2, updating only `(600,1)` | Two rows for course 2 disagree | One Course row contains the name |
| Insert I1 | Create exam 30/PDM Final for course 1, with no registration | Cannot supply the required attempt key without inventing an attempt | Insert Examination independently |
| Insert I2 | Create exam 40/Java Final for course 2, with no registration | Same insertion anomaly for a second new exam | Insert Examination independently |
| Delete D1 | Delete both attempts `(500,1)` and `(500,2)` | Last copy of exam 10 and course 1 facts disappears | Examination and Course remain |
| Delete D2 | Delete both attempts `(600,1)` and `(600,2)` | Last copy of exam 20 and course 2 facts disappears | Examination and Course remain |

For the repeated DDL-specific issue, two attempts with the same RegisID can currently carry two Exam_ID values. The temporary-table lab demonstrates this witness and a lossless reconstruction for valid rows. Database/Khang should additionally capture before/after results on the approved full schema, using the acceptance matrix; those future results are not claimed here.

### Lossless steps for the flat teaching example

1. Split Course(Course_ID,CName). The common Course_ID determines Course, so this step is lossless.
2. Split Examination(Exam_ID,ExamTitle,Course_ID) from the remaining relation. The common Exam_ID determines Examination, so this step is lossless.
3. Split RegistrationExam(RegisID,Exam_ID) from AttemptIdentity(RegisID,Attempt_no). RegisID determines RegistrationExam, so this step is lossless.

Each step retains its motivating FD within a component, so the listed cover is dependency preserving. Registration's other real attributes can then be modeled as documented in section 5; do not claim this reduced teaching example is the entire final schema.

## 7. Rules normalization cannot prove

Normal forms do not prove: same-exam assignment; answer membership in a paper; at most Max_attempts; correctness/number of MCQ options; disjoint/total ISA; prerequisite acyclicity; lifecycle or time checks; enrollment eligibility; concurrent attempt allocation; or stable historical grading. These need explicit constraints, transactions, test cases and documented ownership.

Derived totals are an aggregation design decision, not an ordinary FD decomposition. Store atomic awarded marks once and derive totals. Midterm's Java task 'calculate and store scores' should mean storing Answer.MarkAwarded; writing a second Attempt.Total_Score column would conflict with midterm's derived-only decision unless separately justified.
