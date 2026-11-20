# UI rebuild progress

Cách dùng: giải nén zip mới nhất vào thư mục gốc project (ghi đè). Mỗi lần zip là **cộng dồn**.
Biên dịch: `mvn -q compile` (hoặc Build trong IntelliJ).

## Đã xong
- [x] Nền tảng: Service interfaces, Session, AsyncTask, StateView, Fmt, Navigator
- [x] Login (gọi AuthService thật)
- [x] Student shell (StudentDashBoard) + Overview

## Đang làm / việc kế tiếp (theo thứ tự)
- [ ] Student: Courses
- [ ] Student: My exams (màn mới) + Exam (làm bài) + Results
- [ ] Instructor: shell + My courses (màn mới)
- [ ] Instructor: Exams + Build paper
- [ ] Instructor: Question bank
- [ ] Instructor: Statistics
- [ ] Xóa file mock cũ: `ui/panel/CoursesPanel.java`, `ExamPanel.java`, `InstructorCoursesPanel.java` (khi đã có bản thay thế)
- [ ] Sau UI: viết DAO + Service thật theo `docs/ui/01-screen-data-contract.md`, rồi `Services.install(...)` ở `Main`

## Ghi chú
- Chưa có DAO nên mọi màn hình sẽ hiện "Service unavailable" cho đến khi cài service thật. Đó là chủ ý.
- Dashboard khi chưa viết xong trang nào sẽ hiện "being rebuilt on real data".
