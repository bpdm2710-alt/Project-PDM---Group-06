# Functional dependencies, minimal covers and candidate keys

Draft material for final-report section 5. Review each business assumption before using it as an approved result.

This is the proposed 20-relation contract under D01 single-select MCQ, D02 global `PaperKey` plus natural paper key, D03 repeat teaching, D07 per-paper marks, and D08 uniqueness rules. It is not a claim that the supplied DDL already implements these keys. See [review and decisions](01-review-and-decisions.md).

## Method

For each relation, start from business rules, split every FD into a single attribute on its right-hand side, remove extraneous left-hand attributes, then remove redundant FDs. A minimal cover need not be unique. The sets printed below are one equivalent minimal cover; all implied FDs belong to its closure F+.

A candidate key determines all attributes and has no proper subset that does so. Listing only the declared primary key is insufficient. A primary key is one chosen candidate key; a foreign key is an inclusion constraint and does not by itself establish a functional dependency in the child.

Classical normal forms assume atomic domains and total attribute values. EndTime, AnswerText and MarkAwarded may be NULL for lifecycle reasons; that SQL representation needs separate validation and does not manufacture new keys. No FD is inferred merely because sample values happen to be unique.

Notation: `{A,B}` is an unordered attribute set. Each arrow below has one right-hand attribute. PK column order may be chosen for indexes without changing the mathematical key.

## Complete proposed inventory

| Relation | All candidate keys | Normal form under declared FDs |
| --- | --- | --- |
| `topic` | `{Topic_ID}` | BCNF |
| `user` | `{User_ID}`; `{Email}` | BCNF |
| `instructor` | `{User_ID}` | BCNF |
| `question` | `{Quest_ID}` | BCNF |
| `student` | `{User_ID}` | BCNF |
| `course` | `{Course_ID}` | BCNF |
| `examination` | `{Exam_ID}` | BCNF |
| `registration` | `{RegisID}`; `{User_ID, Exam_ID}` | BCNF |
| `paper` | `{PaperKey}`; `{PaperID, Exam_ID}` | BCNF |
| `attempt` | `{Attempt_no, RegisID}` | BCNF |
| `answer` | `{Quest_ID, Attempt_no, RegisID}` | BCNF |
| `contains` | `{Quest_ID, PaperKey}`; `{PaperKey, QuestionOrder}` | BCNF |
| `covers` | `{Course_ID, Topic_ID}` | BCNF |
| `mcq` | `{Quest_ID}` | BCNF |
| `options` | `{Option_no, Quest_ID}` | BCNF |
| `requires` | `{Prerequisite_ID, Advanced_ID}` | BCNF |
| `select_table` | `{Quest_ID, Attempt_no, RegisID}` | BCNF |
| `short_answer` | `{Quest_ID}` | BCNF |
| `teach` | `{User_ID, Course_ID, Semester}` | BCNF |
| `user_phone` | `{Phone, User_ID}` | BCNF |

## Minimal covers and key proofs

For every single-identifier relation below, the identifier never occurs on the right of a nontrivial FD, so every key must contain it. Its closure is the whole relation; therefore it is the only candidate key. For empty-FD relations, a set determines only itself, so the full heading is the only key. The multi-key cases have separate proofs below.

### topic

`topic(Topic_ID, Content)`

A topic identifier determines its description; descriptions need not be unique.

Minimal cover:

```text
Topic_ID -> Content
```

Key closure(s): `{Topic_ID}+ = all 2 attributes`.

### user

`user(User_ID, FirstName, LastName, DOB, Email, Password_hash)`

D08: each account has one unique, non-null school email.

Minimal cover:

```text
Email -> User_ID
User_ID -> DOB
User_ID -> Email
User_ID -> FirstName
User_ID -> LastName
User_ID -> Password_hash
```

Key closure(s): `{User_ID}+ = all 6 attributes`; `{Email}+ = all 6 attributes`.

### instructor

`instructor(User_ID, Department)`

User_ID is inherited from the User superclass. Department is not a key.

Minimal cover:

```text
User_ID -> Department
```

Key closure(s): `{User_ID}+ = all 2 attributes`.

### question

`question(Quest_ID, Content, Difficulty, Topic_ID, User_ID)`

Each question has one topic and one author. Text is not unique.

Minimal cover:

```text
Quest_ID -> Content
Quest_ID -> Difficulty
Quest_ID -> Topic_ID
Quest_ID -> User_ID
```

Key closure(s): `{Quest_ID}+ = all 5 attributes`.

### student

