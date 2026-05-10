# HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG HỆ THỐNG PHÂN TÍCH MÃ NGUỒN

Tài liệu này hướng dẫn chi tiết các bước để thiết lập và kiểm tra đồ án **"Hệ thống tự động Crawl và Phân tích mã nguồn Codeforces bằng AI"**.

---

## 1. Yêu cầu hệ thống
Trước khi bắt đầu, hãy đảm bảo máy tính đã cài đặt:
- **Java JDK 11** trở lên.
- **Apache Maven** (để quản lý thư viện và chạy dự án).
- **Trình duyệt**: Microsoft Edge (Khuyến nghị) hoặc Google Chrome.

---

## 2. Cấu hình Gemini API Key (Bắt buộc)
Để tính năng phân tích AI hoạt động, thầy/cô cần cung cấp API Key của Google Gemini.

1. Mở **PowerShell** và di chuyển vào thư mục dự án.
2. Chạy lệnh sau (thay `KEY_CỦA_BẠN` bằng API Key thực tế):

```powershell
$env:GEMINI_API_KEY = "KEY_CỦA_BẠN"
```

*Lưu ý: Nếu không thiết lập Key, chương trình vẫn chạy được các tính năng Crawl và xem dữ liệu cũ, nhưng tính năng "Phân tích AI" sẽ báo lỗi.*

---

## 3. Cách khởi chạy chương trình
Dự án được thiết kế để chạy ngay lập tức mà không cần cài đặt Database phức tạp. Thầy/cô chỉ cần chạy lệnh duy nhất sau trong thư mục dự án:

```powershell
mvn clean compile exec:java
```

---

## 4. Lưu ý về Cơ sở dữ liệu (SQLite)
Dự án sử dụng **SQLite**, một hệ quản trị CSDL dạng file nhẹ.
- **Không cần cài MySQL**: Toàn bộ dữ liệu được lưu trong file `code_analyzer.db`.
- **Dữ liệu Demo**: Tôi đã tích hợp sẵn một số tài khoản (ví dụ: `Redial`, `tourist`) và các bài nộp đã được phân tích AI để thầy/cô có thể kiểm tra ngay lập tức mà không cần crawl mới.

---

## 5. Cơ chế Crawl dữ liệu (Tự động & Thông minh)
Hệ thống crawl của dự án đã được tối ưu hóa để tránh các lỗi phổ biến:

### 🛠️ Giải quyết lỗi Driver (Tự động)
Trước đây, người dùng thường gặp lỗi: *"Crawl thất bại: Không tìm thấy EdgeDriver tại drivers/msedgedriver.exe"*. 
- **Hiện tại**: Tôi đã tích hợp **Selenium Manager**. Chương trình sẽ tự động nhận diện phiên bản trình duyệt trên máy thầy/cô và **tự động tải driver tương ứng** về. Thầy/cô không cần phải tải hay copy file `.exe` thủ công vào thư mục dự án nữa.

### 🛡️ Vượt rào cản Cloudflare
Codeforces sử dụng Cloudflare để chặn Bot. Hệ thống này xử lý bằng cách:
1. Mở một cửa sổ trình duyệt Edge thật.
2. Nếu gặp Cloudflare, thầy/cô chỉ cần tích chọn "Xác minh là người" trong cửa sổ đó.
3. Sau khi xác minh xong, bấm **OK** trên hộp thoại của chương trình Java để bắt đầu cào dữ liệu ngầm.

### ⏳ Giới hạn an toàn (Anti-Ban)
Để tránh bị Codeforces chặn IP, hệ thống được cấu hình:
- Chỉ lấy tối đa **10 bài nộp mới nhất** mỗi lần.
- Nghỉ **2 giây** giữa mỗi lần lấy mã nguồn.
- Nghỉ **2.2 giây** giữa các lần gọi API.

---

## 6. Các tính năng chính cần kiểm tra
Thầy/cô có thể trải nghiệm các tính năng theo thứ tự sau:

1.  **Đánh giá năng lực**: Chọn tài khoản `Redial` ở bảng trên -> Bấm **"Đánh giá năng lực"** để xem báo cáo tổng quan.
2.  **Xem phân tích chi tiết**: Tại bảng dưới, bấm **"Xem phân tích"** của một bài nộp bất kỳ để xem Gemini đánh giá mã nguồn.
3.  **Quản lý dữ liệu**:
    *   **Xóa**: Có thể xóa từng bài nộp hoặc xóa hẳn một tài khoản (Handle) kèm dữ liệu liên quan bằng nút **Xóa** ở cuối mỗi dòng.
    *   **Crawl mới**: Nhập một Handle Codeforces bất kỳ (VD: `vjudge1`) -> Bấm **"Lưu nick"** -> Bấm **"Crawl Codeforces"**.

---
*Sinh viên thực hiện: [Tên của bạn]*
