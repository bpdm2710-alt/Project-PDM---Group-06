# UPDATE NOTES – Online Examination (Group 06)

Gói `Project-PDM-Group06_UPDATE.zip` là gói **cộng dồn**: giải nén đè lên thư mục project
(cùng cấp với `pom.xml`). Nếu có `DELETED_FILES.txt` không rỗng thì chạy `apply_deletes.bat`
(Windows) hoặc `apply_deletes.sh` (Linux/Mac) để xóa các file đã bỏ.
`application.properties` (chứa mật khẩu DB) KHÔNG nằm trong gói, giữ nguyên bản của bạn.

Môi trường kiểm chứng: JDK 21 + MySQL 8.0.46 trên Linux (`lower_case_table_names = 0`,
phân biệt hoa thường giống Aiven).

---

## Bước 0 – Sửa chỗ báo đỏ ở AttemptDAO

**Nguyên nhân:** khi chép bản sửa Bước 1, file `AttemptDAO.java` mới (package
`org.example.DAO.jdbc`) bị chép nhầm vào `src/main/java/org/example/DAO/AttemptDAO.java`, đè lên
bản cũ (package `org.example.DAO`). Kết quả:
- `duplicate class: org.example.DAO.jdbc.AttemptDAO` (2 file cùng khai báo 1 lớp);
- `StudentResultService` không còn thấy `org.example.DAO.AttemptDAO` -> 10 lỗi `cannot find symbol`.

**Sửa:** khôi phục `DAO/AttemptDAO.java` về bản cũ trong git. Bản mới chỉ nằm ở
`DAO/jdbc/AttemptDAO.java` (giữ nguyên, đã đúng). `javac` toàn bộ `src/main/java` biên dịch sạch.
Code cũ (`DAO/*`, `MODEL/*`) sẽ được xóa hẳn ở Bước 6.

---

## Bước 2 – Đồng bộ file SQL (#5) – ĐÃ KIỂM CHỨNG trên MySQL 8.0 Linux

| File | Thay đổi | Kết quả chạy |
|---|---|---|
| `normalized_21tables/02_...full_submission.sql` | Không đổi (là "nguồn chuẩn") | Chạy sạch: 21 bảng, 4 view, 35 user seed |
| `normalized_21tables/01_schema_ddl_for_mwb_import.sql` | **Sinh lại từ phần DDL của 02** (bảng + index). Nay có `Status`, `Exam_date` NULL, AUTO_INCREMENT, tên bảng VIẾT HOA | Chạy sạch, `EXAMINATION` đúng cột |
| `normalized_21tables/03_verify_..._N01_N24.sql` | Viết hoa 123 tên bảng trong backtick | Chạy sau 02: **PASSED=39, FAILED=0** (trước đó 7 lỗi `Table ... doesn't exist`) |
| `normalized_21tables/04_migration_...sql` | Viết hoa tên bảng (kể cả `CONTAINS` của DB cũ); chép thêm cột `Status`; **quy đổi Pass_mark điểm thô -> %** theo tổng điểm paper nhỏ nhất (tối đa 100) | Thử với DB cũ 20 bảng + seed `student_results`: mọi audit = 0, lossless check = 0; 10/20 điểm -> 50.00% |
| `database/queries/PDMProjectQuery.sql` (MỚI) | Bản viết hoa tên bảng của file query, thêm `USE Online_Examination` | Toàn bộ query chạy không lỗi |

Ghi chú: khi sửa schema sau này, chỉ sửa 02 rồi sinh lại 01 (01 = dòng 1 tới trước phần
"DERIVED VIEWS" của 02).

---

## Bước 3 – Hash mật khẩu thật (#2) + seed demo ngày tương đối (#3) – ĐÃ KIỂM CHỨNG

### Mật khẩu
- MỚI `src/main/java/org/example/util/PasswordHasher.java`: PBKDF2-HMAC-SHA256, 120 000 vòng,
  salt 16 byte, có sẵn trong JDK (không thêm thư viện). Định dạng lưu:
  `pbkdf2_sha256$120000$<salt>$<hash>`. Sinh hash bằng dòng lệnh:
  `java -cp target/classes org.example.util.PasswordHasher "MatKhau"`.
- `AuthServiceImpl.passwordMatches`: hash PBKDF2 -> verify; giá trị cũ chưa hash -> so chuỗi thô
  MỘT lần rồi **tự thay bằng hash** ngay sau khi đăng nhập đúng (`upgradeHashIfNeeded`).
