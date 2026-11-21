# Hướng dẫn tạo `.mwb` mới, chạy SQL chuẩn hóa & kiểm thử trên MySQL Workbench 8.0 CE

Thư mục này chứa bộ script SQL chuẩn hóa hoàn chỉnh cho thiết kế **21 bảng BCNF** (xem tóm tắt quy tắc và ánh xạ cho Java/Query tại [docs/normalization/README.md](../../docs/normalization/README.md)).

---

## Danh sách file trong gói

| File | Mục đích sử dụng |
| --- | --- |
| `01_schema_ddl_for_mwb_import.sql` | **DDL thuần túy (chỉ CREATE TABLE + Constraints + Indexes)**, không có `DELIMITER`/Trigger/Seed — dùng để import vào MySQL Workbench sinh file `.mwb` sạch không lỗi cú pháp. |
| `02_online_examination_full_submission.sql` | **Script nộp bài hoàn chỉnh (Deliverable D5)**: `DROP/CREATE DATABASE Online_Examination`, 21 bảng, 28 FK (đủ `ON DELETE`/`ON UPDATE`), `UNIQUE`, `CHECK`, 5 Indexes, 3 Views, Triggers bảo vệ 2 chiều, 5 Stored Procedures ACID và dữ liệu mẫu **`>= 15` dòng trên cả 21/21 bảng**. |
| `03_verify_constraints_and_acceptance_N01_N24.sql` | Bộ test tự động kiểm tra số dòng `>= 15`, 5 câu Query bắt buộc của Topic 20, và 39 ca kiểm chứng `N01–N24` + `BR-29/37/41/42/45/49` (duplicate PK, FK sai, CHECK sai, Trigger vòng tròn, sai kỳ thi, freeze lịch sử, chấm câu trống/tab/CRLF...). |
| `04_migration_from_old_20tables_to_21tables.sql` | Chuyển **tại chỗ** DB `Online_Examination` đang ở schema 20 bảng sang 21 bảng, giữ dữ liệu, không cần database thứ hai: đổi tên bảng cũ thành `OLD_*`, tạo 21 bảng mới, chép dữ liệu trong 1 transaction (kiểm tra trước/sau, lỗi thì `ROLLBACK` và dừng, bảng `OLD_*` còn nguyên), tạo view/trigger/procedure, cuối cùng xóa `OLD_*` -> còn đúng 21 bảng. |

> **Kết quả kiểm chứng độc lập trên MySQL 8.4.11:** `01` chạy sạch (exit code `0`); `02` chạy lại **2 lần liên tiếp** không lỗi với `>= 15` dòng trên cả `21/21` bảng; `03` đạt `ACCEPTANCE_SUMMARY|PASSED=39|FAILED=0`; `04` qua `8` tiền kiểm + `4` đối soát lossless và tự `ROLLBACK` (`0` dòng ghi) khi xung đột `Marks`.

---

## 1. Cách tạo file `.mwb` mới & xuất ảnh Schema Diagram trong MySQL Workbench

### Cách A — Import trực tiếp từ file `01_schema_ddl_for_mwb_import.sql` (Nhanh nhất, không cần kết nối DB)
1. Mở **MySQL Workbench 8.0 CE**.
2. Chọn **File → Import → Reverse Engineer MySQL Create Script...**
3. Trỏ tới `01_schema_ddl_for_mwb_import.sql`, tích chọn **`Place imported objects on a diagram`**, rồi bấm **Execute → Next → Finish**.
4. Vào menu **Arrange → Autolayout** (chỉnh gọn vị trí 21 bảng, kéo các đường nối tránh cắt ngang chữ).
5. Bấm **File → Save Model As...** và lưu thành `tables_script_v2_21tables.mwb`.
6. Bấm **File → Export → Export as PNG...** để xuất ảnh Schema Diagram mới (hiện đủ `Quest_ID`, `User_ID`, `Semester`, `PaperKey`, `exam_question_marks`, `paper_question`) đưa vào Final Report.

### Cách B — Reverse Engineer từ MySQL Server sau khi đã chạy file `02` (Lấy đủ cả Views, Triggers, Procedures)
1. Mở kết nối MySQL Server (local hoặc schema test tách riêng) trong Workbench, chạy toàn bộ file `02_online_examination_full_submission.sql`.
2. Trên thanh menu chính, chọn **Database → Reverse Engineer...** (`Ctrl+R`).
3. Chọn kết nối → chọn schema **`Online_Examination`** → tích chọn cả Tables, Views, Routines, Triggers → **Execute → Finish**.
4. Lưu file `.mwb` và xuất ảnh sơ đồ quan hệ.

---

## 2. Cách chạy & kiểm chứng lại script D5 (Assignment Mục 2.3)

1. Mở `02_online_examination_full_submission.sql` trong MySQL Workbench (hoặc CLI `mysql`), bấm **Execute All (`Ctrl+Shift+Enter`)** lần 1.
2. Bấm **Execute All** tiếp **lần 2 liên tiếp** — script phải chạy sạch không cần sửa tay bất kỳ dòng nào.
3. Mở và chạy `03_verify_constraints_and_acceptance_N01_N24.sql` để xem bảng tổng hợp số dòng từng bảng, kết quả 5 Query Topic 20 và `ACCEPTANCE_SUMMARY|PASSED=39|FAILED=0` — chụp ảnh màn hình này để kẹp vào Chương 4 Final Report.

---

## 3. Lưu ý an toàn khi đụng tới Aiven DB dùng chung

* **KHÔNG chạy** `02_online_examination_full_submission.sql` (có lệnh `DROP DATABASE`) lên server Aiven nếu database chung đang chứa dữ liệu test của nhóm Java/Query mà chưa sao lưu.
* Backup trước (Workbench -> Server -> Data Export), rồi chạy `04` thẳng trên `Online_Examination` đang ở schema 20 bảng; chạy xong còn đúng 21 bảng.
