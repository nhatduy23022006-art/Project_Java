# Code Analyzer

Ứng dụng Java Swing để dán code, đưa vào hàng đợi và phân tích tự động bằng Gemini. Chương trình hiển thị danh sách các đoạn code đã thêm, trạng thái xử lý, điểm đánh giá cấu trúc dữ liệu, thuật toán, mức độ nghi ngờ dùng AI và cho phép xem lại nội dung từng submission.

## Tính năng

- Dán code trực tiếp vào ô nhập liệu và bấm `Add` để đưa vào hàng đợi.
- Bấm `Check Pending` để phân tích toàn bộ item đang ở trạng thái chờ.
- Xem chi tiết từng item bằng nút `View` trong bảng.
- Hiển thị kết quả gồm `DS`, `Algo`, `AI`, `Used AI` và `Confidence`.
- Nếu không có API key, ứng dụng vẫn chạy bằng heuristic nội bộ, nhưng sẽ không gọi Gemini.

## Yêu cầu

- Java 11 trở lên
- Maven
- Biến môi trường `GEMINI_API_KEY` nếu muốn dùng Gemini thật
- Tuỳ chọn: `GEMINI_MODEL` để đổi model, mặc định là `gemini-2.5-flash`

## Chạy ứng dụng

### 1. Build

```bash
mvn clean compile
```

### 2. Chạy trên Windows PowerShell

```powershell
$env:GEMINI_API_KEY="your_gemini_api_key"
$env:GEMINI_MODEL="gemini-2.5-flash"
mvn exec:java
```

### 3. Chạy trên Command Prompt

```bat
set GEMINI_API_KEY=your_gemini_api_key
set GEMINI_MODEL=gemini-2.5-flash
mvn exec:java
```

Nếu không muốn gọi Gemini, chỉ cần bỏ qua biến `GEMINI_API_KEY`. Ứng dụng sẽ chạy ở chế độ heuristic, phù hợp để test giao diện và luồng xử lý cơ bản.

## Cách dùng

1. Mở ứng dụng sau khi chạy lệnh `mvn exec:java`.
2. Dán đoạn code cần phân tích vào ô bên trái.
3. Bấm `Add` để thêm vào hàng đợi.
4. Bấm `Check Pending` để phân tích các item chưa xử lý.
5. Bấm `View` trong bảng để xem lại toàn bộ code và kết quả chi tiết.

## Ghi chú kỹ thuật

- Entry point của ứng dụng là `com.example.codeanalyzer.Main`.
- Logic gọi Gemini nằm trong `AnalyzerService`.
- Dữ liệu đầu ra mong đợi từ model là JSON với các khóa: `ds`, `algorithms`, `usedAI`, `confidence`, `dsScore`, `algoScore`, `aiScore`.
