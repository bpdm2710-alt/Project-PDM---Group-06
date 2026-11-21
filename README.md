# Project PDM — Group 06

## Normalization & Normalized SQL (21 bảng BCNF)

* [Tóm tắt chuẩn hóa và đối chiếu cardinality EER](docs/normalization/README.md)
* **Bộ script SQL 21 bảng BCNF (schema `Online_Examination`, đã kiểm chứng trên MySQL 8.4.11):** [database/normalized_21tables/](database/normalized_21tables/)
  * Nộp bài D5 / dựng mới DB mẫu: `02_online_examination_full_submission.sql`
  * Import dựng `.mwb` trên Workbench: `01_schema_ddl_for_mwb_import.sql`
  * Truy vấn Topic 20 & 39 test nghiệm thu: `03_verify_constraints_and_acceptance_N01_N24.sql`
  * Chuyển dữ liệu từ schema 20 bảng cũ: `04_migration_from_old_20tables_to_21tables.sql`
  * DB đã dựng bằng seed cũ (`sha256:...`) -> hash mật khẩu thật: `05_upgrade_password_hashes.sql`
* Query phân tích của project: [database/queries/PDMProjectQuery.sql](database/queries/PDMProjectQuery.sql)
  (giống hệt `ReportDAO`, xem trong app ở tab **Reports** của giảng viên)
* Dữ liệu demo ngày tương đối (chạy được mọi ngày): [database/demo/03_demo_seed.sql](database/demo/03_demo_seed.sql)
* Script schema 20 bảng cũ: [database/legacy_20tables/](database/legacy_20tables/) – **không chạy lên Aiven**

### Thứ tự chạy SQL (DB mới)
1. `normalized_21tables/02_online_examination_full_submission.sql` (bảng, view, trigger, procedure, seed)
2. (tùy chọn, chỉ trên bản sao) `normalized_21tables/03_verify_...sql` – phải ra `PASSED=39|FAILED=0`; chạy ngay sau 02 vì test cần dữ liệu seed gốc
3. `normalized_21tables/05_upgrade_password_hashes.sql` – đổi mật khẩu seed `sha256:...` thành hash thật (mật khẩu demo bên dưới)
4. `demo/03_demo_seed.sql` – các exam `DEMO - ...` để demo luồng làm bài

Lưu ý schema: tên bảng VIẾT HOA; chỉ `PAPER.PaperKey` là AUTO_INCREMENT, các ID khác
(`Exam_ID`, `RegisID`, `Quest_ID`, ...) do app tự sinh = MAX + 1 trong transaction (`Jdbc.nextId`).

### Tài khoản demo (sau bước 3 ở trên)
| Vai trò | Email | Mật khẩu |
|---|---|---|
| Giảng viên (ID 1–15) | `inst01@hcmiu.edu.vn` … `inst15@hcmiu.edu.vn` | `Inst@123` |
| Sinh viên (ID 101–120) | `sv101@student.hcmiu.edu.vn` … `sv120@student.hcmiu.edu.vn` | `Stu@123` |

Course 1 (PDM) do `inst01`, `inst02` dạy. Sau `demo/03_demo_seed.sql`: `sv101`–`sv103` làm được ngay exam `DEMO - Open now`.

### Build, test, đóng gói
* `mvn test` – unit test (không cần DB).
* `mvn package` – tạo `target/Online_Examination_System-1.0-SNAPSHOT-all.jar` (đã gồm MySQL driver);
  chạy: `java -jar target/Online_Examination_System-1.0-SNAPSHOT-all.jar`
  (`application.properties` được đóng vào jar từ `src/main/Sources/`; **đừng gửi jar có mật khẩu thật cho người ngoài nhóm**).
* Test end-to-end trên **MySQL cục bộ** (ghi dữ liệu, KHÔNG chạy lên Aiven): dựng DB bằng 02 + `demo/03_demo_seed.sql`,
  tạo `src/test/resources/application.properties` từ `application.properties.example` trỏ tới MySQL cục bộ, rồi
  `mvn test -Dit.db=local`.

