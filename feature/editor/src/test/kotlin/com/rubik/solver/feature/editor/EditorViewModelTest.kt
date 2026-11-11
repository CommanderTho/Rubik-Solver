package com.rubik.solver.feature.editor

import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.RubikCubeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `solve button unlocks only when all 54 stickers and parity are valid`() = runTest {
        val vm = EditorViewModel()
        advanceUntilIdle()

        // Initially empty -> locked
        assertFalse(vm.uiState.value.canSolve)

        // Load solved state -> unlocked
        vm.resetSolved()
        assertTrue(vm.uiState.value.canSolve)

        // Corrupt 1 sticker (10 white, 8 yellow) -> locked immediately
        vm.selectPaletteColor(CubeColor.WHITE)
        vm.paintSticker(CubeFace.DOWN, 0)
        assertFalse(vm.uiState.value.canSolve)
        assertEquals(10, vm.uiState.value.validationResult.colorCounts[CubeColor.WHITE])
        assertEquals(8, vm.uiState.value.validationResult.colorCounts[CubeFace.DOWN.defaultColor])

        // Fix it back -> unlocked immediately
        vm.selectPaletteColor(CubeColor.YELLOW)
        vm.paintSticker(CubeFace.DOWN, 0)
        assertTrue(vm.uiState.value.canSolve)
    }
}
