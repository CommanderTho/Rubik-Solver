# Rubik Solver 3D

Ứng dụng Android viết bằng **Kotlin** và **Jetpack Compose** giúp quét 6 mặt khối Rubik 3×3 qua Camera, nhận diện màu bằng **OpenCV**, hỗ trợ chỉnh sửa/xác thực trạng thái thủ công theo thời gian thực, tính toán lời giải tối ưu bằng thuật toán **Kociemba (Two-Phase Algorithm)** và mô phỏng từng bước xoay trực quan trên mô hình **3D SceneView (Filament)**.

---

## Tính năng chính

### 1. Quét 6 mặt bằng CameraX & OpenCV
- Giao diện camera trực tiếp với **khung định vị lưới 3×3** hướng dẫn căn chỉnh lần lượt 6 mặt (`Up`, `Down`, `Front`, `Back`, `Left`, `Right`).
- Hiển thị màu dự đoán trực tiếp ngay trên 9 ô lưới trước khi chụp, hỗ trợ bật/tắt **đèn Flash** và chụp lại từng mặt.
- **Xử lý Computer Vision (OpenCV):**
  - Cắt 9 vùng quan tâm (ROI phần lõi mỗi ô để tránh viền nhựa đen), chuyển đổi không gian màu sang **HSV**.
  - Tự động áp dụng **CLAHE** trên kênh độ sáng `V` khi môi trường ánh sáng yếu.
  - Lọc bỏ điểm lóa phản quang (*specular glare*), tính trung bình vòng tròn cho kênh Hue (xử lý màu Đỏ ở biên `0/180`), đồng thời gắn cờ cảnh báo các ô có độ tin cậy thấp.

### 2. Kiểm tra & Chỉnh sửa thủ công 2D (Manual Correction & Validation)
- **Sơ đồ trải phẳng 2D (Unfolded Net)** cho toàn bộ 54 ô cùng lưới **3×3 phóng to** để chạm đổi màu trực tiếp khi ánh sáng gây nhận diện nhầm.
- **Bảng màu mẫu 6 màu** kèm bộ đếm trực tiếp `x/9` cho từng màu và viền cảnh báo tại các ô OpenCV chưa chắc chắn.
- **Xác thực thời gian thực:** Kiểm tra đủ 54 ô, mỗi màu đúng 9 ô, 6 màu tâm phân biệt và kiểm tra tính giải được về mặt vật lý (corner twist, edge flip, permutation parity). Nút **Giải Rubik & Xem 3D** chỉ mở khóa khi trạng thái hợp lệ.
- Hỗ trợ nút **Tạo trạng thái mẫu (Shuffle)** để trải nghiệm nhanh trên máy ảo hoặc khi không cầm Rubik thật.

### 3. Thuật toán giải Kociemba Two-Phase
- Tích hợp `min2phase` (`cs.min2phase.Search` & `Tools.verify`) chạy nền trên `Dispatchers.Default`, đồng thời khởi tạo trước bảng pruning khi mở app để tránh giật UI.
- Ánh xạ màu động dựa trên 6 ô tâm nên người dùng có thể cầm khối Rubik theo bất kỳ hướng màu nào miễn 6 tâm hợp lệ.
- Trả về chuỗi bước giải theo chuẩn HTM (`U`, `R'`, `F2`,...) thường tối ưu $\le 20$ bước.

### 4. Mô phỏng 3D & Bộ điều khiển phát lại (3D Player)
- Render trực quan **26 cubie** (thân khối tối + sticker màu trên các mặt ngoài) bằng **SceneView / Google Filament**, hỗ trợ kéo xoay góc nhìn và chụm tay phóng to/thu nhỏ.
- Đồng bộ trạng thái logic `CubieCubeState` với hoạt ảnh xoay lát cắt 90°/180° theo từng khung hình VSync (`onFrame`) mượt mà.
- **Player Controller đầy đủ:** `Play/Pause`, `Next Step`, `Previous Step`, `Reset`, thanh tua **Timeline**, dải chip từng bước xoay và điều chỉnh tốc độ phát (`0.5x`, `1.0x`, `1.5x`, `2.0x`).

---

## Kiến trúc đa module

