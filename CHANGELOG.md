# Changelog

## Changes Made
- Merged `domain` và `data` vào module `app`.
- Xóa các module cũ và build artifacts.
- Cập nhật `settings.gradle.kts` và `app/build.gradle.kts`.
- Thay thế `LatLng` tùy biến bằng `com.google.android.gms.maps.model.LatLng`.
- Cập nhật các lớp `MapViewModel`, `MapsActivity` và repository tương ứng.
- Thêm `MapSettingsDialog` cho phép chọn kiểu bản đồ và bật/tắt giao thông.
- Cập nhật `MapController` và test kèm theo.
- Bổ sung biểu tượng `ic_layers` và layout `dialog_map_settings`.
- Thêm chế độ bản đồ Dark Mode, lưu vào SharedPreferences.
- Điều chỉnh layout để FAB không chồng lên panel chỉ đường.
- Thêm test Espresso kiểm tra mở BottomSheet khi chọn địa điểm.
- Thêm unit test cho `GetDirectionsUseCase`.
- Sửa TODO trong `data_extraction_rules.xml` thành FIXME.
- Thêm file `map_style_dark.json` và placeholder ảnh trong `docs/ui_fix`.

## Run Instructions
```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
```

## Remaining Work
- Hoàn thiện các tính năng còn thiếu ở Phần 1 và Phần 2.
- Bổ sung xử lý ngoại tuyến cho chỉ đường và lớp dữ liệu thời tiết.
- Di chuyển API key vào BuildConfig thông qua backend NodeJS (TODO).
