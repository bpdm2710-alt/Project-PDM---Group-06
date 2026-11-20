# Patches đã làm (lưu từng phần)

Cách dùng: chép file trong `src/...` đè vào project (cùng đường dẫn). Mỗi patch là bản **cộng dồn**, file mới nhất đã gồm các patch trước.

## Patch 04 – Question bank + Paper builder  ✅ (biên dịch OK, chưa chạy với DB thật)
File: `src/main/java/org/example/Service/InstructorServiceImpl.java`

Đã implement: `listTopics`, `searchQuestionBank`, `getQuestion`, `saveQuestion`, `deleteQuestion`, `savePaper`.

Quy tắc đã chọn (có thể đổi):
- Câu hỏi thuộc "ngân hàng của môn" nếu `TOPIC` của nó nằm trong `COVERS` của môn đó. Instructor chỉ thấy/sửa câu hỏi của topic thuộc môn mình dạy (`TEACH`).
- MCQ: 2–10 option, đúng 1 đáp án đúng, không trùng nội dung. Short answer: bắt buộc có model answer. Độ khó 1–5.
- Sửa câu hỏi đã có sinh viên trả lời: chỉ cho sửa nội dung + độ khó; không cho đổi loại/topic/option/đáp án.
- Xóa câu hỏi: chặn nếu đã có `ANSWER`, hoặc đang nằm trong exam đã PUBLISHED. Nếu chỉ nằm trong đề nháp thì tự gỡ khỏi đề rồi xóa.
- `savePaper`: paperKey = PaperID; null = tạo paper mới (PaperID = max+1). Chặn nếu exam đã có người làm. Thứ tự câu được chuẩn hóa thành 1..n. Marks 0 < m ≤ 9999.99. Chỉ nhận câu hỏi thuộc ngân hàng của môn.

## Patch 05 – Statistics + Student marks  ✅ (biên dịch OK, chưa chạy với DB thật)
File: `src/main/java/org/example/Service/InstructorServiceImpl.java` (cộng dồn 03 + 04 + 05; **InstructorService giờ đã đủ 14/14 method**, đã xóa hàm `notYet`).

Đã implement: `getStatistics`, `listStudentMarks`.

Quy tắc đã chọn (chốt D09 như sau, có thể đổi):
- Mỗi sinh viên + mỗi kỳ thi chỉ tính **lần làm tốt nhất** (điểm cao nhất; bằng điểm thì lần sau). Lần làm xong = `EndTime > StartTime`. Đề DRAFT bị bỏ qua.
- Average by topic = tổng điểm đạt / tổng điểm tối đa của các câu thuộc topic (%), nên các đề khác tổng điểm vẫn so sánh được.
- Hardest questions: tối đa 5 câu, % sinh viên được trọn điểm thấp nhất; câu mà ai cũng đúng không liệt kê.
- Student marks: cột = các exam PUBLISHED của môn; hàng = sinh viên đã đăng ký ít nhất 1 exam đó; "Completed all" = đã có điểm ở mọi cột.

## Việc cần làm khi chép vào máy
1. Chép đè file `InstructorServiceImpl.java`.
2. Build bằng IntelliJ/Maven. Chạy thử các tab: Question bank, Paper builder, Statistics bằng tài khoản `instructor.test@example.com`.
3. Lưu ý dữ liệu seed hiện chỉ có 1 môn test (ID 9001) nên Statistics sẽ trống đến khi có sinh viên làm bài xong.

## Seed test viết lại  ✅
Files: `database/student_results/01_seed_test.sql`, `02_cleanup_test.sql` (schema 20 bảng).
- Thêm `TEACH` + `COVERS` (nguyên nhân màn "No courses assigned"), 3 user, 2 môn, 6 câu hỏi, 4 exam (đã thi / đang mở / sắp tới / draft), 3 đăng ký, 3 attempt có điểm để Results + Statistics có dữ liệu.
- Chạy lại nhiều lần được (đầu file tự xóa dữ liệu seed cũ, gồm cả câu hỏi/exam tạo thêm bằng app trong 2 môn seed).
- `StudentResultsTestRunner` (code cũ) có thể không còn khớp dữ liệu mới vì nó tự chạy seed cũ và kiểm tra số liệu cũ.

## DAO – bước 1: các class DAO mới  ✅ (biên dịch OK)
Thư mục mới: `src/main/java/org/example/DAO/jdbc/` (7 file): `Jdbc` (helper), `UserDAO`, `CourseDAO`, `ExamDAO`, `PaperDAO`, `QuestionDAO`, `StatsDAO`.
- Đặt trong package con `DAO.jdbc` để KHÔNG đụng các DAO cũ (UserDAO, CourseDAO... dùng schema cũ) còn nằm trong `DAO/`.
- Mỗi method nhận `Connection` (service giữ transaction), chỉ chứa SQL, trả về record/giá trị đơn giản, không có luật nghiệp vụ.
- Schema 20 bảng. Các query trong `PDMProjectQuery.sql` là cho schema 21 bảng nên không dùng ở đây; bản tương đương cho app nằm trong `StatsDAO`.

## DAO – bước 2: nối Service vào DAO (Instructor + Auth)  ✅ (biên dịch OK, chưa chạy với DB thật)
Files: `Service/InstructorServiceImpl.java`, `Service/AuthServiceImpl.java` (chép đè).
- `InstructorServiceImpl` và `AuthServiceImpl` không còn câu SQL nào; chỉ giữ luật nghiệp vụ + transaction (`Db.run` / `Db.tx`). Hành vi giữ nguyên như Patch 04/05.
- Cần có cả thư mục `DAO/jdbc/` (bước 1) khi build.

Chưa làm (bước 3): `StudentServiceImpl` (~880 dòng SQL) vẫn còn SQL trực tiếp, sẽ tách tiếp thành `StudentDAO`/`AttemptDAO`... Code cũ trong `DAO/` (không phải `DAO/jdbc/`), `MODEL/`, `StudentResultService`, `StudentResultsTestRunner` vẫn chưa xóa.
