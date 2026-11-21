# Kịch bản demo — Online Examination System (Group 06)

Bản này dùng cho code **sau Bước 11** (giờ thi theo đồng hồ DB, nhiều đề/paper, kiểm tra môn tiên quyết,
connection pool). Toàn bộ kịch bản đã chạy thử tự động (`DemoScenarioIT`, 17/17 PASS).

Thời lượng: khoảng 12–15 phút. Cần 1 máy chạy app (có thể mở 2 cửa sổ app để đổi vai nhanh).

---

## 0. Chuẩn bị (làm trước buổi demo 5–30 phút)

1. `git pull` để có code mới nhất, trong IntelliJ bấm **Maven → Reload project**.
2. Mở MySQL Workbench, kết nối Aiven, mở và chạy **toàn bộ** file
   `database/demo/03_demo_seed.sql` (biểu tượng tia sét ⚡, không phải "execute current statement").
3. Nếu Workbench đang tắt auto-commit: chạy thêm `COMMIT;`.
4. Kết quả cuối của script phải ra 4 dòng:

   | Title | Status | registrations | open_attempts |
   |---|---|---|---|
   | DEMO - Open now | PUBLISHED | 3 | 0 |
   | DEMO - Upcoming | PUBLISHED | 0 | 0 |
   | DEMO - Expired attempt | PUBLISHED | 1 | 1 |
   | DEMO - Draft | DRAFT | 0 | 0 |

5. Chạy app: `src/main/java/org/example/Main.java` → **Run** (hoặc chạy file jar).

> **Quan trọng**
> * Script demo **reset** các exam `DEMO - ...` trên DB chung (Aiven) → báo nhóm trước khi chạy, đừng chạy lúc người khác đang demo.
> * `DEMO - Open now` chỉ mở **180 phút** tính từ lúc chạy script. Quá giờ → chạy lại script.
> * Chạy lại script là đủ để demo lại từ đầu; không cần chạy lại `02` hay `05`.
> * File `03_verify_constraints_...` trong `normalized_21tables/` là **test ràng buộc**, KHÔNG dùng cho demo.

Mật khẩu: sinh viên `Stu@123`, giảng viên `Inst@123`.

---

## Phần A — Sinh viên làm bài (sv103) · ~4 phút

**Đăng nhập:** `sv103@student.hcmiu.edu.vn` / `Stu@123`

| # | Thao tác | Kết quả mong đợi / điểm cần nói |
|---|---|---|
| A1 | Mở tab **My exams** | Thẻ `DEMO - Open now` trạng thái **OPEN**, 0/2 lượt. Giờ mở/đóng tính theo **đồng hồ DB (+07:00)**, máy nào cũng như nhau. |
| A2 | Bấm **Start exam** | Màn làm bài: 5 câu, đồng hồ **Time left** đếm ngược (khoảng 2 giờ 55 phút). Lượt 1 nhận **Paper 1**. |
| A3 | Trả lời 4 câu trắc nghiệm: `HAVING`, `Second Normal Form (2NF)`, `Total participation`, `Relational division` | Mỗi lần chọn là lưu ngay vào DB (`ANSWER` + `SELECT_TABLE`). |
| A4 | Câu tự luận (Candidate key): gõ `a key must be unique and minimal` | Không khớp đáp án mẫu → để giảng viên chấm tay. |
| A5 | Bấm **Submit exam** | Báo đã nộp. Trạng thái **GRADING**: 4 câu MCQ được chấm tự động (40 điểm), câu tự luận chờ chấm (BR-42). |
| A6 | Quay lại **My exams** | Thẻ vẫn **OPEN**, đã dùng 1/2 lượt (Max attempts = 2). |
| A7 | Bấm **Start exam** lần 2, chọn 1 đáp án bất kỳ, rồi **đóng cửa sổ / Log out** (KHÔNG nộp) | Lượt 2 nhận **Paper 2** (cùng câu hỏi, thứ tự khác) → demo **xoay vòng đề**. |
| A8 | Đăng nhập lại sv103 → **My exams** | Thẻ hiện **Resume exam**; bấm vào thì câu đã chọn vẫn còn, đồng hồ tiếp tục chạy theo giờ bắt đầu thật. |

Nộp hoặc để đó lượt 2 đều được (nếu để quá giờ, hệ thống tự đóng và chấm lượt đó, xem Phần B).

---

## Phần B — Lượt làm bài hết giờ tự đóng (sv101) · ~1 phút

**Đăng nhập:** `sv101@student.hcmiu.edu.vn` / `Stu@123`

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| B1 | **My exams** | `DEMO - Expired attempt` = **COMPLETED** (exam 60 phút, mở 3 giờ trước, sv101 bắt đầu nhưng không nộp). |
| B2 | Tab **Results** | Lượt đó đã được hệ thống **tự đóng khi hết giờ** và chấm: **10 / 40** (chỉ 1 câu đã chọn đúng). |
| B3 | (tùy chọn) | sv101 cũng thấy `DEMO - Open now` OPEN → có thể làm thử để demo 2 sinh viên nhận 2 paper khác nhau. |

