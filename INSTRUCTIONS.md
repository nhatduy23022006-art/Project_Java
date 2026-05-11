# HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG HỆ THỐNG PHÂN TÍCH MÃ NGUỒN

**Repository Github:** [https://github.com/nhatduy23022006-art/Project_Java](https://github.com/nhatduy23022006-art/Project_Java)

Tài liệu này hướng dẫn chi tiết các bước để thiết lập và kiểm tra đồ án **"Hệ thống tự động Crawl và Phân tích mã nguồn Codeforces bằng AI"**.

---

## 1. Yêu cầu hệ thống
Trước khi bắt đầu, hãy đảm bảo máy tính đã cài đặt:
- **Java JDK 11** trở lên.
- **Apache Maven** (để quản lý thư viện và chạy dự án).
- **Trình duyệt**: Microsoft Edge.

---

## 2. Cài đặt EdgeDriver (BẮT BUỘC)
Để hệ thống có thể điều khiển trình duyệt Edge và vượt qua Cloudflare tự động, thầy/cô cần thiết lập EdgeDriver cho đúng với phiên bản trình duyệt trên máy của mình.

**Các bước thực hiện:**
1. Mở trình duyệt Edge trên máy tính.
2. Bấm vào menu (dấu 3 chấm ở góc phải trên cùng) -> **Trợ giúp và phản hồi (Help and feedback)** -> **Giới thiệu về Microsoft Edge (About Microsoft Edge)**.
3. Ghi lại số phiên bản hiện tại của trình duyệt (Ví dụ: `122.0.2365.92` hoặc `131.0...`).
4. Truy cập trang chủ tải driver của Microsoft: [Microsoft Edge WebDriver](https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver/)
5. Kéo xuống phần **Downloads**, tìm và tải bản **x64** (dành cho Windows) có số phiên bản **khớp chính xác** với phiên bản Edge vừa xem.
6. Giải nén file `.zip` vừa tải về.
7. Copy file `msedgedriver.exe` ở bên trong và chép đè vào thư mục `drivers` trong thư mục gốc của dự án này (nếu đã có file lỗi thì chép đè hoặc xóa file cũ đi).

*(Lưu ý: Nếu không có file `msedgedriver.exe` hoặc phiên bản không khớp, chức năng Crawl Codeforces sẽ báo lỗi "CreateProcess error" hoặc "Session not created".)*

---

## 3. Cấu hình Gemini API Key (Bắt buộc)
Để tính năng phân tích AI hoạt động, thầy/cô cần cung cấp API Key của Google Gemini.

1. Mở **PowerShell** và di chuyển vào thư mục dự án.
2. Chạy lệnh sau (thay `KEY_CỦA_BẠN` bằng API Key thực tế):

```powershell
$env:GEMINI_API_KEY = "KEY_CỦA_BẠN"
```

*Lưu ý: Nếu không thiết lập Key, chương trình vẫn chạy được các tính năng Crawl và xem dữ liệu cũ, nhưng tính năng "Phân tích AI" sẽ báo lỗi.*

---

## 4. Cách khởi chạy chương trình
Dự án được thiết kế để chạy ngay lập tức mà không cần cài đặt Database phức tạp. Thầy/cô chỉ cần chạy lệnh duy nhất sau trong thư mục dự án:

```powershell
mvn clean compile exec:java
```

---

## 5. Lưu ý về Cơ sở dữ liệu (SQLite)
Dự án sử dụng **SQLite**, một hệ quản trị CSDL dạng file nhẹ.
- **Không cần cài MySQL hay SQL Server**: Toàn bộ dữ liệu được lưu tự động trong file `code_analyzer.db`.
- **Dữ liệu Demo**: Em đã tích hợp sẵn một số tài khoản (ví dụ: `Redial`, `tourist`) và các bài nộp đã được phân tích AI để thầy/cô có thể kiểm tra ngay lập tức mà không cần crawl mới.

---

## 6. Cơ chế Crawl dữ liệu & Vượt Cloudflare
Hệ thống crawl của dự án đã được tối ưu hóa để tránh bị khóa IP và có thể vượt rào cản Cloudflare của Codeforces:

### 🛡️ Vượt rào cản Cloudflare
1. Khi chạy Crawl, một cửa sổ trình duyệt Edge thật sẽ tự động mở lên.
2. Nếu gặp thông báo Cloudflare, thầy/cô chỉ cần tích chọn "Xác minh là người" trong cửa sổ trình duyệt đó.
3. Sau khi xác minh thành công và thấy mã nguồn hiển thị, bấm **OK** trên hộp thoại của ứng dụng Java để chương trình bắt đầu quá trình lấy dữ liệu.

### ⏳ Giới hạn an toàn (Anti-Ban)
- Chỉ lấy tối đa **10 bài nộp mới nhất** mỗi lần cập nhật.
- Tự động nghỉ **2 giây** giữa mỗi lần lấy mã nguồn bài tập.
- Tự động nghỉ **2.2 giây** giữa các lần gọi API hệ thống.

---

## 7. Các tính năng chính cần kiểm tra
Thầy/cô có thể trải nghiệm các tính năng theo thứ tự sau:

1.  **Đánh giá năng lực**: Chọn tài khoản `Redial` ở bảng trên -> Bấm **"Đánh giá năng lực"** để xem báo cáo tổng quan.
2.  **Xem phân tích chi tiết**: Tại bảng dưới, bấm **"Xem phân tích"** của một bài nộp bất kỳ để xem Gemini phân tích ưu nhược điểm của mã nguồn.
3.  **Quản lý dữ liệu**:
    *   **Xóa**: Có thể xóa từng bài nộp hoặc xóa hẳn một tài khoản (Handle) kèm dữ liệu liên quan bằng nút **Xóa** ở cuối mỗi dòng.
    *   **Crawl mới**: Nhập một Handle Codeforces bất kỳ (VD: `vjudge1`) -> Bấm **"Lưu nick"** -> Bấm **"Crawl Codeforces"**.

---
*Sinh viên thực hiện: Duy,Tình*