- `UserDAO.updatePasswordHash(...)` (mới).
- `02_...full_submission.sql`: 35 tài khoản seed nay là hash thật.
  **Mật khẩu demo: giảng viên (ID 1-15) `Inst@123`, sinh viên (ID 101-120) `Stu@123`.**
- MỚI `normalized_21tables/05_upgrade_password_hashes.sql`: cho DB Aiven đã dựng bằng seed cũ
  (`sha256:...`). Chỉ cập nhật dòng còn giá trị cũ, chạy lại không sao.
- `DatabaseConnection`: chỉ đặt truststore khi có cấu hình (trước đây thiếu key -> NullPointerException;
  nay chạy được cả MySQL cục bộ không SSL).

### Seed demo
- MỚI `database/demo/03_demo_seed.sql` (chạy sau 02, chạy lại bao nhiêu lần cũng được; dùng
  `SET time_zone = '+07:00'` vì Aiven mặc định UTC còn app so với giờ máy người dùng):
  - `DEMO - Open now`: mở 5 phút trước, 180 phút, 2 lượt; sv101–sv103 đã đăng ký -> bấm Start.
  - `DEMO - Upcoming`: mở sau 2 ngày, chưa ai đăng ký -> thử Register.
  - `DEMO - Expired attempt`: sv101 có lượt quá giờ chưa nộp -> app phải tự đóng + chấm (kiểm Bước 1).
  - `DEMO - Draft`: bản nháp cho giảng viên sửa/tạo paper/publish.

### Kết quả kiểm thử (gọi thẳng Service, MySQL 8 Linux)
- Đăng nhập: 6/6 PASS (đúng/sai mật khẩu, không phân biệt hoa thường email, nâng cấp hash cũ).
- Luồng sinh viên: 11/11 PASS – lượt quá giờ tự đóng & chấm 10/40; Start -> lưu 5 đáp án ->
  vào lại giữ nguyên đáp án -> Submit 60/60 (100%, passed); Register exam sắp tới; Start trước giờ
  bị chặn "This exam is not open yet."; Overview OK.
- 03_verify sau khi đổi seed: vẫn PASSED=39/39.

---

## Bước 4 – Chốt `sp_*` và cách chấm Short answer (#6) – ĐÃ KIỂM CHỨNG

**Quyết định (ghi vào báo cáo):**
1. App **không gọi** `sp_start_attempt / sp_submit_mcq_answer / sp_finalize_attempt`. Lý do: các
   procedure tự `START TRANSACTION ... COMMIT`, không lồng được trong `Db.tx` (transaction + khóa
   dòng của Java). Logic nằm ở Java (`StudentServiceImpl`), trigger trong DB vẫn là lớp chặn cuối.
   Các procedure vẫn giữ trong schema làm API mức DB và được `03_verify` kiểm (39/39).
2. **Luật chấm Short answer thống nhất với `sp_finalize_attempt` (BR-42):**
   bỏ trống -> 0; khớp `Answer_key` (bỏ khoảng trắng 2 đầu, không phân biệt hoa thường) -> đủ điểm;
   **khác đáp án -> `MarkAwarded = NULL` chờ giảng viên chấm tay** (trước đây cho 0 luôn nên chấm tay
   không bao giờ xảy ra). MCQ vẫn chấm tự động khi nộp.

**Code:**
- `StudentServiceImpl`: `gradeAndClose` dùng hàm mới `autoMark(...)` (null = chờ chấm).
- MỚI `DAO/jdbc/GradingDAO.java`: `pendingForCourse`, `countPendingForCourse`, `findForUpdate`
  (khóa dòng ANSWER), `setMark`.
- `InstructorService` (+ Impl): `listPendingGrading(courseId)`, `gradeAnswer(AnswerRef, mark)`.
  Kiểm tra: giảng viên phải dạy course đó; chỉ Short answer; bài đã nộp; 0 <= mark <= Marks,
  tối đa 2 chữ số thập phân. Có thể sửa điểm bằng cách chấm lại.
- MỚI `ui/panel/GradingTab.java` + tab **"Grading"** trong trang course của giảng viên: bảng bài
  chờ chấm, xem câu hỏi / câu trả lời / đáp án tham khảo, nút "Full marks", "0", "Save mark".
  Màn Results của sinh viên hiện "GRADING" cho tới khi mọi câu có điểm.