---

## Phần C — Giảng viên (inst01) · ~5 phút

**Đăng nhập:** `inst01@hcmiu.edu.vn` / `Inst@123` → chọn khóa **Principles of Database Management (PDM)**.

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| C1 | Tab **Exams** | Thấy 4 exam `DEMO - ...` cùng các exam seed, có trạng thái DRAFT/PUBLISHED. |
| C2 | Tab **Grading** | Có câu tự luận của sv103 (đáp án mẫu hiển thị bên cạnh). Nhập **15** (tối đa 20) → **Save mark**. |
| C3 | Tab **Reports** | Chọn lần lượt Q1 … Q11 → **Run**. Đây đúng là các query trong `database/queries/PDMProjectQuery.sql`. |
| C4 | Tab **Statistics** | Điểm trung bình theo topic, câu hỏi khó nhất (Hardest questions). |
| C5 | Tab **Exams** → `DEMO - Draft` → **Build paper**: thêm 2 câu (10 điểm mỗi câu) → **Save paper**; **Edit exam** đặt ngày thi ngày mai → **Save**; bấm **Publish** | Trạng thái chuyển **DRAFT → PUBLISHED**. Không có paper/câu hỏi thì không publish được. |
| C6 | Tab **Question bank** → **New question**: tạo 1 câu MCQ 2 lựa chọn (1 đúng) → **Save** | Câu hỏi mới có ID = MAX + 1, xuất hiện trong danh sách. |

---

## Phần D — Sinh viên xem kết quả sau khi chấm (sv103) · ~1 phút

**Đăng nhập lại** `sv103@student.hcmiu.edu.vn` → tab **Results**

* Lượt 1: **55 / 60 = 91.7 %**, **Passed** (Pass mark 50 %). 40 điểm MCQ + 15 điểm giảng viên chấm.
* Điểm đại diện lấy theo view `v_attempt_scores` / `v_representative_attempts`.

---

## Phần E — Môn tiên quyết (sv104 vs sv106) · ~2 phút

PDM yêu cầu đã đậu **Data Structures and Algorithms** (bảng `REQUIRES`). Trong seed chỉ **sv106** và **sv111** đã đậu DSA.

| # | Tài khoản | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| E1 | `sv104@student.hcmiu.edu.vn` | **Courses** → PDM → **View exams** → `DEMO - Upcoming` → **Register** | Bị chặn: *"You must pass Data Structures and Algorithms before registering for this exam (prerequisite)."* |
| E2 | `sv106@student.hcmiu.edu.vn` | Làm giống E1 | Đăng ký thành công, thẻ chuyển **UPCOMING** (exam mở sau 2 ngày, chưa Start được). |

---

## Câu hỏi hay gặp khi trình bày (gợi ý trả lời)

* **Vì sao giờ thi không phụ thuộc máy?** App lấy giờ bằng `SELECT NOW()` trên DB, mỗi kết nối đặt `time_zone = '+07:00'`.
* **Nhiều đề hoạt động thế nào?** Mỗi exam có nhiều `PAPER`; lượt mới nhận paper mà đăng ký đó dùng ít nhất, xoay vòng theo số lượt của exam.
* **Chấm điểm?** Để trống → 0; khớp đáp án → đủ điểm; còn lại → `NULL` chờ giảng viên chấm (BR-42).
* **Mật khẩu?** Lưu PBKDF2-SHA256 (120000 vòng, có salt), không lưu bản rõ.
* **ID sinh thế nào?** Schema chỉ có `PAPER.PaperKey` AUTO_INCREMENT; các ID khác = MAX + 1 trong transaction.
* **Kết nối DB?** HikariCP pool (mặc định 5 kết nối), SSL `VERIFY_CA` tới Aiven.

## Nếu demo bị lỗi

| Hiện tượng | Cách xử lý |
|---|---|
| Không thấy `DEMO - Open now` hoặc thẻ toàn COMPLETED | Đã quá 180 phút / chưa chạy seed → chạy lại `03_demo_seed.sql` + `COMMIT;`. Chỉ sv101–sv103 được đăng ký exam này. |
| "Email or password incorrect" | DB chưa chạy `05_upgrade_password_hashes.sql` hoặc code cũ → `git pull`, Maven Reload. |
| "Cannot connect to the database" | Kiểm tra `application.properties`, đường dẫn và mật khẩu truststore, mạng. |
| Grading không có câu của sv103 | sv103 chưa Submit lượt 1 hoặc đang ở khóa khác (chọn PDM). |
