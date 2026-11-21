# Tóm tắt Normalization — Schema 21 bảng BCNF (Nhóm 06 / Topic 20)

* **Trạng thái & Phạm vi (20/11/2026):** Đây là gói đóng góp SQL và tóm tắt kỹ thuật đã kiểm chứng trên MySQL `8.4.11` cô lập cho phương án đề xuất **21 bảng BCNF** (`#7B` + `#2B1`). Tài liệu phân tích chi tiết (bảng 21 quan hệ & FD, chứng minh 1NF → BCNF, ví dụ dị thường và 52 quy tắc `BR-01`–`BR-52` cho Chương 3 Final Report) được lưu riêng ở bộ file Word của nhóm để giữ repo gọn nhẹ cho code Java và SQL.
* **Ký hiệu quyết định:** `#7B` = quyết định D07, phương án B (điểm tối đa của câu hỏi gắn với kỳ thi, lưu ở `exam_question_marks`); `#2B1` = quyết định D02, phương án B1 (khóa đại diện `PaperKey` cho `paper`, giữ `UNIQUE(Exam_ID, PaperID)`).

---

## 1. Tóm tắt kiến trúc 21 bảng BCNF (`#7B` + `#2B1`)

* **Quy mô đối tượng DDL & ràng buộc:** `21` bảng cơ sở (tất cả đạt BCNF), `21` PK, `28` FK (toàn bộ dùng `ON UPDATE RESTRICT`; `27` FK dùng `ON DELETE RESTRICT`, riêng `user_phone.User_ID` dùng `ON DELETE CASCADE`), `4` `UNIQUE`, `29` `CHECK` (gồm `chk_exam_published_dated`), `5` Index, `3` View (`v_paper_total_marks`, `v_attempt_scores`, `v_representative_attempts`), `28` Trigger bảo vệ hai chiều và `5` Stored Procedure ACID (`sp_create_student`, `sp_create_instructor`, `sp_start_attempt`, `sp_submit_mcq_answer`, `sp_finalize_attempt`).
* **6 thay đổi chính so với bản 20 bảng cũ (`script.sql`):**
  1. **`select_table`:** Đổi PK thành `(Quest_ID, Attempt_no, RegisID)` (một lựa chọn mỗi câu MCQ trong một lượt thi) và dùng 2 composite FK tới `options(Option_no, Quest_ID)` và `answer(Quest_ID, Attempt_no, RegisID)`.
  2. **`paper` & `attempt`:** Bổ sung khóa đại diện `PaperKey` (`AUTO_INCREMENT`, `UNIQUE(Exam_ID, PaperID)`), bỏ cột dư `attempt.Exam_ID` để triệt tiêu phụ thuộc hàm bộ phận `RegisID -> Exam_ID`, kèm trigger bảo đảm `paper.Exam_ID = registration.Exam_ID`.
  3. **Tách `contains` thành `exam_question_marks` + `paper_question`:** `exam_question_marks(Exam_ID, Quest_ID, Marks)` cố định điểm tối đa theo từng kỳ thi (`#7B`), còn `paper_question(PaperKey, Quest_ID, QuestionOrder)` giữ cấu trúc đề và thứ tự câu (`UNIQUE(PaperKey, QuestionOrder)`).
  4. **`teach`:** PK 3 cột `(User_ID, Course_ID, Semester)` với `CHECK` phân biệt hoa thường `^20[0-9]{2}-(S[12]|SU)$`.
  5. **Chuẩn hóa tên cột & trạng thái làm bài / chờ chấm:** Thống nhất `Password_hash`, `Prerequisite_ID`, `Advanced_ID`; cho phép `attempt.EndTime`, `answer.AnswerText`, `answer.MarkAwarded` mang giá trị `NULL` khi đang làm hoặc chờ chấm. Khi gọi `sp_finalize_attempt`, mọi câu bỏ trống hoặc tự luận chỉ chứa khoảng trắng (`[[:space:]]`) đều được chấm `0.00`; `v_attempt_scores` chỉ công bố `Total_Score`, `ScorePercent` và `IsPassed` khi `IsFullyGraded = 1`.
  6. **`EXAMINATION.Status` (`DRAFT`/`PUBLISHED`, BR-51):** đề mới mặc định là `DRAFT`; `Exam_date` được phép `NULL` khi còn là bản nháp, và `CHECK chk_exam_published_dated` bắt buộc đề `PUBLISHED` phải có ngày thi. Tên bảng viết HOA trong mọi script (MySQL trên Linux/Aiven phân biệt hoa thường).

