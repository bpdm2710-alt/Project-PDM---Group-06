# Online Examination System — Project PDM, Group 06

Ứng dụng desktop **Java Swing + MySQL** cho thi trắc nghiệm / tự luận online:
sinh viên đăng ký và làm bài có giới hạn thời gian, giảng viên ra đề, chấm tự luận, xem báo cáo và thống kê.

| | |
|---|---|
| Ngôn ngữ / UI | Java 21, Swing |
| Database | MySQL 8.4 trên **Aiven** (SSL `VERIFY_CA`), schema `Online_Examination`, **21 bảng BCNF** |
| Truy cập DB | JDBC + HikariCP (connection pool) |
| Build | Maven (`maven-shade` → 1 file jar chạy được) |

**Tính năng chính**
* Đăng nhập theo vai trò (mật khẩu hash PBKDF2-SHA256).
* Sinh viên: xem khóa học, đăng ký exam (kiểm tra **môn tiên quyết**), làm bài có đồng hồ, lưu từng câu, **Resume** khi lỡ thoát, xem kết quả.
* Giờ thi theo **đồng hồ của DB** (+07:00), không phụ thuộc giờ máy người dùng; lượt quá giờ tự đóng và tự chấm.
* Một exam có **nhiều paper (đề)**, lượt mới được xoay vòng sang đề khác.
* Chấm tự động MCQ / tự luận khớp đáp án; còn lại vào tab **Grading** cho giảng viên.
* Giảng viên: quản lý exam (Draft → Publish), Build paper, Question bank, **Reports Q1–Q11**, Statistics.

👉 **Kịch bản demo chi tiết: [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)**

---

