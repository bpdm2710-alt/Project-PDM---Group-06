# UI rebuild progress

Cách dùng: giải nén zip mới nhất vào thư mục gốc project (ghi đè). Mỗi lần zip là **cộng dồn**.
Biên dịch: `mvn -q compile` (hoặc Build trong IntelliJ).

## Đã xong (UI, build OK)
- [x] Nền tảng: Service interfaces, Session, AsyncTask, StateView, Fmt, Navigator, Dialogs, ExamCardView
- [x] Login, Student: Overview, Courses, My exams, Exam (làm bài), Results
- [x] Instructor: shell, My courses, Exams (tạo/sửa/xóa/publish), Build paper, Question bank, Statistics
- [x] Đã xóa các file mock cũ (CoursesPanel, ExamPanel, InstructorCoursesPanel)
- [x] `Main` mở LoginFrame

## Data layer (đã có schema thật từ DB)
- [x] `AuthServiceImpl` (đăng nhập), `StudentServiceImpl`: Overview, My exams, Results. `Main` đã `Services.install(...)`
- [x] Sửa `UserDAO`: cột đúng là `Password_hash_`
- [x] **Patch 01** `StudentServiceImpl`: getCourseFilterOptions, listCourses (MY/EXPLORE + lọc), listCourseExams, registerForExam (trùng đăng ký -> CONFLICT, đề đã đóng -> CONFLICT). Đã biên dịch OK, chưa chạy với DB thật
- [x] **Patch 02** `StudentServiceImpl`: startAttempt (resume/mới, giới hạn lượt, cửa sổ thi), saveAnswer (upsert, chặn khi hết giờ/đã nộp), submitAttempt (tự chấm MCQ + short answer, nộp 2 lần vẫn an toàn). **StudentService đã đủ.** Chưa chạy với DB thật
- [x] **Patch 03** Chạy `database/migrations/02_add_exam_status.sql` TRƯỚC (thêm cột Status, cho phép Exam_date NULL). `InstructorServiceImpl`: My courses, Exams tab (list/detail/save/delete/publish). Sinh viên chỉ thấy/đăng ký/làm kỳ thi PUBLISHED. Publish cần có ngày + đề có câu hỏi. `Main` đã cài InstructorServiceImpl
- Quy ước: attempt đang làm có `EndTime = StartTime`, nộp xong `EndTime > StartTime` (vì EndTime NOT NULL); điểm = SUM(MarkAwarded)/SUM(Marks); đậu khi điểm >= Pass_mark (điểm thô)
- Chưa chạy thử với DB thật

## Việc kế tiếp
- [ ] InstructorServiceImpl: còn savePaper + Question bank (patch 04), Statistics + Student marks (patch 05)
- [ ] Chốt các quyết định schema (Pass_mark, mã môn). Status đã chốt: cột `EXAMINATION.Status` DRAFT/PUBLISHED

## Ghi chú
- Chưa cài service thật nên mọi màn hình hiện "Service unavailable". Đó là chủ ý.
- Chưa kiểm thử với dữ liệu thật: mới biên dịch và chụp thử giao diện Student/Exam.

## Cách dùng zip này
Giải nén vào thư mục gốc project (ghi đè). Sau đó **xóa tay** 3 file mock cũ nếu còn:
`src/main/java/org/example/ui/panel/CoursesPanel.java`, `ExamPanel.java`, `InstructorCoursesPanel.java`.
Build: `mvn -q compile`.
