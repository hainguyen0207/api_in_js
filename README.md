# Burp Extension: API JavaScript Extractor

![image](https://github.com/user-attachments/assets/6661320d-da41-4ebe-92d9-f07d6169a7e2)





## Mục tiêu
Phát hiện và theo dõi các endpoint API được nhúng trong JavaScript hoặc HTML của response. Extension sẽ so sánh các endpoint này với các request đã đi qua Burp (Proxy, Repeater, Scanner...) để xác định API nào đã được gọi.

---

## Yêu cầu

- Người dùng **phải thêm phạm vi (Scope) vào Burp**
- Extension **chỉ hoạt động** và trích xuất API nếu URL nằm trong scope được cấu hình trong Burp.

---

## Tính năng chính

- Tự động trích xuất endpoint từ response JavaScript/HTML  
  → Các URL API được phát hiện từ mã nguồn sẽ được hiển thị trong bảng theo dõi.

- So sánh API đã trích với SiteMap  
  → Đánh dấu "✓" nếu API đã được gọi (xuất hiện trong SiteMap), hoặc "x" nếu chưa được truy cập.

- Tránh trùng lặp  
  → Một API chỉ được hiển thị một lần dù xuất hiện nhiều lần trong các response khác nhau.

- Lưu và tải dữ liệu tự động  
  → Dữ liệu API được lưu vào file `.csv` trong `AppData\Local\ApiJS\api.csv` khi tắt Burp và được load lại khi mở lại.

- Tùy chọn vị trí lưu CSV  
  → Cho phép người dùng chọn nơi lưu file bằng nút "Chọn nơi lưu".

- Nút "Reset Status"  
  → Thủ công kiểm tra lại các API có trạng thái "x" xem đã xuất hiện trong SiteMap chưa → tự động chuyển sang "✓" nếu có.
  **Ae làm dự án gần xong thì bấm nút reset. Xem còn những api js nào có trạng thái "x" chưa check thì trace lại để kiểm tra nhé.**

- Nút "Clear"  
  → Xóa toàn bộ dữ liệu bảng hiện tại và file lưu trữ.

---


---

## Cấu trúc lưu trữ

- **Mặc định lưu tại:**  
  `C:\Users\<user>\AppData\Local\ApiJS\api.csv`

- **Cấu hình vị trí lưu được chọn:**  
  `C:\Users\<user>\AppData\Local\ApiJS\config.txt`
