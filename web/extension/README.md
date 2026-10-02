# SnowScreen Web Extension

Extension Chrome/Edge Manifest V3, chuyển ý tưởng từ app Android SnowScreen sang Canvas 2D trên trang web. Không cần npm hoặc bước build. Giao diện tiếng Anh, icon bông tuyết ban đầu và link “Support SnowScreen — Buy me a coffee” dưới tiêu đề. Nút Start snow dùng nền xanh sáng với hiệu ứng ánh sáng quét, tắt hiệu ứng khi hệ thống yêu cầu giảm chuyển động. Kích thước mặc định và preset Gentle snow là 0.4×; cài đặt đã lưu được giữ, chọn Reset to defaults để áp dụng mặc định mới.

## Cài đặt

1. Mở `chrome://extensions` (Chrome) hoặc `edge://extensions` (Edge).
2. Bật **Developer mode / Chế độ nhà phát triển**.
3. Chọn **Load unpacked / Tải tiện ích đã giải nén** và chọn thư mục `web/extension`.
4. Tải lại các tab web đã mở. Ghim SnowScreen trên thanh công cụ rồi mở popup để chỉnh hiệu ứng.

Tuyết bật mặc định. Bật/tắt và cài đặt áp dụng cho mọi tab HTTP/HTTPS, lưu trên máy bằng `chrome.storage.local`. Chọn Tuyết nhẹ, Bão tuyết, Huyền ảo hoặc tự chỉnh số lượng, tốc độ, kích thước, gió, độ rõ và tám kiểu bông tuyết.

## Thiết kế

Icon thanh công cụ lấy từ `app/src/main/ic_launcher-playstore.png` (bông tuyết trắng trên nền xanh dương, cùng thiết kế launcher Android), xuất thành PNG 16/32 px và khai báo trong `action.default_icon`. Popup vẫn dùng bông tuyết ban đầu.

- `settings.js`: mặc định và ba preset lấy từ `SnowSettings.kt` / `MainActivity.kt`, kiểm tra giới hạn giá trị.
- `snow.js`: chuyển thuật toán `SnowView.kt` sang Canvas; ba lớp chiều sâu, xoay, lắc, gió ngẫu nhiên và tuyết tan. Gió theo con trỏ thay cho cảm biến nghiêng Android.
- `content.js`: overlay Shadow DOM, không chặn click/cuộn/chọn văn bản. Tạm dừng khi tab bị ẩn hoặc hệ thống yêu cầu giảm chuyển động. Giới hạn pixel ratio ở 2 và delta thời gian để tránh tải dư thừa.
- `popup.*`: bảng điều khiển tiếng Anh; cập nhật các tab qua sự kiện storage, không cần background service worker.

Chỉ dùng quyền `storage` và content scripts cho HTTP/HTTPS. Không gửi dữ liệu, không tải mã ngoài, không dùng thư viện bên thứ ba. Browser hiển thị quyền truy cập trang web vì hiệu ứng cần chèn Canvas vào các trang.

## Giới hạn và kiểm tra thủ công

Browser không cho extension chèn hiệu ứng vào `chrome://`, `edge://`, trang cửa hàng extension và một số trang nội bộ/PDF. File `file://` không thuộc phạm vi hỗ trợ. Các phần tử top layer như dialog modal và video fullscreen có thể xuất hiện phía trên Canvas.

Sau khi cài, kiểm tra trên hai tab web (một trang nền sáng, một nền tối): bật/tắt, đổi preset, kéo slider, chọn từng kiểu tuyết, đóng/mở popup để kiểm tra lưu cài đặt. Kiểm tra liên kết, cuộn và chọn văn bản vẫn hoạt động; chuyển tab rồi quay lại; thay kích thước cửa sổ; bật giảm chuyển động trong hệ thống để kiểm tra tạm dừng.

Kiểm tra tự động: `node web/extension/tests/verify.cjs` từ thư mục gốc repository.

Tài liệu API: [Content scripts](https://developer.chrome.com/docs/extensions/develop/concepts/content-scripts), [Storage](https://developer.chrome.com/docs/extensions/reference/api/storage).