**Kiểm thử:** chấm tay 8/8 PASS (bài chờ chấm xuất hiện, chặn điểm > max, chặn 3 chữ số thập phân,
chặn chấm tay MCQ, sau khi chấm kết quả sinh viên thành fully graded 15/60, giảng viên khác course
bị chặn UNAUTHORIZED). Chạy lại luồng sinh viên 11/11 và đăng nhập 6/6 PASS.

---

## Bước 5 – Thiết kế ReportDAO theo `.sql`, nối vào Service/UI (#7) – ĐÃ KIỂM CHỨNG

**Sửa query (cả `database/queries/PDMProjectQuery.sql` và `ReportDAO`, giữ giống hệt nhau):**
| Query | Sửa |
|---|---|
| Q1 lỗi > 70% | chỉ tính lượt đã nộp (`EndTime IS NOT NULL`) |
| Q4 điểm từng lượt | chỉ lượt đã nộp **và chấm xong** (IN `v_attempt_scores.IsFullyGraded = 1`) -> không còn điểm "một phần"; GROUP BY thêm `RegisID` |
| Q5 set operation | dùng `EXCEPT` như file query. Vì mỗi câu hỏi chỉ thuộc **1 topic**, "topic A EXCEPT topic B" luôn = topic A (và 2 topic trong file cũ không tồn tại nên luôn rỗng) -> đổi thành **"câu có trong exam A nhưng không có trong exam B"**. Cần MySQL 8.0.31+ |
| Q10 phép chia | chỉ exam `PUBLISHED` và lượt đã nộp |
| Q11 + view `vw_AverageScoreByTopic` | chỉ lượt đã nộp; thêm cột `Average_Percent` (% so với điểm câu) – view cũng đã sửa trong 02 |

**Code:**
- `DAO/jdbc/ReportDAO.java` (viết lại): mỗi query là 1 hằng SQL `Q1_...`–`Q11_...` + 1 method typed
  (record) + hàm chung `table(conn, sql, params)` trả cột/dòng để hiển thị.
- `InstructorService.runReport(ReportKind, ReportParams)` (+ enum `ReportKind` có tiêu đề/mô tả,
  `ReportParams`, `ReportTable`). Chỉ giảng viên dạy course mới chạy được.
- MỚI `ui/panel/ReportsTab.java` + tab **"Reports"** trong trang course giảng viên: chọn query,
  nhập ngưỡng N (Q1/Q6/Q8) hoặc chọn exam A/B (Q5), bấm Run, kết quả hiện dạng bảng (sắp xếp được).
- `StatsDAO` (tab Statistics): chỉ tính lượt **đã chấm xong**, tránh điểm thấp giả khi còn câu chờ chấm tay.

**Kiểm thử:** 11/11 report qua Service trả đúng số dòng như chạy trực tiếp file `.sql`
(Q1=2, Q2=49, Q3=6, Q4=34, Q5=2, Q6=14, Q7=14, Q8=11, Q9=15, Q10=3, Q11=17); method typed khớp;
chặn thiếu exam, chặn giảng viên không dạy course; dựng được GradingTab/ReportsTab (headless).
Chạy lại: luồng sinh viên 11/11, chấm tay 8/8, đăng nhập 6/6, 03_verify 39/39.

---

## Bước 6 – Dọn code cũ, script cũ, tài liệu (#12–#15)

- **Xóa code chết** (đã grep: ngoài nhau ra không file nào dùng): `DAO/*.java` (12 file, gồm cả
  `DAO/AttemptDAO.java` cũ gây báo đỏ), `MODEL/*` (11 file), `Service/StudentResultService.java`,
  `src/test/.../StudentResultsTestRunner.java`. Project vẫn biên dịch sạch.
- **Script SQL 20 bảng** chuyển vào `database/legacy_20tables/` (có README cảnh báo; file schema
  cũ có dòng đầu "DO NOT RUN ON AIVEN" vì chứa `DROP DATABASE`).
- **Tài liệu cũ** (`PROGRESS_REPORT.md`, `CHANGELOG_PATCHES.md`, `CHANGES_STEP1.md`,
  `docs/ui/PROGRESS.md`) chuyển vào `docs/legacy/`, đầu mỗi file có ghi chú "LỖI THỜI".
  `README.md` bổ sung thứ tự chạy SQL, tài khoản demo, build/test.
