package com.rubik.solver.feature.scanner

import com.rubik.solver.core.cv.DetectedSticker
import com.rubik.solver.core.cv.FaceScanResult
import com.rubik.solver.core.cv.HsvSample
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerViewModelTest {

    private fun fakeScan(centerColor: CubeColor, edgeColor: CubeColor = CubeColor.WHITE): FaceScanResult {
        val colors = List(9) { idx -> if (idx == 4) centerColor else edgeColor }
        val stickers = colors.mapIndexed { idx, color ->
            DetectedSticker(
                index = idx,
                color = color,
                confidence = 0.92f,
                isLowConfidence = false,
                averageHsv = HsvSample(0f, 0f, 180f)
            )
        }
        return FaceScanResult(
            stickers = stickers,
            colors = colors,
            faceMedianBrightness = 180f,
            isLowLight = false,
            hasGlareRisk = false
        )
    }

    @Test
    fun `initial state starts with zero captured faces`() {
        val vm = ScannerViewModel()
        assertEquals(0, vm.uiState.value.scannedCount)
        assertTrue(vm.uiState.value.capturedFaces.isEmpty())
    }

    @Test
    fun `auto detects arbitrary starting face by center color and auto captures when stable`() {
        var fakeTime = 2_000L
        val vm = ScannerViewModel(clockMs = { fakeTime })

        // User starts with RED (RIGHT face) instead of UP/FRONT
        val redFaceScan = fakeScan(centerColor = CubeColor.RED, edgeColor = CubeColor.BLUE)

        // Frame 1 & 2 switch currentFace automatically to RIGHT
        vm.onLiveFrameAnalyzed(redFaceScan)
        vm.onLiveFrameAnalyzed(redFaceScan)
        assertEquals(CubeFace.RIGHT, vm.uiState.value.currentFace)

        // Continue steady frames until auto-capture triggers
        vm.onLiveFrameAnalyzed(redFaceScan)
        vm.onLiveFrameAnalyzed(redFaceScan)
        vm.onLiveFrameAnalyzed(redFaceScan)

        assertEquals(1, vm.uiState.value.scannedCount)
        assertEquals(redFaceScan.colors, vm.uiState.value.capturedFaces[CubeFace.RIGHT])
        // After capturing RIGHT, 3D guide rotates to BACK next
        assertEquals(CubeFace.BACK, vm.uiState.value.currentFace)
        assertFalse(vm.uiState.value.allFacesScanned)
    }

    @Test
    fun `already captured face is not overwritten when seen again`() {
        var fakeTime = 1_000L
        val vm = ScannerViewModel(clockMs = { fakeTime })

        val redScan = fakeScan(centerColor = CubeColor.RED, edgeColor = CubeColor.YELLOW)
        vm.selectFace(CubeFace.RIGHT)
        vm.applyCaptureResult(redScan)
        assertEquals(CubeFace.BACK, vm.uiState.value.currentFace)

        // Advance past cooldown and show RED again -> should stay on BACK, not overwrite RIGHT
        fakeTime += 2_000L
        val differentRedScan = fakeScan(centerColor = CubeColor.RED, edgeColor = CubeColor.GREEN)
        repeat(5) { vm.onLiveFrameAnalyzed(differentRedScan) }

        assertEquals(CubeFace.BACK, vm.uiState.value.currentFace)
        assertEquals(redScan.colors, vm.uiState.value.capturedFaces[CubeFace.RIGHT])
    }
}
