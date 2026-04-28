# Code Analyzer (Java)

Ứng dụng Java để nhập các nick Codeforces, crawl code định kỳ, sử dụng AI để phân tích CTDL/thuật toán và đánh giá mức độ sử dụng AI. **Sử dụng MySQL trên XAMPP**.

Tính năng hiện có:
- Nhập handle Codeforces.
- **Xóa handle**: Chọn handle trong tab `Handles`, bấm `Delete Handle` (xóa cascade: handle + submissions + analyses)
- Crawl định kỳ mỗi 24 giờ (bấm `Start Scheduler`) và crawl thủ công (bấm `Crawl Now`).
- Crawl Codeforces: lấy metadata từ API, cố gắng scrape source code.
- Lưu submissions và phân tích AI vào MySQL.
- Chống trùng dữ liệu theo `submissionId`.
- Tổng hợp đánh giá theo nick: số bài đã phân tích, điểm CTDL, điểm thuật toán, điểm nghi vấn dùng AI.
- GUI gồm 3 tab:
  - **Handles**: Danh sách các nick đã thêm (có thể Delete)
  - **Evaluations**: Tổng hợp điểm trung bình theo nick
  - **Submissions**: Xem chi tiết từng submission (chọn handle từ dropdown, bấm `View Code` để xem source code)

Yêu cầu:
- Java 11+
- Maven
- XAMPP (MySQL chạy)
- Biến môi trường `OPENAI_API_KEY` (để bật chức năng phân tích AI)

Setup MySQL trên XAMPP:

1. Chạy XAMPP, bật MySQL.
2. Mở browser vào `http://localhost/phpmyadmin/` hoặc dùng MySQL CLI:

```sql
CREATE DATABASE code_analyzer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Cài đặt & chạy:

1. Build:

```bash
mvn clean compile
```

2. Chạy (với MySQL XAMPP mặc định):

```powershell
$env:OPENAI_API_KEY="your_openai_api_key"
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="code_analyzer"
$env:DB_USER="root"
$env:DB_PASS=""
mvn exec:java "-Dexec.mainClass=com.example.codeanalyzer.Main"
```

Nếu bạn đã config MySQL khác, tuỳ chỉnh các biến trên.

Ghi chú:
- Codeforces crawler sử dụng API (`user.status`) để lấy metadata và cố gắng scrape trang submission để lấy source code. Code có thể không luôn khả dụng do quyền hiển thị.
- Analyzer gọi OpenAI Chat Completions API. Bạn cần cung cấp `OPENAI_API_KEY`. Kết quả phân tích kỳ vọng JSON gồm: `ds`, `algorithms`, `usedAI`, `confidence`, `dsScore`, `algoScore`, `aiScore`.
- Scheduler mặc định đặt lịch mỗi 24 giờ. Để test nhanh, sửa khoảng thời gian trong `Main`.
