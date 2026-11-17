# Rà soát và quyết định thiết kế

Status: DRAFT, 17/11/2026. Các đề xuất trong biên bản business rules không được coi là tất cả đã phê duyệt: hạn chốt còn trống và phần xác nhận chưa đầy đủ. Dấu tick ở mục MCQ là dấu hiệu cần xác nhận, không thay cho decision log cả nhóm.

## Nguồn và giới hạn

| Nguồn | Phần đã dùng |
| --- | --- |
| Repo `bpdm2710-alt/Project-PDM---Group-06`, main `3fb7848` | Cây file; README; pom; hai Java classes; cấu hình mẫu; nhánh và PR |
| `Midterm Report_Final.pdf` | Tr.3–4: thay đổi EER; tr.5–9: công cụ, phân công và tiến độ |
| `IT079IU_Project_Assignment_Sem1_2026-2027.pdf` | Tr.5–10: requirements/report/AI; tr.29–30: Topic 20 |
| `Project Submittion Guideline.pdf` | Tr.1–2: stages và deadlines |
| `BIÊN BẢN QUYẾT ĐỊNH BUSINESS RULE.docx` | Các phương án #1–#6 và danh sách mặc định |
| `script.sql` | Toàn bộ 20 CREATE TABLE và các ràng buộc đã khai báo; nội dung văn bản được cung cấp |
| EER image cung cấp trong cuộc trao đổi | User/Question ISA, weak Paper/Attempt/Options, relationship attributes và derived totals |
| `tables_script.mwb` | Chỉ xác minh được file có mặt; chưa đọc được model nhị phân. Cần Modelers đối chiếu trong Workbench |

Không kết nối tới Aiven; không kết luận về dữ liệu hoặc schema live. Các lỗi DDL bên dưới dựa trên file SQL được cung cấp, không phải một file SQL đã có trong main.

## Findings ưu tiên

| ID | Mức | Bằng chứng / hiện trạng | Hệ quả và việc cần làm |
| --- | --- | --- | --- |
| F01 | P0 | `select_table` PK `(Option_no, Attempt_no, RegisID)` | Hai câu cùng chọn option 1 đụng PK; một câu chọn cả option 1 và 2 lại có thể được chấp nhận. Chốt single/multi-select rồi sửa PK |
| F02 | P0 | `attempt` giữ RegisID và Exam_ID; FK RegisID và FK paper tách nhau | Có thể chọn paper khác exam đăng ký. Theo nghiệp vụ, `RegisID -> Exam_ID` gây partial dependency trên key `(Attempt_no, RegisID)` |
| F03 | P0 có điều kiện | Midterm: Marks theo paper; assignment: mark theo examination | Nếu `Exam_ID, Quest_ID -> Marks`, `contains` hiện cũng dưới 2NF. Không khẳng định chỉ `attempt` cần phân rã trước khi chốt |
| F04 | P1 | Không UNIQUE Email, `(User_ID, Exam_ID)` ở registration, hoặc paper-question order | Không ép login email duy nhất, một đăng ký/student-exam, hay thứ tự câu không trùng |
| F05 | P1 | `teach` PK `(User_ID, Course_ID)` | Không lưu được cùng giảng viên/môn ở hai học kỳ. Đây là lỗi mô hình nếu cần lịch sử học kỳ, không phải tự thân một vi phạm 3NF |
| F06 | P1 | Các subtype chỉ FK tới parent | Có thể ở cả hai subtype hoặc không subtype; disjoint/total chưa được ép |
| F07 | P1 | `select_table` chỉ FK option và attempt | Selection có thể không có hàng `answer` tương ứng; điểm tổng bị thiếu dù đã chọn đáp án. Cần FK tới answer hoặc một cơ chế atomic tương đương |
| F08 | P1 | EndTime, AnswerText, MarkAwarded đều NOT NULL | Không biểu diễn rõ đang thi/chưa chấm; MCQ bị ép có text không có nghĩa. Xác định lifecycle và NULL |
| F09 | P1 | Answer chỉ FK question, attempt | Không ép question thuộc assigned paper; không ép MarkAwarded <= Marks hoặc số attempt tối đa |
| F10 | P1 | DDL chỉ CREATE IF NOT EXISTS, tắt FK/unique checks khi chạy | Không cập nhật cấu trúc bảng đã tồn tại; chạy lại không chứng minh rebuild đúng. Cần script submission chạy 2 lần trên môi trường demo riêng |
| F11 | P1 | Không CHECK hoặc ON DELETE/ON UPDATE tường minh | Chưa đáp ứng bộ constraints đề bài; thêm bounds và referential actions có lý do |
| F12 | P1 | `Sources` trong Git, `sources` trong pom; cấu hình credential chưa bị gitignore | Có rủi ro không copy resources trên hệ thống phân biệt hoa thường; file cấu hình thật có thể vô tình commit. Java xử lý trong PR riêng |
| F13 | P1 | README nhắc Online_Examination, SQL tạo online_examination1, URL mẫu trỏ defaultdb | Nhóm cần một tên schema/cách chọn schema có cấu hình, tránh chạy nhầm hoặc test không cùng database |
| F14 | P2 | Chưa có chính sách sửa câu hỏi/options/paper sau khi có attempt | Sửa dữ liệu tham chiếu có thể thay đổi ý nghĩa lịch sử; chọn freeze hoặc versioning và test |
| F15 | P2 | `Password_hash_` lệch tên; Semester là chuỗi tự do | Đổi tên thống nhất; quy định Semester chứa năm học và kỳ để không nhập nhằng qua các năm |

