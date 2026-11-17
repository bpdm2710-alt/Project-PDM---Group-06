# Kế hoạch Normalization sau midterm

Ngày rà soát: 17/11/2026 (Việt Nam). Mốc repo: `main@3fb78481aa4c669ed836472d6bd68a1797ba7cae`.

Đây là gói rà soát và đề xuất của nhóm Normalization, dùng để thống nhất thiết kế với Leader, Database, Data Modelers, Query và Java. Chưa phải schema đã được nhóm phê duyệt hoặc migration cho database dùng chung.

## Kết luận cần hành động

Nhóm đã có EER và bản mapping 20 bảng. Việc tiếp theo là xác nhận các FD từ business rules, sửa khóa biểu diễn sai nghiệp vụ, chứng minh dạng chuẩn và bàn giao một schema thống nhất để Database/Query/Java triển khai. Forward engineering từ `.mwb` tạo ra DDL; nó không chứng minh FD, candidate keys, lossless join hoặc 3NF.

Ba việc phải chốt trước khi đóng băng schema:

1. Sửa khóa `select_table` theo câu hỏi và lần làm bài.
2. Chọn thiết kế `attempt` đạt ít nhất 3NF. Giữ `Exam_ID` và thêm composite FK không giải quyết partial dependency `RegisID -> Exam_ID`.
3. Chốt `Marks` theo paper hay cố định theo examination, vì hai cách hiểu cho hai tập FD khác nhau.

## Đọc và sử dụng gói này

| Tài liệu / công cụ | Mục đích |
| --- | --- |
| [01-review-and-decisions.md](01-review-and-decisions.md) | Phát hiện có bằng chứng, quyết định cần chốt và ảnh hưởng đến các nhóm |
| [02-fd-keys.md](02-fd-keys.md) | Bản nháp phần 5: toàn bộ 20 quan hệ, minimal cover, mọi candidate key và chứng minh |
| [03-normalization-proofs.md](03-normalization-proofs.md) | Bản nháp phần 6: 2NF/3NF/BCNF, lossless join, dependency preservation và anomalies |
| [04-handoff-and-tests.md](04-handoff-and-tests.md) | Hợp đồng schema đề xuất, checklist SQL/Java/Query và các test nghiệm thu |
| [05-validation-and-ai.md](05-validation-and-ai.md) | Kết quả thực chạy, giới hạn kiểm chứng và nội dung khai báo hỗ trợ AI |
| [fd-spec.json](fd-spec.json) | Tập FD và khóa mong đợi để kiểm tra lại khi business rules đổi |
| [normalization_check.py](../../tools/normalization_check.py) | Closure, minimal cover, mọi candidate key và dạng chuẩn theo F+ |
| [normalization_counterexamples.sql](../../database/review/normalization_counterexamples.sql) | Thí nghiệm MySQL bằng TEMPORARY TABLE, chứng minh lỗi khóa và sai exam |
| [audit_baseline.sql](../../database/review/audit_baseline.sql) | Truy vấn chỉ đọc để tìm dữ liệu vi phạm trong schema hiện tại |

Chạy kiểm tra toán học tại thư mục gốc repo, với Python 3.10+:

```text
python tools/normalization_check.py
python -m unittest discover -s tests -v
```

Kết quả BCNF của bản đề xuất có điều kiện: single-select MCQ, PaperKey toàn cục, teaching lặp học kỳ, Marks theo paper và các UNIQUE được chấp nhận. Kết quả này không khẳng định DDL hoặc database Aiven hiện tại đã đạt BCNF hay đã ép đủ business rules.

## Trạng thái đã xác minh

- `main` có Java/JDBC skeleton, README kết nối và Maven config; chưa có SQL, seed, chứng minh normalization, DAO, form hoặc query nghiệp vụ trong cây repo được kiểm tra.
- Nhánh `Feature/java_skeleton` ở sau `main` 5 commits và không có commit đi trước. Hai PR cũ đã merge; chưa thấy work item mở trong lần đọc này.
- README nói database dùng chung đã có 20 bảng. Chưa truy cập database đó; không suy ra nhóm chưa làm các phần chưa commit.
- `script.sql` được cung cấp có 20 bảng, các PK/FK cơ bản; chưa có UNIQUE nghiệp vụ, CHECK, referential actions viết tường minh, dữ liệu mẫu, trigger/procedure/view hoặc test.
- `.mwb` là nguồn model cần Data Modelers đối chiếu trực tiếp. Lần rà soát này chưa đọc được nội dung nhị phân của file; không có kết luận nó đồng bộ byte-for-byte với SQL.

