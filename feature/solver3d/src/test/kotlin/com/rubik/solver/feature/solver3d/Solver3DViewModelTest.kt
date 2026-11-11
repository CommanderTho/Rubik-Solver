package com.rubik.solver.feature.solver3d

import com.rubik.solver.core.model.CubieCubeState
import com.rubik.solver.core.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Solver3DViewModelTest {

    @Test
    fun `playback advances steps smoothly and reaches solved state`() {
        val scramble = Move.parseSequence("R U R'")
        val initial = CubieCubeState.solved().applyMoves(scramble).toRubikCubeState()
        val solution = Move.parseSequence("R U' R'")

        val vm = Solver3DViewModel()
        vm.loadSolution(initial, solution, solveDurationMs = 12L)

        assertEquals(0, vm.uiState.value.currentStepIndex)
        assertFalse(vm.uiState.value.currentCubeState.isSolved())

        // Start playback
        vm.togglePlayPause()
        assertTrue(vm.uiState.value.isPlaying)

        // Advance through all 3 moves via simulated VSync frames
        repeat(60) {
            vm.advanceAnimation(0.03f)
        }

        assertEquals(3, vm.uiState.value.currentStepIndex)
        assertFalse(vm.uiState.value.isPlaying)
        assertNull(vm.uiState.value.activeAnimation)
        assertTrue(vm.uiState.value.currentCubeState.isSolved())
    }

    @Test
    fun `timeline seek, previous, next and reset stay synchronized with 3D cubie state`() {
        val scramble = Move.parseSequence("F R U'")
        val initial = CubieCubeState.solved().applyMoves(scramble).toRubikCubeState()
        val solution = Move.parseSequence("U R' F'")

        val vm = Solver3DViewModel()
        vm.loadSolution(initial, solution)

        // Seek directly to final step
        vm.seekToStep(3)
        assertEquals(3, vm.uiState.value.currentStepIndex)
        assertTrue(vm.uiState.value.currentCubeState.isSolved())

        // Step backward 1 move
        vm.previousStep()
        repeat(20) { vm.advanceAnimation(0.05f) }
        assertEquals(2, vm.uiState.value.currentStepIndex)
        assertFalse(vm.uiState.value.currentCubeState.isSolved())

        // Step forward 1 move back to solved
        vm.nextStep()
        repeat(20) { vm.advanceAnimation(0.05f) }
        assertEquals(3, vm.uiState.value.currentStepIndex)
        assertTrue(vm.uiState.value.currentCubeState.isSolved())

        // Reset back to step 0
        vm.reset()
        assertEquals(0, vm.uiState.value.currentStepIndex)
        assertFalse(vm.uiState.value.currentCubeState.isSolved())
    }
}
