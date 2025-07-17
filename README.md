# Burp Extension: API JavaScript Extractor

## Yêu cầu

- Người dùng **phải thêm phạm vi (Scope) vào Burp**
- Extension **chỉ hoạt động** nếu URL nằm trong scope đã cấu hình.

## Tính năng

- Tự động trích xuất endpoint từ JavaScript/HTML response.
- So sánh API đã trích xuất với SiteMap.
- Tránh trùng lặp endpoint.
- Lưu và tải dữ liệu tự động (`AppData\Local\ApiJS\api.csv`)
- Load file có sẵn
- Nút “Reset Status” kiểm tra lại các API ❌.
- Nút “Clear” để xóa bảng và file.
- Tìm kiếm và lọc api
- Xuất những api có trạng thái là X