## Phân công đề xuất cho ba thành viên

Giữ đúng vai trò đã ghi trong midterm; phân bổ dưới đây cần ba thành viên xác nhận và ghi nhận phần thực tế tự làm.

| Thành viên | Phần chịu trách nhiệm chính | Người review | Sản phẩm đo được |
| --- | --- | --- | --- |
| Nguyễn Mạnh Chiến | Điều phối business rules; kiểm tra FD/keys; chứng minh decomposition; tổng hợp phần 5–6 | Huân kiểm tra phép suy diễn; Khang kiểm tra ví dụ | Decision log đã chốt, bản report nhất quán và danh sách bàn giao |
| Trần Hồ Cao Huân | FD từng quan hệ; minimal cover; closures; mọi candidate key; kiểm tra 3NF/BCNF các bảng | Chiến | FD spec có căn cứ, tính tay các khóa và kiểm tra với công cụ |
| Nguyễn Đặng Duy Khang | Ví dụ anomalies; closure checker; test dữ liệu trước/sau với Database | Huân kiểm tra toán; Chiến đối chiếu nghiệp vụ | Hai ví dụ mỗi loại anomaly, test log và hướng dẫn tái lập |

Không ghi gói AI này thành khối lượng đã hoàn thành riêng của một thành viên. Mỗi người phải kiểm tra, sửa, giải thích và commit phần mình thực sự thực hiện.

## Kế hoạch 10 ngày làm việc

`D0` là ngày Leader xác nhận bắt đầu đợt chốt schema, không phải giả định hôm nay đang là tuần học nào. Chuyển sang ngày lịch sau khi xác nhận deadline của lớp.

| Mốc | Người chính | Việc cần hoàn tất | Đầu ra / điều kiện xong | Phụ thuộc |
| --- | --- | --- | --- | --- |
| D0 | Chiến + Leader | Đọc review; xác nhận file nguồn và database hiện dùng; chốt D01, D02, D03, D07; xác nhận hạn nộp | Decision log có người quyết định, ngày và căn cứ; một phiên bản schema chung | Leader, Database, Modelers |
| D1 | Huân | FD cho 20 quan hệ; phân biệt FD với FK, domain rule và tổng participation | Mỗi FD gắn business rule; không suy FD từ sample | Các quyết định khóa |
| D1–D2 | Khang | Chuẩn bị anomalies và chạy counterexamples; baseline audit trên bản sao dữ liệu được Database cung cấp | Log kết quả, lỗi có dữ liệu tái hiện; không sửa DB chung | Schema baseline |
| D2–D3 | Huân | Minimal cover; closure; liệt kê và chứng minh tối thiểu của mọi candidate key | Tính tay + checker khớp; xử lý các khóa thay thế Email, student-exam, paper-order | FD đã review |
| D3 | Chiến | Review chéo FD/keys; giải quyết điểm khác nhau về Marks và subtype | Chốt schema contract v1 cho Database/Java/Query | D0–D3 |
| D3–D4 | Chiến + Huân | Chứng minh dạng chuẩn và phân rã `attempt`; nếu cần, `contains`; dependency preservation | Phần 5–6 bản v1, proof lossless đúng tập FD | Schema contract v1 |
| D4–D5 | Database; Normalization review | DDL chuẩn hóa, constraints, transaction/trigger hoặc procedure; cập nhật `.mwb` và EER mapping | SQL trên DB sạch; diagram, từ điển dữ liệu và DDL khớp | Contract v1 |
| D5–D6 | Khang + Database | Chạy test PK/FK/UNIQUE/CHECK, cross-table rules, parent-update và rollback | Tất cả P0/P1 trong test matrix được xử lý hoặc có giới hạn được chấp nhận rõ | DDL mới |
| D6–D7 | Java + Query; Normalization hỗ trợ | Đổi join/DAO theo PaperKey, key selection, NULL; chốt cách tính 5 query đề bài | Có kết quả mong đợi bằng tay; 4/5 query truy cập qua UI theo đề | DDL đã ổn định |
| D8 | Chiến | Freeze phần 5–6 và hướng dẫn giải thích thiết kế; đồng bộ references + AI appendix | Không còn mâu thuẫn giữa FD, diagrams, SQL và report | Test và tích hợp |
| D9–D10 | Cả nhóm | Clone sạch, chạy SQL 2 lần, build Java, thử form; rehearsal hỏi đáp | Log nghiệm thu, phân công đã ký/xác nhận, gói nộp hoàn chỉnh | Tất cả nhóm |

