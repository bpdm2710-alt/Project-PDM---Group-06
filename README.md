# Project PDM — Group 06

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

Khi vào MySQL các bạn sẽ thấy 1 database tên 'Online_Examination' được tạo sẵn, trong đó có 20 bảng đã đuo




  