## Kiểm tra lại các đề xuất trong biên bản cũ

### Giữ Exam_ID + composite FK

Thêm `UNIQUE(RegisID, Exam_ID)` ở registration rồi FK từ attempt có thể ép exam khớp; nhưng trong attempt vẫn có `RegisID -> Exam_ID`. Đây là denormalization dưới 2NF, không phải trường hợp dừng ở 3NF thay vì BCNF. Assignment yêu cầu mọi quan hệ ít nhất 3NF; chỉ ghi lý do vào report chưa đủ để đáp ứng. Không chọn mặc định phương án này nếu chưa được giảng viên chấp nhận ngoại lệ rõ ràng.

### Thêm discriminator cho ISA

Role/Quest_Type và FK ghép với discriminator cố định có thể hỗ trợ disjointness; không bắt parent phải có child. Total participation vẫn cần một cơ chế cho toàn bộ vòng đời insert/delete/retype. Không ghi rằng chỉ thêm cột loại là đã ép đủ total/disjoint.

Nếu đưa một cột discriminator hằng vào mọi hàng của subtype, cần nói rõ nó là chi tiết enforcement và xét ảnh hưởng của FD hằng `empty-set -> discriminator`. Bản FD logic đề xuất ở đây giữ subtype như hiện tại, chưa tự thêm cột lặp lại để rồi tuyên bố tất cả bảng vật lý BCNF.

### Email, boolean và đáp án đúng

`Email LIKE '%@%'` chỉ là kiểm tra sơ bộ; không chứng minh email hợp lệ hoặc thuộc trường. Chốt domain/email normalization ở Java và UNIQUE/NOT NULL ở DB. `TINYINT(1)` không tự giới hạn Bool_Correct về 0/1: cần CHECK. Với câu single-answer MCQ thông thường, chốt đúng một option đúng, không chỉ ít nhất một, và ít nhất hai options. Đây là constraint đếm liên hàng.

### Cross-table constraints

MySQL CHECK không cho tham chiếu cột bảng khác hoặc subquery. Dùng trigger/procedure/transaction cho các rule liên bảng và ghi rõ việc nào do Java kiểm tra bổ sung. Cần kiểm tra cả update ở parent: thay Exam_ID của registration/paper, giảm Max_attempts, giảm Marks hoặc sửa membership đều có thể làm dữ liệu cũ sai.

MySQL 8.0/InnoDB không chạy trigger cho thao tác FK cascade. Vì vậy không dùng cascade như một đường cập nhật rồi kỳ vọng trigger child tự kiểm tra business rules; cân nhắc RESTRICT và procedure kiểm soát thay đổi.

Nguồn kỹ thuật: [MySQL 8.0 CHECK constraints](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html), [MySQL 8.0 foreign keys](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html). Chọn MySQL 8.0.16+ nếu dùng enforced CHECK; ghi phiên bản máy chạy thực tế trong README/report.

## Decision log cần hoàn thiện