Ước lượng phần việc Normalization: Chiến 9–12 giờ, Huân 10–14 giờ, Khang 9–12 giờ; chưa gồm thời gian chờ phản hồi và buổi rehearsal. Đây là dự toán, không phải giờ đã thực hiện.

## Điều chỉnh cần thiết so với midterm

Midterm đặt việc Database cập nhật DDL sau normalization ở tuần 10, trong khi DAO/query đã triển khai từ tuần 8–9. Khóa và cột liên quan `attempt`, `paper`, `contains`, `select_table` phải được chốt sớm hơn để Java và Query không phải làm lại các join.

Assignment, trang 5, ghi D3/D5/D6 ở tuần 10; Project Submission Guidelines, trang 1, ghi final ở tuần 11. Leader cần xác nhận hướng dẫn nào hiện được giảng viên áp dụng. Trong lúc chờ, đặt mốc hoàn tất nội bộ theo tuần 10; chưa khẳng định tuần 11 là hạn được phép nộp.

## Cách đóng góp trên GitHub

1. Review gói này qua draft PR. Chốt quyết định trong `01-review-and-decisions.md`; chỉ merge tài liệu khi nhóm hiểu các giả định và giới hạn.
2. Huân tạo branch `normalization/fd-keys` để kiểm tra và sửa FD/keys; Khang tạo `normalization/anomaly-tests` để bổ sung dữ liệu/test; Chiến tổng hợp `normalization/report-proof`. Đây là tên branch gợi ý, chưa được tự tạo cho từng người.
3. Database mở PR riêng cho schema/seed/constraints, liên kết decision log và test matrix. Modelers cập nhật EER source, ảnh export và `.mwb` trong cùng đợt schema; Query và Java review các cột/khóa chịu ảnh hưởng.
4. Không sửa độc lập cả `.mwb` và SQL theo hai hướng. ERDPlus là conceptual source; bản DDL đã review là implementation source. Sau sửa DDL, reverse engineer/đồng bộ Workbench rồi kiểm tra lại ý nghĩa conceptual. Mỗi bản release có version và changelog.
5. Đưa report/design hiện chưa nằm trên repo vào thư mục phù hợp khi Leader xác nhận bản cuối: `docs/design/`, `docs/reports/`, `database/schema.sql`, `database/seed.sql`, `database/queries.sql`. Không tạo bản sao report cũ rồi gọi đó là phiên bản hiện hành.

Mỗi PR ghi rõ: rule nào đổi, FD/key nào đổi, ảnh hưởng Database/Java/Query, cách tái lập test và phần chưa xác minh. Không merge thay đổi schema chỉ vì import vào Workbench thành công.

## Checklist hoàn tất phần Normalization

- [ ] FD bắt nguồn từ business rules đã được nhóm xác nhận.
- [ ] Mọi relation có đầy đủ attributes, minimal cover, mọi candidate key và proof tối thiểu.
- [ ] Từng quan hệ đạt ít nhất 3NF; không để `attempt` dưới 2NF rồi gọi là ngoại lệ 3NF/BCNF.
- [ ] Mỗi decomposition có proof lossless và phân tích dependency preservation riêng.
- [ ] Có ít nhất hai ví dụ cho từng update/insert/delete anomaly và trước/sau normalization.
- [ ] DDL, EER/mapping, `.mwb`, FD spec và query dùng cùng một phiên bản.
- [ ] Test có kết quả thực chạy; không đánh dấu các test tương lai là pass.
- [ ] Mỗi thành viên giải thích được vì sao Option_no/PaperID/Attempt_no không mặc nhiên là khóa toàn cục.
- [ ] Phần 5–6, contributions và AI appendix được các thành viên kiểm tra trước nộp.