---

## 2. Hướng dẫn sử dụng bộ script trong [`database/normalized_21tables/`](../../database/normalized_21tables/)

| Nhóm / Mục đích | File cần dùng | Ghi chú sử dụng |
| --- | --- | --- |
| **Nộp bài D5 / Dựng DB mới** | [`02_online_examination_full_submission.sql`](../../database/normalized_21tables/02_online_examination_full_submission.sql) | Dùng `Online_Examination` cho cả `DROP`, `CREATE` và `USE`, khớp `application.properties.example`. Bộ seed có ít nhất 15 dòng mỗi bảng. |
| **Dựng `.mwb` trên Workbench** | [`01_schema_ddl_for_mwb_import.sql`](../../database/normalized_21tables/01_schema_ddl_for_mwb_import.sql) + [`WORKBENCH_MWB_AND_EXECUTION_GUIDE.md`](../../database/normalized_21tables/WORKBENCH_MWB_AND_EXECUTION_GUIDE.md) | DDL thuần tạo schema `Online_Examination` (không chứa `DELIMITER`/Trigger) để *Reverse Engineer MySQL Create Script* sinh file `.mwb` và ảnh sơ đồ không lỗi cú pháp. |
| **Team Query & Báo cáo Ch. 4** | [`03_verify_constraints_and_acceptance_N01_N24.sql`](../../database/normalized_21tables/03_verify_constraints_and_acceptance_N01_N24.sql) | Chạy trực tiếp ngay sau `02` trên `Online_Examination` để lấy kết quả 5 Query Topic 20, bảng đếm số dòng và 39 ca nghiệm thu. |
| **Team Java DAO / Service** | [`02_online_examination_full_submission.sql`](../../database/normalized_21tables/02_online_examination_full_submission.sql) | Java đọc điểm qua các view (`v_attempt_scores`…) và tự chạy các thao tác nhiều bước (bắt đầu, lưu câu trả lời, nộp bài) trong transaction JDBC riêng; ứng dụng **không gọi** 5 stored procedure, các procedure được giữ để minh họa transaction ở tầng DB. Trigger vẫn kiểm tra mọi thay đổi. |
| **DML & transaction (nộp bài)** | [`04_dml_and_transactions.sql`](../../database/normalized_21tables/04_dml_and_transactions.sql) | Chạy sau `02`: INSERT, UPDATE, DELETE cho mỗi bảng chính và 3 transaction đổi hai bảng cùng lúc (2 lần `COMMIT`, 1 lần `ROLLBACK`). Dùng khối ID riêng 9001+ / 90001+ và tự xóa sạch những gì đã tạo, nên an toàn trên DB dùng chung; chạy lại nhiều lần được. App không chạy file này. |
| **Nâng cấp mật khẩu mẫu** | [`05_upgrade_password_hashes.sql`](../../database/normalized_21tables/05_upgrade_password_hashes.sql) | Chỉ dùng cho DB dựng từ seed `02` cũ: thay giá trị `sha256:…` giữ chỗ bằng hash PBKDF2 thật (như seed `02` mới). Chạy lại nhiều lần vẫn an toàn. |

### Schema MySQL

Các script `01`–`05` dùng schema đích **`Online_Examination`**, khớp `application.properties.example`. (Script chuyển đổi cũ `04_migration_from_old_20tables_to_21tables.sql` đã được gỡ khỏi repo ở commit `a72b19d`, vì DB trên Aiven đã được dựng lại bằng `02`; số `04` nay dùng cho script DML & transaction.)