- MỚI `.gitattributes` (LF cho java/sql/md/xml, CRLF cho .bat) -> hết cảnh ~40 file "modified" do CRLF/LF.
  Sau khi chép, chạy 1 lần: `git add --renormalize .` rồi commit.

## Bước 7 – pom.xml, JUnit, chạy thử toàn luồng (#4, #17, #18)

- `pom.xml`: resource `src/main/Sources` (đúng hoa/thường, chạy được trên Linux/Mac),
  `maven.compiler.release = 21` (thay 26), JUnit 5.10.2, surefire, **maven-shade** ->
  `mvn package` tạo `target/Online_Examination_System-1.0-SNAPSHOT-all.jar` chạy bằng `java -jar`.
- Test (`src/test/java`):
  - `util/PasswordHasherTest`, `Service/GradingRulesTest` – unit test, không cần DB: **8/8 PASS**.
  - `it/EndToEndTest` – toàn luồng (login -> lượt quá giờ tự đóng -> làm bài/lưu/vào lại/nộp ->
    đăng ký -> short answer chờ chấm -> giảng viên chấm -> 11 report + statistics): **6/6 PASS**,
    chạy lại lần 2 sau khi seed lại demo vẫn PASS. Chỉ chạy khi `-Dit.db=local` VÀ `db.url` là
    localhost (tự từ chối nếu trỏ ra ngoài, để không ghi dữ liệu lên Aiven).
  - Cách chạy E2E: dựng MySQL cục bộ bằng 02 + `demo/03_demo_seed.sql`; chép
    `src/test/resources/application.properties.example` thành `application.properties`; `mvn test -Dit.db=local`.
- Lưu ý: sandbox không tải được Maven Central nên các test được chạy bằng javac + JUnit console
  (cùng phiên bản JUnit 5); hãy chạy `mvn clean package` một lần trên máy nhóm để xác nhận plugin.

---

## Sau khi giải nén gói này
1. Chạy `apply_deletes.bat` (Windows) / `apply_deletes.sh` để xóa các file trong `DELETED_FILES.txt`
   (code cũ + file đã chuyển chỗ). Có thể xóa tay các thư mục rỗng còn lại
   (`src/main/java/org/example/MODEL`, `database/schema`, `database/migrations`, `database/student_results`).
2. Xóa thư mục `target/` (hoặc `mvn clean`): trong đó còn class cũ và **bản copy `application.properties`
   có mật khẩu thật**.
3. Trong IntelliJ: Maven -> Reload project (pom đổi Java 21 + thêm JUnit/shade).
4. DB trên Aiven: chạy `normalized_21tables/05_upgrade_password_hashes.sql` (nếu còn seed cũ) và cập nhật
   view: chạy đoạn `CREATE OR REPLACE VIEW vw_AverageScoreByTopic` ở cuối `database/queries/PDMProjectQuery.sql`.
   Muốn demo: chạy `database/demo/03_demo_seed.sql`.

## Còn lại (chưa làm, theo báo cáo PDF)
- #8 giờ thi lấy theo đồng hồ máy client (`LocalDateTime.now()`); nên dùng `NOW()` của DB hoặc cố định
  `connectionTimeZone`. Seed demo đã tạm xử lý bằng `SET time_zone = '+07:00'`.
- #9 nhiều paper dùng chung điểm theo exam; sinh viên luôn nhận paper PaperID nhỏ nhất -> ghi vào báo cáo.
- #10 kiểm tra quyền trong service bỏ qua khi không có Session (chấp nhận cho test).
- #11 REQUIRES chưa áp dụng khi đăng ký thi; `MAX(Semester)` so chuỗi; `deleteQuestion` chưa kiểm tra `attemptedCount`.
- #16 đổi mật khẩu Aiven nếu zip cũ từng gửi ra ngoài; dùng user DB quyền hạn chế. Chưa có connection pool.

---

## Bước 8 – Sửa theo file 02 của nhóm (bản bạn gửi) + hết báo đỏ AttemptDAO