## Mục lục
1. [Chuẩn bị phần mềm](#1-chuẩn-bị-phần-mềm)
2. [Clone project](#2-clone-project)
3. [Tạo truststore từ CA certificate](#3-tạo-truststore-từ-ca-certificate)
4. [Cấu hình `application.properties`](#4-cấu-hình-applicationproperties)
5. [Cấu hình MySQL Workbench](#5-cấu-hình-mysql-workbench)
6. [Chạy thử demo](#6-chạy-thử-demo)
7. [Tài khoản demo](#7-tài-khoản-demo)
8. [Xử lý lỗi thường gặp](#8-xử-lý-lỗi-thường-gặp)
9. [Các file SQL](#9-các-file-sql)
10. [Build, test, đóng gói](#10-build-test-đóng-gói)
11. [Cấu trúc project](#11-cấu-trúc-project)

---

## 1. Chuẩn bị phần mềm

* **Git**
* **IntelliJ IDEA** (Community hoặc Ultimate) — Maven có sẵn trong IntelliJ, không cần cài riêng
* **JDK 21** trở lên (IntelliJ: *File → Project Structure → SDK → Download JDK* nếu chưa có)
* **MySQL Workbench 8.x** (để chạy script SQL demo)
* Từ nhóm (qua **Zalo**): file `ca.pem`, **username / password Aiven riêng của bạn**

## 2. Clone project

```bash
git clone https://github.com/bpdm2710-alt/Project-PDM---Group-06.git
```

Hoặc trong IntelliJ: *File → New → Project from Version Control* → dán URL trên → **Clone**.

Sau khi mở project:
1. Chờ IntelliJ nhận `pom.xml`; nếu chưa tự tải thư viện, mở tab **Maven** (bên phải) → bấm **Reload All Maven Projects** 🔄.
2. Kiểm tra *Project Structure → Project SDK* = 21.

Lần sau chỉ cần `git pull` (hoặc *Git → Pull*) rồi **Maven Reload**.

## 3. Tạo truststore từ CA certificate

Lưu `ca.pem` vào một thư mục trên máy, ví dụ `D:\IU\PDMLAB\CA_CER\`.

**Bước 1** — Mở Terminal trong IntelliJ, kiểm tra `keytool` có sẵn:

```powershell
keytool -help
```

**Bước 2** — Tạo truststore PKCS12 (đổi đường dẫn cho đúng máy bạn):

```powershell
keytool -importcert -trustcacerts `
  -alias aiven-mysql-ca `
  -file "D:\IU\PDMLAB\CA_CER\ca.pem" `
  -keystore "D:\IU\PDMLAB\CA_CER\aiven-truststore.p12" `
  -storetype PKCS12
```

* Khi được hỏi, **đặt mật khẩu truststore** và ghi nhớ (dùng ở bước 4).
* Khi hỏi *Trust this certificate?* gõ `yes` → Enter.
* Kiểm tra file `aiven-truststore.p12` đã được tạo.

> Không tìm thấy `keytool`? Dùng bản trong JDK: `"<thư mục JDK>\bin\keytool.exe" ...`

## 4. Cấu hình `application.properties`

1. Copy `src/main/Sources/application.properties.example` → **`src/main/Sources/application.properties`** (cùng thư mục).
2. Điền thông tin:

```properties
db.url=jdbc:mysql://mysql-224eb3e6-pn8935916-f2fb.h.aivencloud.com:20365/Online_Examination?sslMode=VERIFY_CA
db.username=<username được cấp qua Zalo>
db.password=<password được cấp qua Zalo>
db.ssl.truststore=D:/IU/PDMLAB/CA_CER/aiven-truststore.p12
db.ssl.truststore.password=<mật khẩu truststore bạn đặt ở bước 3>

# tùy chọn (có giá trị mặc định)
# db.timezone=+07:00
# db.pool.size=5
```

* Đường dẫn truststore dùng dấu `/` (hoặc `\\`).
* Mỗi thành viên dùng **đúng tài khoản được cấp cho mình**.

> ⚠️ **Không commit `application.properties`** (đã có trong `.gitignore`). Trước khi commit, xem lại tab *Commit*
> không có file này. Không gửi file jar đã build (có chứa mật khẩu) cho người ngoài nhóm.

## 5. Cấu hình MySQL Workbench

Tạo connection mới:
* **Hostname / Port:** endpoint Aiven (`mysql-224eb3e6-pn8935916-f2fb.h.aivencloud.com`, port `20365`)
* **Username / Password:** tài khoản được cấp cho bạn
* **SSL:** *Use SSL* = `Require and Verify CA`, **SSL CA File** = đường dẫn `ca.pem`

<img width="885" height="352" alt="Workbench connection" src="https://github.com/user-attachments/assets/731629a1-e3a7-4742-89b8-7e6a97a91689" />
<img width="830" height="363" alt="Workbench SSL" src="https://github.com/user-attachments/assets/93ce5b89-50f6-443d-9c50-324ffa60e55d" />

Bấm **Test Connection** → thành công thì mở schema **`Online_Examination`** (phải có 21 bảng tên VIẾT HOA).

Checklist:
- [ ] Project mở được trong IntelliJ, Maven Reload không lỗi
- [ ] `application.properties` đã điền đúng
- [ ] Truststore đã tạo, đường dẫn đúng
- [ ] Workbench kết nối thành công

## 6. Chạy thử demo

DB trên Aiven **đã được dựng sẵn** (script `02` + `05`). Bạn **chỉ cần nạp dữ liệu demo**:

1. Workbench → *File → Open SQL Script* → `database/demo/03_demo_seed.sql` → bấm ⚡ (**Execute all**).
2. Nếu Workbench tắt auto-commit, chạy thêm `COMMIT;`.
3. Kết quả cuối phải có 4 exam: `DEMO - Open now`, `DEMO - Upcoming`, `DEMO - Expired attempt`, `DEMO - Draft`.
4. IntelliJ: mở `src/main/java/org/example/Main.java` → bấm ▶ **Run 'Main.main()'**.
5. Đăng nhập `sv103@student.hcmiu.edu.vn` / `Stu@123` → tab **My exams** → `DEMO - Open now` → **Start exam**.

Làm tiếp theo **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)** (phần A → E: làm bài, hết giờ, chấm bài, báo cáo, môn tiên quyết).

> ⚠️ DB là **dùng chung**: chạy `03_demo_seed.sql` sẽ **reset dữ liệu `DEMO - ...` của cả nhóm**. Báo trong nhóm trước khi chạy.
> `DEMO - Open now` mở **180 phút** kể từ lúc chạy script; quá giờ thì chạy lại script.
> Chỉ `sv101`, `sv102`, `sv103` được đăng ký sẵn exam này.

## 7. Tài khoản demo

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Giảng viên (ID 1–15) | `inst01@hcmiu.edu.vn` … `inst15@hcmiu.edu.vn` | `Inst@123` |
| Sinh viên (ID 101–120) | `sv101@student.hcmiu.edu.vn` … `sv120@student.hcmiu.edu.vn` | `Stu@123` |

Tài khoản dùng trong kịch bản:

| Tài khoản | Dùng để demo |
|---|---|
| `sv103` | Làm bài `DEMO - Open now`, nộp, làm lượt 2 rồi **Resume**, xem kết quả sau khi chấm (55/60) |
| `sv101` | Lượt `DEMO - Expired attempt` bị hệ thống tự đóng khi hết giờ (10/40) |
| `inst01` | Khóa PDM: Grading, Reports Q1–Q11, Statistics, Publish `DEMO - Draft`, Question bank |
| `sv104` | Đăng ký `DEMO - Upcoming` → **bị chặn** vì chưa đậu môn tiên quyết DSA |
| `sv106` | Đăng ký `DEMO - Upcoming` → thành công (đã đậu DSA) |

## 8. Xử lý lỗi thường gặp

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| `Cannot connect to the database: ...` | Sai `application.properties` (username, password, đường dẫn / mật khẩu truststore), hoặc mất mạng. File phải nằm đúng `src/main/Sources/`. |
| `Email or password incorrect` dù nhập đúng | Code cũ → `git pull` + Maven Reload. Nếu DB mới dựng lại: chạy `05_upgrade_password_hashes.sql` + `COMMIT;`. |
| Không thấy exam để làm / thẻ toàn **COMPLETED** | Seed demo chưa chạy hoặc đã quá 180 phút → chạy lại `03_demo_seed.sql` + `COMMIT;`. Đăng nhập đúng sv101–sv103. |
| Chạy script xong app không thấy thay đổi | Workbench đang tắt auto-commit → `COMMIT;`. |
| IntelliJ báo đỏ tên bảng/cột trong file `.sql` hoặc chuỗi SQL | Data source của IntelliJ còn cache schema cũ → tab *Database* → chuột phải data source → **Refresh**. Không ảnh hưởng chạy app. |
| Báo đỏ `import com.zaxxer...` / thiếu thư viện | Chưa Maven Reload. |
| `Unsupported class file major version` / lỗi Java | Project SDK chưa phải 21. |

## 9. Các file SQL

| File | Dùng khi nào |
|---|---|
| `database/demo/03_demo_seed.sql` | **Trước mỗi buổi demo** (chạy lại thoải mái, chỉ đụng tới exam `DEMO - ...`) |
| `database/normalized_21tables/02_online_examination_full_submission.sql` | Dựng mới toàn bộ DB: 21 bảng, view, trigger, procedure, seed (**xóa dữ liệu cũ**, không chạy lên Aiven nếu chưa thống nhất) |
| `database/normalized_21tables/05_upgrade_password_hashes.sql` | Ngay sau `02`: đổi mật khẩu seed thành hash PBKDF2 (`Inst@123` / `Stu@123`) |
| `database/normalized_21tables/03_verify_constraints_and_acceptance_N01_N24.sql` | Test ràng buộc (phải ra `PASSED=39 / FAILED=0`), chạy ngay sau `02` trên bản sao. **Không phải file demo** |
| `database/normalized_21tables/01_schema_ddl_for_mwb_import.sql` | Chỉ DDL, để Reverse Engineer ra `.mwb` trong Workbench |
| `database/queries/PDMProjectQuery.sql` | Query phân tích Q1–Q11 (giống tab **Reports** trong app) + Q12 bản view của Q11 |
| `database/student_results/01_seed_test.sql`, `02_cleanup_test.sql` | Dữ liệu test riêng (ID 9001+) và dọn dẹp |

Dựng lại DB từ đầu (chỉ khi cần): `02` → (tùy chọn `03_verify` trên bản sao) → `05` → `demo/03_demo_seed.sql` → `COMMIT;`

Ghi chú schema: tên bảng **VIẾT HOA**; chỉ `PAPER.PaperKey` là AUTO_INCREMENT, các ID khác app tự sinh = MAX + 1
trong transaction. Chi tiết chuẩn hóa: [docs/normalization/README.md](docs/normalization/README.md),
sơ đồ: [docs/design/](docs/design/).

## 10. Build, test, đóng gói

```bash
mvn test        # unit test, không cần DB
mvn package     # tạo target/Online_Examination_System-1.0-SNAPSHOT-all.jar
java -jar target/Online_Examination_System-1.0-SNAPSHOT-all.jar
```

* Jar được đóng kèm `application.properties` của máy bạn → **không chia sẻ ra ngoài nhóm**.
* Test end-to-end (`EndToEndTest`) **ghi dữ liệu** → chỉ chạy trên **MySQL cục bộ**, KHÔNG chạy lên Aiven:
  dựng DB local bằng `02` + `05` + `demo/03_demo_seed.sql`, tạo `src/test/resources/application.properties`
  trỏ tới `localhost`, rồi `mvn test -Dit.db=local`.

## 11. Cấu trúc project

```
src/main/java/org/example/
├── Main.java                 # điểm chạy app
├── ui/                       # Swing: LoginFrame, StudentDashBoard, InstructorDashBoard, panel/, components/
├── Service/                  # nghiệp vụ: AuthServiceImpl, StudentServiceImpl, InstructorServiceImpl, Db (giờ DB)
├── DAO/jdbc/                 # truy vấn SQL: AttemptDAO, ExamDAO, ReportDAO, StudentDAO, ...
├── session/                  # Session (người dùng đang đăng nhập)
└── util/                     # DatabaseConnection (HikariCP), PasswordHasher
src/main/Sources/             # application.properties(.example)
src/test/java/                # unit test + it/EndToEndTest
database/                     # các file SQL (mục 9)
docs/                         # DEMO_SCRIPT, normalization, design, ui
```

Lịch sử thay đổi theo từng bước: [UPDATE_NOTES.md](UPDATE_NOTES.md).

---

**Group 06 — Principles of Database Management, International University (VNU-HCM)**



  