Kết quả chạy thực tế bên dưới dùng MySQL 8.4.11 cô lập trên Windows với `lower_case_table_names = 1`. Trên máy chủ có `lower_case_table_names = 0`, các câu lệnh dùng đúng tên `Online_Examination`. Chế độ này chưa được chạy thử.

`01` và `02` xóa rồi tạo lại schema `Online_Examination`. Chạy hai script này trong môi trường kiểm thử riêng.

---

## 3. Trạng thái kiểm chứng & Đầu việc chờ nhóm chốt

* **Đã kiểm chứng thực tế trên MySQL `8.4.11` cô lập:**
  * `01_schema_ddl_for_mwb_import.sql` chạy sạch (`exit code 0`).
  * `02_online_examination_full_submission.sql` chạy sạch **2 lần liên tiếp** (`exit code 0`), cả `21/21` bảng có từ `15` đến `116` dòng.
  * `03_verify_constraints_and_acceptance_N01_N24.sql` trả đủ 5 Query Topic 20 và **`ACCEPTANCE_SUMMARY|PASSED=39|FAILED=0`**.
* **Việc còn lại:**
  1. ~~Cập nhật `docs/design/Relational_Schema.mwb` và `Relational_Schema.png` (thêm cột `Status`)~~ — đã xong (PR #17). Workbench trên Windows hiển thị tên bảng chữ thường; các script dùng chữ HOA.
  2. ~~Quyết định làm mới DB trên Aiven~~ — đã dựng lại bằng `02`.
  3. ~~Đồng bộ Java DAO/MODEL/Service sang schema 21 bảng~~ — đã xong trên `main`.

---

## 4. Đối chiếu ký hiệu Cardinality trên EER (`erdplus_D.png`) với Business Rules

Theo quy ước ERDPlus (ký hiệu đặt **cạnh thực thể X** biểu diễn có bao nhiêu X gắn với một thực thể ở đầu kia), đối chiếu `erdplus_D.png` hiện hành cần chỉnh 3 đầu cạnh phải của `Question` từ `|<` (`1..N`, bắt buộc `>= 1`) sang `o<` (`0..N`, có thể bằng `0` khi soạn thảo):

| Quan hệ | Đầu đang vẽ | Cần sửa thành | Business Rule căn cứ |
| --- | --- | --- | --- |
| `Has_ques` (`Topic` — `Question`) | `\|<` cạnh `Question` | `o<` | **BR-11:** Topic có thể tồn tại trước khi có câu hỏi đầu tiên (đầu cạnh `Topic` vẫn giữ `\|\|` theo **BR-15**). |
| `Paper_Question` (`Paper` — `Question`) | `\|<` cạnh `Question` | `o<` | **BR-25, BR-28:** Đề được tạo trước rồi mới thêm câu; điều kiện có `>= 1` câu chỉ bắt buộc khi đề được giao cho `Attempt` (trigger `trg_attempt_bi_checks`). |
| `Exam_Question_Marks` (`Examination` — `Question`) | `\|<` cạnh `Question` | `o<` | **BR-25:** Kỳ thi được lập trước khi gán câu hỏi và mức điểm tối đa. |

*Cùng nguyên tắc cho phép tồn tại trước khi gắn con lúc soạn dữ liệu (`Master v2` §3.1):* các đầu `o<` cũng áp dụng tương tự ở `Register` (cạnh `Registration`, **BR-31**), `Has_paper` (cạnh `Paper`, **BR-25**) và `Option_of` (cạnh `Options`, **BR-20** — bắt buộc `>= 2` option và `1` đáp án đúng được kiểm trước khi đưa MCQ vào đề). Các chỉnh sửa minimum cardinality (`1` → `0`) trên sơ đồ EER này **không làm thay đổi bất kỳ bảng, PK hay FK nào trong bộ DDL 21 bảng SQL**.