**02 chuẩn = file bạn gửi** (đã chép đè `normalized_21tables/02_...sql`, `01` sinh lại từ nó).
Khác với bản trước: KHÔNG có AUTO_INCREMENT (trừ `PAPER.PaperKey`), KHÔNG có view
`vw_AverageScoreByTopic`, mật khẩu seed là `sha256:...`. Tên bảng VIẾT HOA – code đã khớp
(đã quét mọi chuỗi SQL trong Java: mọi bảng sau FROM/JOIN/INTO/UPDATE đều có trong 02, mọi cột `x.Cot` đều tồn tại).

**Hết đỏ ở AttemptDAO:**
- `FOR UPDATE OF r` / `FOR UPDATE OF a` (cú pháp riêng MySQL 8, IntelliJ gạch đỏ) -> khóa đúng 1 dòng bằng
  câu chuẩn `SELECT RegisID FROM REGISTRATION WHERE RegisID = ? FOR UPDATE` rồi mới đọc join. Tương tự `GradingDAO`.
- `ON DUPLICATE KEY UPDATE x = VALUES(x)` (deprecated từ MySQL 8.0.20) -> `x = ?` (AttemptDAO, PaperDAO).
- Nếu IntelliJ báo **"duplicate class: org.example.DAO.jdbc.AttemptDAO"**: là do file `DAO/AttemptDAO.java`
  cũ còn trên máy (chưa chạy `apply_deletes`). Gói này **ghi đè các file đã xóa bằng file rỗng (chỉ có comment)**,
  nên dù chưa chạy `apply_deletes` cũng hết đỏ; chạy `apply_deletes.bat` để xóa hẳn.

**Sửa cho schema không có AUTO_INCREMENT** (trước đây tạo exam / câu hỏi / đăng ký thi lỗi
`Field 'Exam_ID' doesn't have a default value`):
- MỚI `Jdbc.nextId(table, column)` = `SELECT COALESCE(MAX(id),0)+1 ... FOR UPDATE` (trong transaction).
- `ExamDAO.insert`, `QuestionDAO.insert`, `StudentDAO.register` truyền ID tự sinh (vẫn chạy được nếu sau này có AUTO_INCREMENT).
- `demo/03_demo_seed.sql`: Exam_ID / RegisID = MAX + 1.

**Không phụ thuộc view thiếu:** `ReportDAO` Q11 viết thẳng trên bảng; `PDMProjectQuery.sql` Q12 tự tạo view.

**Thứ tự chạy SQL trên Aiven:** 02 -> `05_upgrade_password_hashes.sql` -> (demo) `demo/03_demo_seed.sql`.

**Kiểm thử trên đúng file 02 của bạn (MySQL 8 Linux):** 03_verify 39/39; tạo exam/câu hỏi/paper/publish/đăng ký 7/7;
luồng sinh viên 11/11; chấm tay 8/8; đăng nhập 6/6; Reports 11/11 khớp file .sql; JUnit 14/14; migration 04 sạch.

---

## Bước 9 – Đồng bộ 03/04 theo 02 của nhóm, 04 chuyển tại chỗ

- `03_verify_...sql`: tên bảng VIẾT HOA (123 chỗ). Chạy ngay sau 02 của nhóm: **PASSED=39 | FAILED=0**.
- `04_migration_...sql` **viết lại, không còn dùng database `online_examination_old`**. Chạy thẳng trên
  `Online_Examination` đang là schema 20 bảng (như Aiven hiện tại):
  0. kiểm tra đúng là schema cũ (nếu không -> dừng, không đổi gì); đổi tên 20 bảng cũ thành `OLD_*`
     và gỡ khóa ngoại/CHECK cũ (tên khóa cũ trùng tên khóa mới, MySQL bắt tên khóa duy nhất trong DB);
  1. tạo 21 bảng mới (DDL y hệt 02);
  2. chép + chuyển dữ liệu trong 1 transaction, có kiểm tra trước/sau (lỗi -> ROLLBACK, bảng `OLD_*` còn nguyên);
     `Password_hash_` -> `Password_hash`, `CONTAINS` -> `EXAM_QUESTION_MARKS` + `PAPER_QUESTION`,
     `ATTEMPT.PaperID+Exam_ID` -> `PaperKey`, giữ `Status`, `Pass_mark` điểm thô -> %;
  3. tạo view / trigger / procedure (y hệt 02);
  4. tự xóa 20 bảng `OLD_*` -> **còn đúng 21 bảng** (in ra `TABLE_COUNT 21 OK`). Bước này chỉ chạy khi
     mọi bước trên thành công (script dừng ở lỗi đầu tiên).
  Kiểm chứng: DB cũ có dữ liệu (20 bảng) -> chạy 04 -> **21 bảng**, cấu trúc **giống hệt** DB dựng từ 02
  (so 312 mục: cột, khóa, ràng buộc, index, trigger, procedure, view), dữ liệu đủ; chạy lại lần 2 bị từ chối
  an toàn; dữ liệu cũ lỗi (Marks khác nhau giữa 2 paper) -> dừng ở bước kiểm tra, `OLD_*` còn nguyên, không xóa gì.
  **Nhớ backup trước khi chạy trên Aiven.** File được sinh bằng `tools`-script từ 02 + 04 cũ.