```text
rubik-antigravity/
├── app/                # Entry point, Application (init OpenCV & warm-up solver), Navigation & Theme
├── core/
│   ├── model/          # CubeColor, CubeFace, Move, RubikCubeState, CubeValidator, FaceletMapper, CubieCubeEngine (26 cubies)
│   ├── cv/             # OpenCvInitializer, RubikFaceDetector, HsvColorClassifier (xử lý ánh sáng yếu & phản quang)
│   └── solver/         # RubikSolver, KociembaRubikSolver (tích hợp min2phase & kiểm tra parity)
└── feature/
    ├── scanner/        # CameraX preview/capture/analysis + Overlay 3x3 + ScannerViewModel
    ├── editor/         # Sơ đồ 2D 54 ô + Zoomed 3x3 + Bảng màu + Validation UI + EditorViewModel
    └── solver3d/       # SceneView 3D Rubik renderer + Player Controller + Solver3DViewModel
```

---

## Công nghệ sử dụng

| Thành phần | Công nghệ / Phiên bản |
| --- | --- |
| Ngôn ngữ & Build | Kotlin `2.4.21`, Android Gradle Plugin `9.5.1`, Gradle `9.6.0` |
| Giao diện | Jetpack Compose (BOM `2026.09.00`), Material 3, Navigation Compose `2.10.2` |
| Camera | AndroidX CameraX (`1.6.2`) |
| Thị giác máy tính | OpenCV for Android (`org.opencv:opencv:4.14.0`) |
| Thuật toán giải | Kociemba Two-Phase (`org.worldcubeassociation.tnoodle:scrambler-min2phase:0.20.0`) |
| Đồ họa 3D | SceneView (`io.github.sceneview:sceneview:4.67.0`) / Google Filament |
| SDK Android | `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` |

---

## Hướng dẫn Build & Chạy ứng dụng

### Yêu cầu môi trường
- **Android Studio** (hỗ trợ AGP 9.5+ / JDK 17–25) và **Android SDK** (`compileSdk 37`).
- Thiết bị Android thật (Android 8.0 / API 26 trở lên) hoặc Android Emulator hỗ trợ OpenGL ES / Filament.

### 1. Clone dự án
```bash
git clone https://github.com/CommanderTho/Rubik-Solver.git
cd Rubik-Solver
```

### 2. Build file APK
Mở dự án bằng **Android Studio** để tự tạo `local.properties` (hoặc tạo file `local.properties` trỏ `sdk.dir=...` tới Android SDK trên máy bạn), sau đó chạy:

- **Trên Windows (PowerShell / CMD):**
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **Trên macOS / Linux:**
  ```bash
  ./gradlew assembleDebug
  ```

File APK đầu ra nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Cài đặt & chạy trực tiếp
```powershell
.\gradlew.bat installDebug
adb shell am start -n com.rubik.solver/.MainActivity
```

### 4. Chạy Unit Tests
```powershell
.\gradlew.bat :core:model:test :core:solver:test testDebugUnitTest
```
Bộ test bao gồm kiểm thử logic xác thực 54 ô & parity (`CubeValidatorTest`), phép xoay 26 cubie 3D (`CubieCubeEngineTest`), ánh xạ tâm mặt (`FaceletMapperTest`), bộ phân loại màu HSV dưới điều kiện phản quang/thiếu sáng (`HsvColorClassifierTest`), giải Rubik Kociemba (`KociembaRubikSolverTest`), `EditorViewModelTest` và `Solver3DViewModelTest`.

---

## Mẹo dùng thử nhanh trên máy ảo (không cần Rubik thật)
1. Ở màn hình Camera, nhấn **Sửa (0/6)** (hoặc **Chuyển sang Nhập màu Thủ công** nếu bỏ qua quyền Camera).
2. Nhấn biểu tượng **Shuffle** ở thanh tiêu đề góc trên bên phải để nạp sẵn một trạng thái Rubik đã xáo trộn hợp lệ.
3. Nhấn **Giải Rubik & Xem 3D** để xem lời giải và tương tác với mô hình 3D.

---

## Giấy phép

Phân phối theo giấy phép **MIT License** — xem chi tiết tại file [LICENSE](LICENSE).
