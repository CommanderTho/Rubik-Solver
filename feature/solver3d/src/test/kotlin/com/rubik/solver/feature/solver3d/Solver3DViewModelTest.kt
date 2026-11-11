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
        assertNull(vm.uiState.value.highlightedStepNumber)
        assertFalse(vm.uiState.value.currentCubeState.isSolved())
    }

    @Test
    fun `only one step is highlighted during forward and backward animations`() {
        val scramble = Move.parseSequence("R U F")
        val initial = CubieCubeState.solved().applyMoves(scramble).toRubikCubeState()
        val solution = Move.parseSequence("F' U' R'")

        val vm = Solver3DViewModel()
        vm.loadSolution(initial, solution)
        assertNull(vm.uiState.value.highlightedStepNumber)

        // Step 1 animating (0 -> 1): highlightedStepNumber must be 1
        vm.nextStep()
        vm.advanceAnimation(0.1f)
        assertEquals(1, vm.uiState.value.highlightedStepNumber)
        assertEquals(solution[0], vm.uiState.value.currentMove)

        // Complete step 1, then start step 2 (1 -> 2): highlightedStepNumber must be ONLY 2
        vm.advanceAnimation(1.0f)
        assertEquals(1, vm.uiState.value.currentStepIndex)
        assertEquals(1, vm.uiState.value.highlightedStepNumber)

        vm.nextStep()
        vm.advanceAnimation(0.1f)
        assertEquals(1, vm.uiState.value.currentStepIndex)
        assertEquals(2, vm.uiState.value.activeAnimation?.targetStepIndex)
        assertEquals(2, vm.uiState.value.highlightedStepNumber)
        assertEquals(solution[1], vm.uiState.value.currentMove)

        // Complete step 2, then animate backward (2 -> 1): highlightedStepNumber is 2 while undoing step 2
        vm.advanceAnimation(1.0f)
        vm.previousStep()
        vm.advanceAnimation(0.1f)
        assertEquals(2, vm.uiState.value.highlightedStepNumber)
        assertEquals(solution[1], vm.uiState.value.currentMove)
    }
}
