# Codeforces Source Crawler & AI Analyzer

Dự án Java giúp cào mã nguồn từ Codeforces, lưu trữ vào cơ sở dữ liệu và sử dụng AI (Google Gemini) để phân tích thuật toán, cấu trúc dữ liệu và đánh giá mức độ sử dụng AI trong code.

## Tính năng chính
- **Crawl Codeforces**: Tự động tải mã nguồn các bài nộp từ nhiều tài khoản khác nhau.
- **AI Analysis**: Tích hợp Google Gemini để phân tích logic, độ tối ưu và phát hiện code do AI viết.
- **Đánh giá năng lực**: Tổng hợp dữ liệu từ nhiều bài nộp để đưa ra báo cáo năng lực tổng quan cho từng Account.
- **Auto-crawl**: Chế độ tự động quét code mới mỗi 12 giờ.

## Hướng dẫn cài đặt

### 1. Yêu cầu hệ thống
- Java JDK 11 hoặc mới hơn.
- Maven.
- Google Gemini API Key.

### 2. Cấu hình
Tạo một file chạy (ví dụ `run_app.ps1` trên Windows hoặc `run_app.sh` trên Linux) và thiết lập các biến môi trường sau:

```powershell
$env:GEMINI_API_KEY = "AIzaSyDPT-aod9LF1_FQ1VsdVoTkmSLMU1wc1ww"
$env:GEMINI_MODEL = "gemini-2.5-flash"
mvn exec:java
```

### 3. Chạy ứng dụng
Mở terminal tại thư mục dự án và chạy lệnh:
```bash
mvn clean compile exec:java
```

## Cơ sở dữ liệu
Dự án sử dụng **SQLite**, dữ liệu được lưu trữ tự động vào file `code_analyzer.db` trong thư mục gốc. Bạn không cần cài đặt thêm server database nào khác.

---
*Dự án được phát triển như một Bài tập lớn môn Lập trình Java.*