---

## Bước 10 – Rà project ngày 21/11 (zip nhóm gửi) và cập nhật phần còn cũ

**Nguyên nhân sinh viên `sv102` đăng nhập báo sai mật khẩu:** gói update trước bị giải nén vào thư mục lồng
`Project-PDM---Group-06_cub`, nên code chính vẫn là bản gốc – trong đó `AuthServiceImpl` cũ so mật khẩu dạng chuỗi
thô, không đọc được hash PBKDF2 (sau khi chạy 05). Gói này chép đè đúng chỗ.

**Còn cũ và đã thay** (giống hệt bản gốc, chưa nhận update): `AuthServiceImpl`, `StudentServiceImpl`,
`InstructorService(+Impl)`, `DatabaseConnection`, `DAO/jdbc/*` (Attempt, Exam, Jdbc, Paper, Question, Stats, Student,
User), `InstructorCoursesPage`, `pom.xml`, `.gitignore`, README/tài liệu, `01`/`02` SQL (02 = bản của nhóm).
`DAO/jdbc/ReportDAO` bản chỉ sửa view -> bản đầy đủ (InstructorServiceImpl mới cần các hằng Q1..Q11).
**Thêm mới:** `GradingDAO`, `GradingTab`, `ReportsTab`, test JUnit, `demo/03_demo_seed.sql`, `05_upgrade_password_hashes.sql`,
`queries/PDMProjectQuery.sql`, `04` (chuyển tại chỗ), `.gitattributes`.
**Giữ nguyên bản của nhóm:** `ExamCardView` (đã sửa comment), `03_verify` (cùng nội dung, khác thụt lề),
các DAO cũ đã dán bản sửa (`DAO/AnswerDAO`, `AttemptDAO`, `PaperDAO`, `QuestionOptionDAO`, `UserDAO`).

**Code cũ `org.example.DAO` + `MODEL` + `StudentResultService` (nhóm giữ lại) – sửa nốt cho 21 bảng:**
- `CourseDAO`, `TopicDAO`, `ExaminationDAO`: đúng tên cột (trước đọc `course_id`/`name`/`duration_min` không tồn tại).
- `QuestionDAO.SELECT_BASE`: `QUESTION/MCQ/SHORT_ANSWER` viết hoa, cột alias đúng tên `mapRow` đọc.
- `ScoreDAO`: `CONTAINS` -> `PAPER_QUESTION` + `EXAM_QUESTION_MARKS`; "đã nộp" = `EndTime IS NOT NULL`.
- `MODEL/Attempt.isSubmitted()`: `EndTime != null`. `MODEL/Score`: đậu khi **%** >= `Pass_mark` (Pass_mark là %).
- `StudentResultService`: bắt đầu lượt với `EndTime NULL`; MCQ lưu `AnswerText NULL` + `SELECT_TABLE`; chưa chấm = `NULL`.
- `database/student_results/01_seed_test.sql`, `02_cleanup_test.sql`: chuyển sang 21 bảng.
- `StudentResultsTestRunner`: kỳ vọng theo quy ước 21 bảng, xóa lượt seed sẵn của RegisID 9001 trước kịch bản.

**Kiểm thử trên chính project đã ghép (DB = 02 của nhóm, MySQL 8 Linux):** biên dịch sạch toàn bộ main + test;
`StudentResultsTestRunner` 56/56; đăng nhập 6/6; luồng sinh viên 11/11; chấm tay 8/8; tạo exam/câu hỏi/paper 7/7;
Reports 11/11 khớp file .sql; JUnit 14/14; DAO cũ đọc đúng dữ liệu (15 course, 18 topic, 19 exam, ...).
