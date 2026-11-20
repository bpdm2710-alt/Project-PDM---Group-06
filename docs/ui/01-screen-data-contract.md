# UI data contract (wave 1)

Nguyên tắc: UI **không chứa dữ liệu và không chứa SQL**. Mọi màn hình lấy dữ liệu qua
`AuthService`, `StudentService`, `InstructorService` (package `org.example.Service`).
Chưa cài service thật thì màn hình hiện trạng thái lỗi "Service unavailable", không có dữ liệu giả.

Quyết định đã áp dụng: D01 MCQ chọn một đáp án · D02 `PaperKey`, UI không thấy PaperID/PaperKey · D07 điểm theo paper.

## Màn hình -> hàm service

| Màn hình | Hàm | Ghi chú |
| --- | --- | --- |
| Login | `AuthService.login` | Role lấy từ DB (student/instructor), bỏ logic "email chứa chen" |
| Student / Overview | `getOverview` | Tên, số môn, số kỳ thi sắp tới, điểm TB, danh sách sắp thi, kết quả mới nhất |
| Student / Courses | `getCourseFilterOptions`, `listCourses(MY/EXPLORE)`, `listCourseExams` | |
| Student / My exams (MỚI) | `listMyExams`, `registerForExam` | Hiện chưa có màn danh sách |
| Student / Exam | `startAttempt`, `saveAnswer`, `submitAttempt` | Đồng hồ dùng `secondsRemaining` do server tính; không bao giờ gửi đáp án đúng xuống UI |
| Student / Results | `listResults` | Hiện chỉ là placeholder |
| Instructor / My courses (MỚI) | `listTeachingCourses` | Hiện UI nhảy thẳng vào một môn |
| Instructor / Exams | `listExams`, `getExam`, `saveExam`, `deleteExam`, `setExamStatus` | |
| Instructor / Build paper | `savePaper`, `searchQuestionBank`, `listTopics` | |
| Instructor / Question bank | `searchQuestionBank`, `getQuestion`, `saveQuestion`, `deleteQuestion` | Hiện ghi "will be implemented later" |
| Instructor / Statistics | `getStatistics`, `listStudentMarks` | Khớp query 5, query 3, query 4 của đề |

## UI vẽ ra nhưng schema chưa có chỗ chứa (cần nhóm quyết định)

| UI | Vấn đề | Đề xuất |
| --- | --- | --- |
| Mã môn "CS201" | `COURSE` chỉ có `Course_ID, CName, Credits` | Hiện số tín chỉ thay mã môn, hoặc thêm cột `Course_Code` |
| "Midterm / Practice quiz" (loại kỳ thi) | `EXAMINATION` không có cột loại | Bỏ nhãn này, hoặc thêm cột `Exam_Type` |
| Published / Draft | `EXAMINATION` không có cột trạng thái | Thêm cột `Status` (cần chốt, `setExamStatus` đang chờ) |
| Nút "+ Enroll" vào môn học | DB chỉ có đăng ký theo **kỳ thi** (`REGISTRATION`), không có đăng ký theo môn | "My courses" = môn đã đăng ký thi; nút Enroll đổi thành "View exams" -> Register |
| Điểm "/ 100", phần trăm | `Pass_mark` là điểm thô hay %? (D11) | Chốt trước khi viết `AttemptResult.passed` |

## Lỗi cần xử lý ở tầng service (không phải UI)

- `ServiceException.Kind`: VALIDATION, NOT_FOUND, CONFLICT, UNAUTHORIZED, UNAVAILABLE, INTERNAL.
  Message luôn an toàn để hiển thị cho người dùng.
- `AttemptResult.fullyGraded = false` nghĩa là còn câu chưa chấm (NULL mark), điểm chỉ là tạm thời (N15).
