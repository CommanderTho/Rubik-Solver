package com.rubik.solver.feature.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rubik.solver.core.cv.FaceScanResult
import com.rubik.solver.core.cv.RubikFaceDetector
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.RubikCubeState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannerUiState(
    val currentFace: CubeFace = CubeFace.FRONT,
    val capturedFaces: Map<CubeFace, List<CubeColor>> = emptyMap(),
    val lowConfidenceByFace: Map<CubeFace, Set<Int>> = emptyMap(),
    val livePreviewColors: List<CubeColor>? = null,
    val isLowLight: Boolean = false,
    val hasGlareRisk: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isCapturing: Boolean = false,
    val manualFaceLock: Boolean = false,
    val rotationInstructionVi: String = "Đưa mặt Rubik vào khung — Nhấn nút chụp để lưu màu",
    val justCapturedFace: CubeFace? = null,
    val errorMessage: String? = null
) {
    val scannedCount: Int get() = capturedFaces.size
    val allFacesScanned: Boolean get() = scannedCount == CubeFace.entries.size

    fun toRubikCubeState(): RubikCubeState {
        val map = CubeFace.entries.associateWith { face ->
            capturedFaces[face] ?: List(9) { null }
        }
        return RubikCubeState(map)
    }
}

class ScannerViewModel(
    private val detector: RubikFaceDetector = RubikFaceDetector(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var pendingAutoFace: CubeFace? = null
    private var pendingAutoFaceStreak: Int = 0

    fun selectFace(face: CubeFace) {
        pendingAutoFace = null
        pendingAutoFaceStreak = 0
        _uiState.update {
            it.copy(
                currentFace = face,
                manualFaceLock = true,
                rotationInstructionVi = "Đang chọn thủ công: ${face.displayNameVi} — Nhấn nút chụp để xác nhận",
                errorMessage = null
            )
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchEnabled = !it.isTorchEnabled) }
    }

    fun onLiveFrameAnalyzed(result: FaceScanResult) {
        val state = _uiState.value

        var updatedFace = state.currentFace
        var updatedInstruction = state.rotationInstructionVi

        val centerColor = result.colors.getOrNull(RubikCubeState.CENTER_INDEX)
        val detectedFace = centerColor?.let(::faceForCenterColor)

        // Orient 3D guide cube and current target face by detected center color if not manually locked
        if (!state.manualFaceLock && !state.allFacesScanned && detectedFace != null && detectedFace !in state.capturedFaces) {
            if (detectedFace == pendingAutoFace) {
                pendingAutoFaceStreak++
            } else {
                pendingAutoFace = detectedFace
                pendingAutoFaceStreak = 1
            }

            if (pendingAutoFaceStreak >= FACE_SWITCH_STREAK && detectedFace != state.currentFace) {
                updatedFace = detectedFace
                updatedInstruction = "Đã nhận diện tâm ${detectedFace.displayNameVi} — Nhấn nút chụp để xác nhận"
            }
        } else {
            pendingAutoFace = null
            pendingAutoFaceStreak = 0
        }

        _uiState.update {
            it.copy(
                currentFace = updatedFace,
                livePreviewColors = result.colors,
                isLowLight = result.isLowLight,
                hasGlareRisk = result.hasGlareRisk,
                rotationInstructionVi = updatedInstruction
            )
        }
    }

    fun captureFromBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int,
        overlayFraction: Float = 0.68f
    ) {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = withContext(computeDispatcher) {
                    detector.detectFromBitmap(
                        bitmap = bitmap,
                        rotationDegrees = rotationDegrees,
                        overlaySizeFraction = overlayFraction
                    )
                }
                applyCaptureResult(result)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = "Không thể nhận diện ảnh: ${t.message ?: "Lỗi xử lý"}"
                    )
                }
            }
        }
    }

    internal fun applyCaptureResult(result: FaceScanResult) {
        pendingAutoFace = null
        pendingAutoFaceStreak = 0

        _uiState.update { state ->
            val targetFace = resolveTargetFaceForCapture(state, result)
            val updatedFaces = state.capturedFaces + (targetFace to result.colors)
            val updatedLowConf = state.lowConfidenceByFace + (targetFace to result.lowConfidenceIndices)

            val nextUnscanned = findNextGuidedFace(
                justScanned = targetFace,
                capturedFaces = updatedFaces
            )

            val nextInstruction = if (nextUnscanned == null) {
                "Đã quét đủ 6/6 mặt! Chạm \"Sửa (6/6)\" để kiểm tra & giải."
            } else {
                rotationPrompt(from = targetFace, to = nextUnscanned)
            }

            state.copy(
                capturedFaces = updatedFaces,
                lowConfidenceByFace = updatedLowConf,
                currentFace = nextUnscanned ?: targetFace,
                manualFaceLock = false,
                justCapturedFace = targetFace,
                rotationInstructionVi = nextInstruction,
                isLowLight = result.isLowLight,
                hasGlareRisk = result.hasGlareRisk,
                isCapturing = false,
                errorMessage = null
            )
        }
    }

    fun clearFace(face: CubeFace) {
        pendingAutoFace = null
        pendingAutoFaceStreak = 0
        _uiState.update { state ->
            state.copy(
                capturedFaces = state.capturedFaces - face,
                lowConfidenceByFace = state.lowConfidenceByFace - face,
                currentFace = face,
                manualFaceLock = true,
                rotationInstructionVi = "Chụp lại ${face.displayNameVi} — Nhấn nút chụp"
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun resolveTargetFaceForCapture(state: ScannerUiState, result: FaceScanResult): CubeFace {
        if (state.manualFaceLock) return state.currentFace
        val detected = result.colors.getOrNull(RubikCubeState.CENTER_INDEX)?.let(::faceForCenterColor)
        return if (detected != null && detected !in state.capturedFaces) {
            detected
        } else {
            state.currentFace
        }
    }

    companion object {
        private const val FACE_SWITCH_STREAK = 2

        private val GUIDED_RING_ORDER = listOf(
            CubeFace.FRONT,
            CubeFace.RIGHT,
            CubeFace.BACK,
            CubeFace.LEFT,
            CubeFace.UP,
            CubeFace.DOWN
        )

        fun faceForCenterColor(color: CubeColor): CubeFace =
            CubeFace.entries.first { it.defaultColor == color }

        fun findNextGuidedFace(
            justScanned: CubeFace,
            capturedFaces: Map<CubeFace, List<CubeColor>>
        ): CubeFace? {
            val startIdx = GUIDED_RING_ORDER.indexOf(justScanned).coerceAtLeast(0)
            for (offset in 1..GUIDED_RING_ORDER.size) {
                val candidate = GUIDED_RING_ORDER[(startIdx + offset) % GUIDED_RING_ORDER.size]
                if (candidate !in capturedFaces) return candidate
            }
            return null
        }

        fun rotationPrompt(from: CubeFace, to: CubeFace): String = when (from to to) {
            CubeFace.FRONT to CubeFace.RIGHT,
            CubeFace.RIGHT to CubeFace.BACK,
            CubeFace.BACK to CubeFace.LEFT,
            CubeFace.LEFT to CubeFace.FRONT -> "Xoay khối sang TRÁI 90° theo hình 3D -> ${to.displayNameVi}"

            CubeFace.RIGHT to CubeFace.FRONT,
            CubeFace.BACK to CubeFace.RIGHT,
            CubeFace.LEFT to CubeFace.BACK,
            CubeFace.FRONT to CubeFace.LEFT -> "Xoay khối sang PHẢI 90° theo hình 3D -> ${to.displayNameVi}"

            CubeFace.LEFT to CubeFace.UP,
            CubeFace.FRONT to CubeFace.UP,
            CubeFace.RIGHT to CubeFace.UP,
            CubeFace.BACK to CubeFace.UP -> "Nghiêng mặt TRÊN về phía trước 90° -> ${to.displayNameVi}"

            CubeFace.UP to CubeFace.DOWN -> "Lật ngược khối 180° -> ${to.displayNameVi}"
            CubeFace.DOWN to CubeFace.UP -> "Lật ngược khối 180° -> ${to.displayNameVi}"

            else -> "Xoay khối theo mô hình 3D để chụp ${to.displayNameVi}"
        }
    }
}
