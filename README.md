# Rubik Solver 3D

An Android application built with **Kotlin** and **Jetpack Compose** that scans all 6 faces of a 3×3 Rubik's Cube via **CameraX**, detects sticker colors using **OpenCV**, supports real-time manual correction and validation, computes optimal solutions with **Kociemba's Two-Phase Algorithm**, and animates step-by-step moves on an interactive **3D SceneView (Filament)** cube.

---

## Features

### 1. 6-Face Camera Scanning (CameraX + OpenCV)
- Live camera preview with a **3×3 alignment overlay grid** guiding the user through all 6 faces (`Up`, `Down`, `Front`, `Back`, `Left`, `Right`).
- Real-time color preview dots inside each grid cell before snapping, plus **Flashlight (Torch)** toggle and per-face retake support.
- **Computer Vision Pipeline (OpenCV):**
  - Crops the 9 inner sticker ROIs (excluding black plastic borders) and converts RGB frames to **HSV**.
  - Automatically applies **CLAHE** contrast enhancement on the `V` channel under low-light conditions.
  - Filters out specular glare highlights, uses circular Hue statistics (to handle Red wrapping around `0/180`), adapts White vs. Yellow thresholds by luminance, and flags low-confidence cells for review.

### 2. 2D Manual Correction & Real-Time Validation
- **2D Unfolded Cube Net** displaying all 54 stickers alongside a **zoomed 3×3 interactive grid** to tap and fix misread colors.
- **6-Color Palette** with live `x/9` per-color counters and warning borders on low-confidence cells flagged by OpenCV.
- **Real-Time Validation:** Verifies all 54 cells are filled, each color appears exactly 9 times, all 6 center stickers are distinct, and the cube satisfies physical solvability constraints (corner twist, edge flip, permutation parity). The **Solve** button stays locked until the state is valid.
- Includes a **Sample Scramble (Shuffle)** action for quick testing on emulators without a physical cube.

### 3. Kociemba Two-Phase Solver
- Powered by `min2phase` (`cs.min2phase.Search` & `Tools.verify`) running asynchronously on `Dispatchers.Default`, with pruning tables pre-warmed on app startup to avoid UI stutter.
- Dynamically maps colors using the 6 center stickers so users can hold the cube in any orientation as long as the 6 centers are unique.
- Generates Half-Turn Metric (HTM) move sequences (`U`, `R'`, `F2`, etc.), typically $\le 20$ moves.

### 4. Interactive 3D Visualization & Playback Controller
- Renders **26 individual cubies** (dark plastic cores with colored outer face stickers) using **SceneView / Google Filament**, supporting touch orbit and pinch-to-zoom gestures.
- Synchronizes the discrete `CubieCubeState` with smooth 90°/180° slice rotation animations inside SceneView's VSync `onFrame` callback.
- **Full Player Controller:** `Play/Pause`, `Next Step`, `Previous Step`, `Reset`, interactive **Timeline Slider**, scrollable step chips, and playback speed selector (`0.5x`, `1.0x`, `1.5x`, `2.0x`).

---

## Multi-Module Architecture

```text
rubik-antigravity/
├── app/                # Application entry point (OpenCV init & solver warm-up), Navigation & Material 3 Theme
├── core/
│   ├── model/          # CubeColor, CubeFace, Move, RubikCubeState, CubeValidator, FaceletMapper, CubieCubeEngine (26 cubies)
│   ├── cv/             # OpenCvInitializer, RubikFaceDetector, HsvColorClassifier (glare & low-light handling)
│   └── solver/         # RubikSolver, KociembaRubikSolver (min2phase integration & parity verification)
└── feature/
    ├── scanner/        # CameraX preview/capture/analysis + 3x3 overlay + ScannerViewModel
    ├── editor/         # 2D unfolded net + zoomed 3x3 editor + color palette + real-time validation + EditorViewModel
    └── solver3d/       # SceneView 3D cube renderer + playback controller + Solver3DViewModel
```

---

## Tech Stack

| Layer | Library / Version |
| --- | --- |
| Language & Build | Kotlin `2.4.21`, Android Gradle Plugin `9.5.1`, Gradle `9.6.0` |
| UI & Navigation | Jetpack Compose (BOM `2026.09.00`), Material 3, Navigation Compose `2.10.2` |
| Camera | AndroidX CameraX (`1.6.2`) |
| Computer Vision | OpenCV for Android (`org.opencv:opencv:4.14.0`) |
| Cube Solver | Kociemba Two-Phase (`org.worldcubeassociation.tnoodle:scrambler-min2phase:0.20.0`) |
| 3D Rendering | SceneView (`io.github.sceneview:sceneview:4.67.0`) / Google Filament |
| Android SDK | `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` |

---

## Getting Started

### Prerequisites
- **Android Studio** (supporting AGP 9.5+ / JDK 17–25) and **Android SDK** (`compileSdk 37`).
- A physical Android device (Android 8.0 / API 26+) or an Android Emulator with OpenGL ES / Filament support.

### 1. Clone the Repository
```bash
git clone https://github.com/CommanderTho/Rubik-Solver.git
cd Rubik-Solver
```

### 2. Build the Debug APK
Open the project in **Android Studio** to generate `local.properties` automatically (or create `local.properties` pointing `sdk.dir=...` to your local Android SDK), then run:

- **Windows (PowerShell / CMD):**
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **macOS / Linux:**
  ```bash
  ./gradlew assembleDebug
  ```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install & Launch
```powershell
.\gradlew.bat installDebug
adb shell am start -n com.rubik.solver/.MainActivity
```

### 4. Run Unit Tests
```powershell
.\gradlew.bat :core:model:test :core:solver:test testDebugUnitTest
```
The test suite covers 54-sticker & parity validation (`CubeValidatorTest`), 3D 26-cubie rotation invariants (`CubieCubeEngineTest`), dynamic center facelet mapping (`FaceletMapperTest`), HSV classification under glare & low light (`HsvColorClassifierTest`), Kociemba end-to-end solving (`KociembaRubikSolverTest`), `EditorViewModelTest`, and `Solver3DViewModelTest`.

---

## Quick Demo on Emulator (Without a Physical Cube)
1. On the camera screen, tap **Sửa (0/6)** (or **Chuyển sang Nhập màu Thủ công** if camera permission is skipped) to open the 2D Editor.
2. Tap the **Shuffle** icon in the top-right app bar to load a valid scrambled state.
3. Tap **Giải Rubik & Xem 3D** to compute the solution and explore the 3D step-by-step animation.

---

## License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for details.
