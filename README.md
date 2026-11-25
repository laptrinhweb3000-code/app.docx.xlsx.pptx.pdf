# DocCast Presenter (Android APK)

Ứng dụng Android gọn nhẹ tối ưu cho việc **đọc nhanh** và **trình chiếu hai chế độ** với 7 định dạng phổ biến: `PDF`, `DOCX`, `DOC`, `PPTX`, `PPT`, `XLSX`, `XLS`.

## Tính năng chính

* **Đọc nhanh & gọn nhẹ:**
  * **PDF:** Dựng trang sắc nét bằng `PdfRenderer` gốc của Android kèm bộ nhớ đệm chuyển trang tức thì.
  * **DOCX / PPTX / XLSX:** Phân tích trực tiếp bằng bộ đọc OOXML thuần Kotlin siêu nhẹ (tách từng slide cho PowerPoint, tách từng sheet cho Excel, giữ ảnh nhúng và bảng biểu).
  * **DOC / PPT / XLS:** Hỗ trợ định dạng Office cũ qua thư viện Apache POI 4.1.2 gọn nhẹ.
  * Hỗ trợ mở trực tiếp tệp từ trình quản lý tệp, Zalo, Google Drive và lưu lịch sử mở gần đây.
* **Trình chiếu 2 chế độ song song:**
  * **Màn hình ngoài (HDMI / USB-C / Miracast / Smart View / Chromecast):** Tự động tách màn hình chiếu sạch toàn màn hình, trong khi điện thoại đóng vai trò bảng điều khiển.
  * **Trình chiếu qua Wi-Fi & Mã QR:** Tạo máy chủ web nội bộ ngay trên điện thoại; máy tính gắn máy chiếu hoặc Smart TV chỉ cần quét mã QR hoặc mở địa chỉ `http://<IP>:8080` trên trình duyệt là đồng bộ trang chiếu, con trỏ laser, nét vẽ và chế độ màn hình đen theo thời gian thực.
* **Công cụ giảng dạy / thuyết trình:** Con trỏ **Laser đỏ**, **Bút vẽ ghi chú**, nút **Xóa nét**, **Màn hình đen tạm thời** và **đồng hồ đếm giờ**.

## Cách xuất file APK tự động trên GitHub Actions (Không cần cài Android Studio)

1. Giải nén file `DocCastPresenter_Android.zip` trên máy tính.
2. Tạo một kho lưu trữ (Repository) mới trên [GitHub](https://github.com/new) (có thể để Private hoặc Public).
3. Tải toàn bộ thư mục và tệp bên trong `DocCastPresenter/` lên nhánh `main` (đảm bảo giữ nguyên thư mục `.github/workflows/build-apk.yml`).
   * *Gợi ý nhanh bằng Git:*
     ```bash
     cd DocCastPresenter
     git init
     git add .
     git commit -m "Initial commit DocCast Presenter"
     git branch -M main
     git remote add origin https://github.com/<ten-tai-khoan>/<ten-repo>.git
     git push -u origin main
     ```
4. Mở tab **Actions** trên GitHub → chọn workflow **Build Android APK** (nếu chưa tự chạy, nhấn **Run workflow**).
5. Chờ khoảng 2–4 phút để build hoàn tất → kéo xuống mục **Artifacts** → tải về **DocCastPresenter-APK** (bên trong là file `DocCastPresenter-v1.0.apk`).
6. Chép file `DocCastPresenter-v1.0.apk` sang điện thoại Android và cài đặt (bật cho phép *Cài đặt ứng dụng không rõ nguồn gốc* nếu máy hỏi).

## Hướng dẫn sử dụng trình chiếu

* **Chiếu bằng cáp HDMI / USB-C hoặc Miracast / Smart View:** Cắm cáp hoặc nhấn nút **TV/HDMI** trong app để kết nối không dây. Ứng dụng sẽ tự phát hiện màn hình thứ 2 và chiếu toàn màn hình sạch.
* **Chiếu không cần dây qua Wi-Fi / Trình duyệt:** Kết nối điện thoại và máy tính/Smart TV cùng một mạng Wi-Fi (hoặc phát Wi-Fi từ điện thoại). Nhấn nút **Wi-Fi QR** ở góc trên màn hình đọc tài liệu, sau đó quét mã QR hoặc nhập địa chỉ `http://...` lên trình duyệt của máy chiếu/TV và bấm **Toàn màn hình**.
