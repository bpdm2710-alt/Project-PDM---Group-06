# Student & Results module - test flow

Scope: module 3 (Student, Attempt, Answer, Score).

Decision: **option C - no schema changes**.

- `Score` is calculated from `ANSWER` + `CONTAINS`; there is no `SCORE` table.
- MCQ answers are stored in `ANSWER.AnswerText` as the selected option number.
- `SELECT_TABLE` is not used.

## Files

| File | Purpose |
| --- | --- |
| `01_seed_test.sql` | Creates test data with IDs `9001+` |
| `02_cleanup_test.sql` | Removes all test data |
| `src/test/java/org/example/StudentResultsTestRunner.java` | End-to-end test runner |

## Run

From the project root, with `application.properties` pointing to
`Online_Examination`:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn" -q test-compile

java -cp "target/test-classes:target/classes:$HOME/.m2/repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar:$HOME/.m2/repository/com/google/protobuf/protobuf-java/4.31.1/protobuf-java-4.31.1.jar" \
  org.example.StudentResultsTestRunner
```

The runner seeds the database, executes the test flow, and always runs the
cleanup script in a `finally` block.

Expected result: `56 passed, 0 failed`.

## Test data

- Student `9001`, instructor `9002`, exam `9001`, paper `1`.
- Four questions, 5 marks each: two MCQ and two short-answer.
- `Max_attempts = 2`, `Pass_mark = 10.00` (raw score).
- First attempt: 15/20 (75%), passed.
- Second attempt: 0/20, not passed.
- Aggregate: average 37.50%, best 75.00%, passed attempts 1.

## Manual cleanup

```bash
mysql --host=... --user=... --database=Online_Examination \
  < database/student_results/02_cleanup_test.sql
```

The test runner's cleanup is automatic; the manual script is only a fallback.
