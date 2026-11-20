# Báo cáo tiến độ – Online Examination (Group 06)

Đánh giá dựa trên code trong file zip (git log tới commit `15767fc`).

## 1. Tiến độ tổng quan (~70%)

| Phần | Trạng thái | Ghi chú |
|---|---|---|
| Thiết kế DB (20 bảng + bản 21 bảng chuẩn hóa BCNF) | Xong | **Hai schema khác nhau, xem mục 3.1** |
| UI Swing (Login, Student, Instructor) | Xong | 10 panel, đã test với tài khoản seed |
| AuthService | Xong | Đăng nhập bằng mật khẩu plain-text |
| StudentService (13 method) | Xong | Đăng ký thi, làm bài, lưu đáp án, nộp bài, chấm tự động, kết quả |
| InstructorService (14 method) | **14/14 sau Patch 04+05** | Đã viết, cần chạy thử với DB thật |
| Test tự động | Rất ít | Chỉ có `StudentResultsTestRunner` cho code cũ |
| Bảo mật / đóng gói | Chưa | Hash mật khẩu, jar đóng gói, v.v. |

## 2. Phần Service còn thiếu (InstructorServiceImpl)

Đang `throw notYet(...)`, nên các màn hình sau sẽ báo lỗi "not connected to the database yet":

| Patch | Method | Màn hình bị ảnh hưởng |
|---|---|---|
| 04 | `listTopics`, `searchQuestionBank`, `getQuestion`, `saveQuestion`, `deleteQuestion` | Tab Question bank, Paper builder (tìm câu hỏi) |
| 04 | `savePaper` | Paper builder, và **không publish được đề nếu chưa có paper** |
| 05 | `getStatistics`, `listStudentMarks` | Tab Statistics |

Thứ tự hợp lý: 04 trước (vì Exams -> Paper -> Student làm bài phụ thuộc vào nó), rồi 05.

## 3. Vấn đề cần quyết định / sửa

### 3.1 (Quan trọng) Code và schema nộp bài đang lệch nhau
Code Java chạy trên schema **20 bảng** (`database/schema/01_schema.sql`: `CONTAINS`, `PAPER(PaperID, Exam_ID)`, `ATTEMPT.PaperID+Exam_ID`, `Pass_mark` là điểm thô, `Status`, `Exam_date` nullable).
Bộ SQL nộp bài `normalized_21tables/` là schema **khác**: `paper_question`, `exam_question_marks`, `PaperKey`, `Pass_mark` là %, `Exam_date NOT NULL`, **không có cột Status**, tên bảng viết thường, và cột `Password_hash` (không có dấu `_` cuối).
=> Nếu nộp schema 21 bảng mà app vẫn chạy bản 20 bảng thì app không chạy được trên DB nộp. Nhóm cần chốt một trong hai:
- (A) Giữ 20 bảng cho app, 21 bảng chỉ là tài liệu chuẩn hóa (cần nói rõ trong báo cáo), hoặc
- (B) Chuyển app sang 21 bảng (phải sửa toàn bộ SQL trong StudentServiceImpl + InstructorServiceImpl, thêm cột Status).
Việc 04/05 mình đang viết theo **schema 20 bảng** (khớp code hiện tại). Nếu chọn (B) sẽ phải port lại.

### 3.2 Bảo mật
- Zip có `application.properties` chứa **mật khẩu DB và truststore thật**. File này đã được `.gitignore` nên không lên Git, nhưng đừng gửi zip này cho ai/đừng upload công khai. Nếu đã chia sẻ thì đổi mật khẩu Aiven.
- Mật khẩu user lưu plain-text (`AuthServiceImpl.passwordMatches`). Đã có chú thích chỉ cần sửa 1 hàm, làm ở giai đoạn vá lỗi (BCrypt/PBKDF2 + migrate seed).

### 3.3 Code cũ còn sót
`DAO/*`, `MODEL/*`, `StudentResultService`, `StudentResultsTestRunner` là bản trước khi có Service mới. `UserDAO` dùng tên bảng viết thường (`user`) sẽ lỗi trên Aiven/Linux phân biệt hoa thường. Nên xóa hoặc chuyển vào `legacy/` sau khi chắc chắn không còn chỗ dùng.

### 3.4 Điểm yếu logic (để vá sau)
- "Đang làm bài" được nhận biết bằng `EndTime = StartTime`: nếu nộp bài trong cùng giây bắt đầu sẽ bị coi là chưa nộp. Nên cho `EndTime` nullable (schema 21 bảng đã làm vậy).
- `ANSWER.MarkAwarded NOT NULL`: không phân biệt "chưa chấm" và "0 điểm", nên chưa có chấm tay cho Short answer.
- `Pass_mark` hiện là điểm thô (`score >= Pass_mark`), còn schema 21 bảng là % - cần thống nhất.
- Mỗi lần gọi DB mở 1 connection mới + set System property truststore mỗi lần (chưa có connection pool).
- `pom.xml` chưa có plugin đóng gói jar, chưa có JUnit; source/target = 26.
- README còn trỏ tới `docs/normalization/README.md` không tồn tại, và còn ghi "20 bảng".

## 4. Kế hoạch

1. Patch 04: Question bank + Paper builder  (XONG, xem CHANGELOG)
2. Patch 05: Statistics + Student marks  (XONG, xem CHANGELOG)
3. Chạy thử toàn bộ luồng trên máy nhóm (seed -> instructor tạo câu hỏi -> paper -> publish -> student thi -> thống kê)
4. Chốt 3.1 (20 vs 21 bảng)
5. Vá: hash mật khẩu, EndTime nullable, dọn code cũ, JUnit cho service, đóng gói jar, cập nhật README