| ID | Quyết định | Đề xuất để nhóm xem xét | Người chốt / ngày / kết quả |
| --- | --- | --- | --- |
| D01 | Single-select hay multi-select MCQ? | Single-select; PK selection `(Quest_ID, Attempt_no, RegisID)`; exactly one correct option cho mỗi MCQ hoàn chỉnh | Chưa ghi nhận đầy đủ |
| D02 | Thiết kế attempt đạt 3NF | Thêm PaperKey surrogate, giữ UNIQUE `(Exam_ID, PaperID)`, bỏ Exam_ID khỏi attempt; rule same-exam bằng transaction/trigger | Chờ Leader + Normalization + Database + Modelers |
| D03 | Teach lặp môn/học kỳ? | Có; PK `(User_ID, Course_ID, Semester)`; Semester gồm năm học/kỳ. Chưa mô hình lớp học riêng | Chờ nhóm |
| D04 | Enforce ISA total/disjoint ở đâu? | Giữ superclass/subclasses; atomic creation + kiểm tra disjoint, delete/retype, quyền ghi qua đường kiểm soát; thêm audit. Xác định rõ giới hạn của enforcement | Chờ Database/Java; FD spec phải đổi nếu thêm discriminator |
| D05 | Trạng thái làm dở/chưa chấm | EndTime/AnswerText/MarkAwarded nullable theo ngữ nghĩa; không dùng 0 để thay chưa chấm | Chờ Query + Java + Database |
| D06 | Rule nào DB bảo vệ? | PK/FK/UNIQUE/CHECK + core cross-table rules bằng DB routine/trigger, Java validate thêm; có test update cả hai phía | Chờ Database |
| D07 | Marks theo paper hay examination? | Dùng midterm per-paper làm baseline phân tích; cần xác nhận với Leader/giảng viên trước khi chốt. Nếu exam-level, áp dụng decomposition riêng | Chưa chốt; quyết định ảnh hưởng số bảng |
| D08 | Email, registration, QuestionOrder duy nhất? | UNIQUE Email; UNIQUE student-exam; UNIQUE paper-order; NOT NULL trên các key attributes | Đã được biên bản đề xuất mặc định; cần đồng bộ DDL |
| D09 | Mẫu số của query >70% và average topic | Tính theo sinh viên, không vô tình theo số attempts; chọn attempt đại diện và cách coi bỏ trống/partial credit. Average topic dùng tỉ lệ earned/max để so được paper khác tổng điểm | Chờ Query + Leader |
| D10 | Quy tắc course-topic và lịch sử | Course–Topic tiếp tục M:N theo midterm; paper chỉ lấy topic thuộc course; freeze dữ liệu được tham chiếu sau khi có attempt, trừ thao tác sửa được kiểm soát | Chờ Modelers/Database/Java |
| D11 | Điểm pass và thời gian | Pass_mark là điểm thô hay phần trăm; các paper có cùng total không; RegisDate có thể bằng Exam_date không; Exam_date là lịch mở hay thời điểm bắt đầu cứng | Chờ nhóm, không tự áp đặt '<' thay '<=' |
| D12 | Nguồn chuẩn và deadline | Xác nhận DB/schema live, file nguồn mới nhất, deadline tuần 10 hay 11; dùng tuần 10 làm mốc nội bộ thận trọng | Leader xác nhận |

## Hai phương án 3NF cho Paper/Attempt

| Phương án | Khóa và enforcement | Ảnh hưởng |
| --- | --- | --- |
| Giữ natural weak key | Paper `(Exam_ID, PaperID)`; attempt bỏ Exam_ID, giữ PaperID + RegisID. Suy Exam_ID từ registration rồi kiểm tra paper bằng trigger/procedure; kiểm soát cả parent update/delete | Ít đổi conceptual, nhưng không còn FK trực tiếp từ attempt tới paper bằng cặp cột lưu tại attempt |
| Thêm surrogate, đề xuất trong spec | Paper có PK PaperKey, UNIQUE `(Exam_ID, PaperID)`; attempt FK PaperKey và RegisID, không có Exam_ID. Kiểm tra exam của hai parent phải bằng nhau | Đổi contains/attempt/query join và model vật lý; FK tới paper rõ ràng hơn. Vẫn cần rule same-exam liên bảng |

Không biến PaperID cục bộ thành global mà không migration mapping. Thêm surrogate vẫn giữ ý nghĩa Paper phụ thuộc Examination và khóa tự nhiên; việc có surrogate vật lý không tự loại bỏ weak entity trong EER. Options và Attempt cũng còn weak entities.

## Đề xuất ngoài scope Normalization

Java cần kiểm tra casing resource, external connection settings không phải sửa rồi recompile để đổi server, version JDK/driver, thiếu/null truststore config và error handling. Đây là phát hiện read-only, chưa sửa trong gói này. Không có DAO/form để review nghiệp vụ sâu hơn tại mốc commit đang xét.