Xem [quy ước tên schema của bộ SQL](docs/normalization/README.md#schema-mysql) trước khi dựng DB mẫu hoặc chuyển dữ liệu.

## MySQL Database Connection Setup

Hướng dẫn các thành viên thiết lập môi trường và kết nối Java project với MySQL database trên Aiven.

## 1. Clone Project in IntelliJ IDEA

Mọi người nên sử dụng **IntelliJ IDEA** để mở và chạy project.

1. Clone repository của nhóm về máy.
2. Mở project vừa clone trong IntelliJ IDEA.
3. Đọc file `application.properties.example` để biết cấu hình cần thiết.
4. Sử dụng **username và password được cấp riêng qua Zalo**. Mỗi thành viên phải sử dụng đúng tài khoản được cấp cho mình.

## 2. Configure the CA Certificate

Mọi người cần có file `ca.pem` và lưu tại vị trí phù hợp trên máy tính.

### Step 1: Open the IntelliJ Terminal

Mở Terminal trong IntelliJ IDEA và chạy:

```powershell
keytool -help
```

Nếu lệnh hiển thị danh sách các tùy chọn của `keytool`, bạn có thể tiếp tục.

### Step 2: Import the CA Certificate

Chạy lệnh sau trong Terminal. Thay đổi đường dẫn `ca.pem` nếu file của bạn nằm ở vị trí khác.

```powershell
keytool -importcert -trustcacerts `
  -alias aiven-mysql-ca `
  -file "D:\IU\PDMLAB\CA_CER\ca.pem" `
  -keystore "D:\IU\PDMLAB\CA_CER\aiven-truststore.p12" `
  -storetype PKCS12
```

- Khi được yêu cầu đặt mật khẩu cho truststore, hãy tạo mật khẩu và ghi nhớ nó.
- Khi được hỏi có tin cậy chứng chỉ hay không, nhập `yes` rồi nhấn Enter.
- Đảm bảo file `aiven-truststore.p12` được tạo thành công tại đường dẫn đã chỉ định.

**Lưu ý:** Đường dẫn trên chỉ là ví dụ theo máy hiện tại. Mỗi thành viên cần điều chỉnh đường dẫn theo vị trí lưu file của mình.

## 3. Configure `application.properties`

1. Tạo file `application.properties` từ file mẫu `application.properties.example` theo cấu trúc project.
2. Điền các thông tin kết nối được cấp.
3. Cập nhật đường dẫn truststore và mật khẩu truststore tương ứng với máy của bạn.

Không commit file `application.properties` chứa thông tin đăng nhập thật lên GitHub.

## 4. Configure MySQL Workbench

Mở MySQL Workbench và chỉnh sửa thông tin kết nối theo hình ảnh được chia sẻ trong nhóm.

- **Hostname / Port:** Sử dụng thông tin endpoint của Aiven.
- **Username / Password:** Sử dụng thông tin đăng nhập được cấp cho bạn.
- **SSL:** Bật SSL theo cấu hình của nhóm.
- **CA Certificate:** Chọn đúng đường dẫn đến file `ca.pem` trên máy của bạn.
<img width="885" height="352" alt="Screenshot 2026-10-23 061253" src="https://github.com/user-attachments/assets/731629a1-e3a7-4742-89b8-7e6a97a91689" />
<img width="830" height="363" alt="Screenshot 2026-10-23 061318" src="https://github.com/user-attachments/assets/93ce5b89-50f6-443d-9c50-324ffa60e55d" />


Sau khi hoàn tất, nhấn **Test Connection** để kiểm tra kết nối.

## 5. Verify the Connection

Sau khi cấu hình xong:

- [ ] IntelliJ IDEA mở project thành công.
- [ ] File `application.properties` đã được cấu hình đúng.
- [ ] Truststore đã được tạo và đường dẫn chính xác.
- [ ] MySQL Workbench kết nối thành công.
- [ ] Java database connection test chạy thành công.

Nếu gặp lỗi kết nối, hãy kiểm tra lại hostname, port, username, password, SSL và các đường dẫn trước khi báo lỗi lên nhóm.

Trong Workbench, mở schema theo cấu hình trong `application.properties`. File mẫu dùng `Online_Examination`. Script `02` dựng 21 bảng trong schema này trên môi trường kiểm thử. Số bảng trên Aiven phụ thuộc phiên bản đã triển khai.




  