`student(User_ID, Enrollment_year)`

User_ID is inherited from User; enrollment year is not unique.

Minimal cover:

```text
User_ID -> Enrollment_year
```

Key closure(s): `{User_ID}+ = all 2 attributes`.

### course

`course(Course_ID, CName, Credits)`

Course names are not assumed to be unique.

Minimal cover:

```text
Course_ID -> CName
Course_ID -> Credits
```

Key closure(s): `{Course_ID}+ = all 3 attributes`.

### examination

`examination(Exam_ID, Title, Exam_date, Duration, Pass_mark, Max_attempts, Course_ID)`

Each exam belongs to one course; no uniqueness on title or date is assumed.

Minimal cover:

```text
Exam_ID -> Course_ID
Exam_ID -> Duration
Exam_ID -> Exam_date
Exam_ID -> Max_attempts
Exam_ID -> Pass_mark
Exam_ID -> Title
```

Key closure(s): `{Exam_ID}+ = all 7 attributes`.

### registration

`registration(RegisID, RegisDate, User_ID, Exam_ID)`

Midterm p.3 and D08: one registration per student-examination pair.

Minimal cover:

```text
RegisID -> Exam_ID
RegisID -> RegisDate
RegisID -> User_ID
Exam_ID, User_ID -> RegisID
```

Key closure(s): `{RegisID}+ = all 4 attributes`; `{User_ID, Exam_ID}+ = all 4 attributes`.

### paper

`paper(PaperKey, PaperID, Exam_ID)`

D02 proposal: a new global surrogate plus the original weak-entity natural key.

Minimal cover:

```text
PaperKey -> Exam_ID
PaperKey -> PaperID
Exam_ID, PaperID -> PaperKey
```

Key closure(s): `{PaperKey}+ = all 3 attributes`; `{PaperID, Exam_ID}+ = all 3 attributes`.

### attempt

`attempt(Attempt_no, RegisID, StartTime, EndTime, PaperKey)`

D02 proposal: attempt numbering is local to registration; exactly one assigned paper. Exam_ID is not stored.

Minimal cover:

```text
Attempt_no, RegisID -> EndTime
Attempt_no, RegisID -> PaperKey
Attempt_no, RegisID -> StartTime
```

Key closure(s): `{Attempt_no, RegisID}+ = all 5 attributes`.

Every key component is absent from all right-hand sides. Removing any one prevents recovering that component, so the key is minimal and no other candidate key exists.

### answer

`answer(Quest_ID, Attempt_no, RegisID, AnswerText, MarkAwarded)`

One answer/mark record per question per attempt. Nullable payloads model incomplete state outside classical FD theory.

Minimal cover:

```text
Attempt_no, Quest_ID, RegisID -> AnswerText
Attempt_no, Quest_ID, RegisID -> MarkAwarded
```

Key closure(s): `{Quest_ID, Attempt_no, RegisID}+ = all 5 attributes`.

Every key component is absent from all right-hand sides. Removing any one prevents recovering that component, so the key is minimal and no other candidate key exists.

### contains

`contains(Quest_ID, PaperKey, QuestionOrder, Marks)`

D07 per-paper marks; D08 unique question order per paper. If marks are fixed by examination, use the separate variant.

Minimal cover:

```text
PaperKey, Quest_ID -> Marks
PaperKey, Quest_ID -> QuestionOrder
PaperKey, QuestionOrder -> Quest_ID
```

Key closure(s): `{Quest_ID, PaperKey}+ = all 4 attributes`; `{PaperKey, QuestionOrder}+ = all 4 attributes`.

### covers

`covers(Course_ID, Topic_ID)`

Midterm p.4: a topic may be covered by several courses; each pair occurs once.

Minimal cover:

`Fmin = {}` (no nontrivial FD). The full heading is the only key.

Key closure(s): `{Course_ID, Topic_ID}+ = all 2 attributes`.

### mcq

`mcq(Quest_ID)`

Existence-only subtype membership relation; no nontrivial FDs.

Minimal cover:

`Fmin = {}` (no nontrivial FD). The full heading is the only key.

Key closure(s): `{Quest_ID}+ = all 1 attributes`.

### options

`options(Option_no, Quest_ID, Op_Content, Bool_Correct)`

Option numbers are local to their question, not global. Correctness is not an identifier.

Minimal cover:

```text
Option_no, Quest_ID -> Bool_Correct
Option_no, Quest_ID -> Op_Content
```

Key closure(s): `{Option_no, Quest_ID}+ = all 4 attributes`.

