# UI rebuild progress

Cách dùng: giải nén zip mới nhất vào thư mục gốc project (ghi đè). Mỗi lần zip là **cộng dồn**.
Biên dịch: `mvn -q compile` (hoặc Build trong IntelliJ).

## Đã xong (UI, build OK)
- [x] Nền tảng: Service interfaces, Session, AsyncTask, StateView, Fmt, Navigator, Dialogs, ExamCardView
- [x] Login, Student: Overview, Courses, My exams, Exam (làm bài), Results
- [x] Instructor: shell, My courses, Exams (tạo/sửa/xóa/publish), Build paper, Question bank, Statistics
- [x] Đã xóa các file mock cũ (CoursesPanel, ExamPanel, InstructorCoursesPanel)
- [x] `Main` mở LoginFrame

## Việc kế tiếp
- [ ] Viết DAO + ServiceImpl theo `01-screen-data-contract.md`, rồi `Services.install(...)` trong `Main`
- [ ] Làm từng lát: Auth+Overview -> Courses/My exams -> Exam/Results -> Instructor
- [ ] Chốt các quyết định schema (Status, Pass_mark, mã môn)

## Ghi chú
- Chưa cài service thật nên mọi màn hình hiện "Service unavailable". Đó là chủ ý.
- Chưa kiểm thử với dữ liệu thật: mới biên dịch và chụp thử giao diện Student/Exam.

## Cách dùng zip này
Giải nén vào thư mục gốc project (ghi đè). Sau đó **xóa tay** 3 file mock cũ nếu còn:
`src/main/java/org/example/ui/panel/CoursesPanel.java`, `ExamPanel.java`, `InstructorCoursesPanel.java`.
Build: `mvn -q compile`.
