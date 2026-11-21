# UI data contract (wave 1)

Principle: the UI **holds no data and no SQL**. Every screen gets its data through
`AuthService`, `StudentService`, `InstructorService` (package `org.example.Service`).
If a real service is not installed, the screen shows a "Service unavailable" error state, never fake data.

Decisions applied: D01 an MCQ has one selected answer · D02 `PaperKey`, the UI never sees PaperID/PaperKey · D07 marks per paper.

## Screen -> service method

| Screen | Method | Notes |
| --- | --- | --- |
| Login | `AuthService.login` | The role comes from the database (student/instructor), the old "email contains chen" logic is gone |
| Student / Overview | `getOverview` | Name, number of courses, number of upcoming exams, average score, upcoming list, latest results |
| Student / Courses | `getCourseFilterOptions`, `listCourses(MY/EXPLORE)`, `listCourseExams` | |
| Student / My exams (NEW) | `listMyExams`, `registerForExam` | |
| Student / Exam | `startAttempt`, `saveAnswer`, `submitAttempt` | The timer uses `secondsRemaining` computed by the server; correct answers are never sent to the UI |
| Student / Results | `listResults` | |
| Instructor / My courses (NEW) | `listTeachingCourses` | |
| Instructor / Exams | `listExams`, `getExam`, `saveExam`, `deleteExam`, `setExamStatus` | |
| Instructor / Build paper | `savePaper`, `searchQuestionBank`, `listTopics` | |
| Instructor / Question bank | `searchQuestionBank`, `getQuestion`, `saveQuestion`, `deleteQuestion` | |
| Instructor / Grading | `listPendingGrading`, `gradeAnswer` | Short answers waiting for a mark (BR-42) |
| Instructor / Reports | `runReport` | Queries Q1–Q11 of `database/queries/PDMProjectQuery.sql` |
| Instructor / Statistics | `getStatistics`, `listStudentMarks` | Matches queries 5, 3 and 4 of the assignment |

## UI elements the schema had no place for (decided by the group)

| UI | Issue | Decision |
| --- | --- | --- |
| Course code "CS201" | `COURSE` only has `Course_ID, CName, Credits` | Show the credits instead of a course code |
| "Midterm / Practice quiz" (exam type) | `EXAMINATION` has no type column | Not shown on exam cards (only in the decorative preview on the login screen) |
| Published / Draft | `EXAMINATION` had no status column | `Status` column added (`DRAFT` / `PUBLISHED`), used by `setExamStatus` |
| "+ Enroll" button for a course | The database only has registration per **exam** (`REGISTRATION`), not per course | "My courses" = courses with a registered exam; the button became "View exams" -> Register |
| Score "/ 100", percentage | Is `Pass_mark` raw marks or a %? (D11) | `Pass_mark` is a **percentage** |

## Errors handled in the service layer (not in the UI)

- `ServiceException.Kind`: VALIDATION, NOT_FOUND, CONFLICT, UNAUTHORIZED, UNAVAILABLE, INTERNAL.
  The message is always safe to show to the user.
- `AttemptResult.fullyGraded = false` means some questions are not graded yet (NULL mark); the score is provisional (N15).
