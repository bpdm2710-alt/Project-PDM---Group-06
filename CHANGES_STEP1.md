# Bước 1 – Sửa lỗi "lượt làm bài quá hạn bị rollback"

Cách dùng: chép đè thư mục `src/` trong gói này lên `src/` của project (cùng đường dẫn).
Chưa biên dịch được trong sandbox (không có javac) – hãy build lại bằng IntelliJ/Maven.

## File thay đổi
1. `src/main/java/org/example/DAO/jdbc/AttemptDAO.java`
   - Thêm record `OpenAttempt` và hàm `openAttemptsOfStudent(Connection, studentId)`.
2. `src/main/java/org/example/Service/StudentServiceImpl.java`
   - `startAttempt`: gọi `closeExpiredAttempt(regisId)` TRƯỚC (transaction riêng, commit ngay).
     Trong transaction chính, nhánh "hết giờ" không còn tự đóng nữa mà báo CONFLICT nhẹ (hiếm gặp).
   - Thêm `closeExpiredAttempt` (1 đăng ký) và `closeExpiredAttemptsOf` (cả sinh viên, có khóa dòng).
   - `getOverview`, `listMyExams`, `listResults`, `listCourseExams` gọi quét trước khi đọc,
     nên bài hết giờ không còn hiện "đang làm" và có kết quả.

## Lỗi gốc
Trước đây `startAttempt` chấm + đóng lượt hết giờ rồi mới kiểm tra "No attempts left"/"exam closed".
Hai lỗi đó ném ServiceException nên `Db.tx` rollback luôn việc đóng lượt -> lượt kẹt "đang làm" mãi
(đặc biệt khi Max_attempts = 1).

## Cách tự kiểm tra
1. Tạo đăng ký + 1 attempt `EndTime IS NULL` có `StartTime` cách đây > Duration, exam Max_attempts = 1.
2. Mở My exams -> lượt phải tự đóng, có điểm ở Results (không còn IN_PROGRESS).
3. Hoặc bấm Start trên exam đó: nhận "No attempts left", nhưng lượt cũ phải đã có EndTime trong DB.
