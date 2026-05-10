# Hướng Dẫn Cài Đặt và Chạy Chương Trình (Dành cho Giảng viên)

Dự án này sử dụng Java Swing, SQLite và tích hợp AI Gemini để phân tích mã nguồn Codeforces. Dưới đây là các bước để thầy/cô có thể chạy chương trình trên môi trường Windows.

## 1. Yêu cầu hệ thống
Thầy/cô cần cài đặt sẵn các công cụ sau:
- **Java JDK 11** hoặc phiên bản mới hơn.
- **Maven** (Để quản lý thư viện và build dự án).
- **Trình duyệt Edge** (Dùng để Crawl dữ liệu qua Selenium).

## 2. Tải mã nguồn
Thầy/cô có thể clone dự án từ GitHub bằng lệnh sau:
```powershell
git clone https://github.com/nhatduy23022006-art/Project_Java.git
cd Project_Java
```

## 3. Cấu hình API Key (Bắt buộc)
Để tính năng AI hoạt động, chương trình cần sử dụng **Google Gemini API Key**. 
Thầy/cô vui lòng thực hiện các bước sau trong cửa sổ **PowerShell**:

```powershell
# Thiết lập Key (Thay bằng Key của thầy/cô)
$env:GEMINI_API_KEY = "AIzaSy..." 

# Thiết lập Model (Mặc định là gemini-1.5-flash)
$env:GEMINI_MODEL = "gemini-1.5-flash"
```

## 4. Khởi chạy chương trình
Sau khi đã thiết lập API Key trong PowerShell, thầy/cô chạy lệnh sau để khởi động ứng dụng:

```powershell
mvn clean compile exec:java
```

## 5. Lưu ý về Cơ sở dữ liệu
Dự án sử dụng **SQLite**, vì vậy thầy/cô **KHÔNG CẦN** cài đặt thêm MySQL hay bất kỳ database server nào khác. 
- Toàn bộ dữ liệu demo (Nick đã cào, bài nộp, kết quả phân tích) đã được tích hợp sẵn trong file `code_analyzer.db`. 
- Khi mở ứng dụng lên, các Nick và bài nộp sẽ tự động hiển thị để thầy/cô có thể kiểm tra ngay lập tức.

## 6. Các tính năng chính để kiểm tra:
1. **Đánh giá năng lực**: Chọn một tài khoản (ví dụ: `Redial`) và bấm nút **"Đánh giá năng lực"** để xem báo cáo tổng quan.
2. **Xem phân tích AI**: Bấm vào nút **"Xem phân tích"** ở danh sách bài nộp để xem AI đánh giá mã nguồn về CTDL/Thuật toán và mức độ nghi vấn sử dụng AI.
3. **Crawl dữ liệu**: Nhập một Nick Codeforces mới và bấm **"Crawl Codeforces"** (Hệ thống sẽ tự động bypass Cloudflare).

---
*Sinh viên thực hiện: [Tên của bạn]*
