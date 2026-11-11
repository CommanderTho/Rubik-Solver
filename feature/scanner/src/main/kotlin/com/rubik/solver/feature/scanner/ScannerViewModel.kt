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
    val currentFace: CubeFace = CubeFace.SCAN_ORDER.first(),
    val capturedFaces: Map<CubeFace, List<CubeColor>> = emptyMap(),
    val lowConfidenceByFace: Map<CubeFace, Set<Int>> = emptyMap(),
    val livePreviewColors: List<CubeColor>? = null,
    val isLowLight: Boolean = false,
    val hasGlareRisk: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isCapturing: Boolean = false,
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
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun selectFace(face: CubeFace) {
        _uiState.update { it.copy(currentFace = face, errorMessage = null) }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchEnabled = !it.isTorchEnabled) }
    }

    fun onLiveFrameAnalyzed(result: FaceScanResult) {
        _uiState.update { state ->
            state.copy(
                livePreviewColors = result.colors,
                isLowLight = result.isLowLight,
                hasGlareRisk = result.hasGlareRisk
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
        _uiState.update { state ->
            val targetFace = state.currentFace
            val updatedFaces = state.capturedFaces + (targetFace to result.colors)
            val updatedLowConf = state.lowConfidenceByFace + (targetFace to result.lowConfidenceIndices)

            val nextUnscanned = CubeFace.SCAN_ORDER.firstOrNull { it !in updatedFaces }
                ?: targetFace

            state.copy(
                capturedFaces = updatedFaces,
                lowConfidenceByFace = updatedLowConf,
                currentFace = nextUnscanned,
                isLowLight = result.isLowLight,
                hasGlareRisk = result.hasGlareRisk,
                isCapturing = false,
                errorMessage = null
            )
        }
    }

    fun clearFace(face: CubeFace) {
        _uiState.update { state ->
            state.copy(
                capturedFaces = state.capturedFaces - face,
                lowConfidenceByFace = state.lowConfidenceByFace - face,
                currentFace = face
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