Every key component is absent from all right-hand sides. Removing any one prevents recovering that component, so the key is minimal and no other candidate key exists.

### requires

`requires(Prerequisite_ID, Advanced_ID)`

Directed many-to-many prerequisite relation. No self-edge and acyclicity are not FDs.

Minimal cover:

`Fmin = {}` (no nontrivial FD). The full heading is the only key.

Key closure(s): `{Prerequisite_ID, Advanced_ID}+ = all 2 attributes`.

### select_table

`select_table(Option_no, Quest_ID, Attempt_no, RegisID)`

D01: at most one selected option per question per attempt. The option reference is (Quest_ID, Option_no).

Minimal cover:

```text
Attempt_no, Quest_ID, RegisID -> Option_no
```

Key closure(s): `{Quest_ID, Attempt_no, RegisID}+ = all 4 attributes`.

Every key component is absent from all right-hand sides. Removing any one prevents recovering that component, so the key is minimal and no other candidate key exists.

### short_answer

`short_answer(Quest_ID, Answer_key)`

One current answer key per short-answer question.

Minimal cover:

```text
Quest_ID -> Answer_key
```

Key closure(s): `{Quest_ID}+ = all 2 attributes`.

### teach

`teach(User_ID, Course_ID, Semester)`

D03: the same instructor may teach the same course in different academic-year/term values.

Minimal cover:

`Fmin = {}` (no nontrivial FD). The full heading is the only key.

Key closure(s): `{User_ID, Course_ID, Semester}+ = all 3 attributes`.

### user_phone

`user_phone(Phone, User_ID)`

Phone is multivalued for a user; shared phone numbers are allowed unless the team adds a rule.

Minimal cover:

`Fmin = {}` (no nontrivial FD). The full heading is the only key.

Key closure(s): `{Phone, User_ID}+ = all 2 attributes`.

## Why the alternate-key lists are complete

- `user`: the only ways to obtain User_ID or Email start with one of them. Neither the empty set nor any set of names, DOB and password derives either identifier. Both singleton identifiers determine every attribute.
- `registration`: a key either contains RegisID, or must begin with both User_ID and Exam_ID to derive it. RegisDate cannot derive any identifier. Removing User_ID or Exam_ID from the pair prevents recovery of RegisID.
- `paper`: a key either contains PaperKey, or both Exam_ID and the local PaperID. A local PaperID alone is intentionally not unique.
- `contains`: PaperKey is never on a right-hand side and must be in every key. With it, either Quest_ID or QuestionOrder is needed; Marks derives neither. Thus exactly the two listed keys exist.

## Worked closure for registration

Start `{User_ID, Exam_ID}`. Apply `User_ID, Exam_ID -> RegisID`; then apply `RegisID -> RegisDate` (the other two RegisID dependencies add already-known attributes). The closure is the full heading. `{User_ID}+` and `{Exam_ID}+` contain only themselves, proving minimality. `{RegisID}+` is also the full heading.

## Worked closure and violation in the exported attempt design

Use A=Attempt_no, R=RegisID, E=Exam_ID, P=PaperID, S=StartTime, T=EndTime. The business FDs are `AR -> S`, `AR -> T`, `AR -> P`, `R -> E`. Then `{A,R}+={A,R,S,T,P,E}`, while `{R}+={R,E}`. R is a proper subset of the only key AR and E is non-prime: a partial dependency and a 2NF violation. The current independent FKs do not enforce R -> E; the violation is assessed against the intended same-examination rule.

## Important counterexamples

The current `teach` and `select_table` can look BCNF if their erroneous or overrestrictive exported keys are accepted as business truth. A normal-form proof cannot validate the business rules used to construct it. Correct those rules and their keys first.

If D07 chooses examination-level marks, `Exam_ID, Quest_ID -> Marks` changes the proof for the original `contains`; use the alternate decomposition in [section 6](03-normalization-proofs.md). If multi-select MCQ is chosen, all four select_table attributes become the key and there is no nontrivial FD. Re-run the checker whenever a rule changes.

## Reproduction

From the repository root, with Python 3.10 or newer:

```text
python tools/normalization_check.py
python tools/normalization_check.py --json
python -m unittest discover -s tests -v
```

The JSON specification contains 20 proposed relations plus eight diagnostic/decomposition scenarios. The checker enumerates all determinants for normal-form checking, all minimal keys, and verifies minimal-cover equivalence and irredundancy. This cross-check supplements handwritten reasoning; it cannot infer or approve business rules and does not test SQL constraints.
