package com.rubik.solver.core.solver

import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.CubeValidator
import com.rubik.solver.core.model.CubieCubeState
import com.rubik.solver.core.model.Move
import com.rubik.solver.core.model.RubikCubeState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KociembaRubikSolverTest {

    private val solver = KociembaRubikSolver()

    @Test
    fun `solved cube returns zero moves`() = runTest {
        val result = solver.solve(RubikCubeState.solved())
        assertTrue(result is SolveResult.Success)
        val success = result as SolveResult.Success
        assertTrue(success.isAlreadySolved)
        assertTrue(success.moves.isEmpty())
    }

    @Test
    fun `solves scrambled cube in 20 or fewer moves and 3D cubie engine verifies solution restores solved state`() = runTest {
        val scramble = Move.parseSequence("R U R' F2 D' L B' U2 R2 F' D L2")
        val scrambledCubie = CubieCubeState.solved().applyMoves(scramble)
        val scrambled2D = scrambledCubie.toRubikCubeState()

        val validation = CubeValidator.validate(scrambled2D, solvabilityVerifier = solver)
        assertTrue("Scrambled state should pass validation & parity check", validation.isValid)

        val result = solver.solve(scrambled2D)
        assertTrue("Expected SolveResult.Success, got $result", result is SolveResult.Success)
        val success = result as SolveResult.Success

        assertTrue("Solution should be <= 21 moves, was ${success.moves.size}", success.moves.size <= 21)
        assertTrue(success.moves.isNotEmpty())

        // Apply solver output to 3D CubieCubeState and verify solved!
        val finalCubie = scrambledCubie.applyMoves(success.moves)
        assertTrue("Applying Kociemba moves on CubieCubeState must yield solved cube", finalCubie.isSolved())
    }

    @Test
    fun `rejects physically impossible corner twist via parity check`() = runTest {
        // Twist one corner on an otherwise solved cube: swap U9 (UFR's U) -> F3 (UFR's F) -> R1 (UFR's R)
        val solved = RubikCubeState.solved()
        val uColor = solved.getSticker(CubeFace.UP, 8)!!
        val fColor = solved.getSticker(CubeFace.FRONT, 2)!!
        val rColor = solved.getSticker(CubeFace.RIGHT, 0)!!

        val twisted = solved
            .withSticker(CubeFace.UP, 8, fColor)
            .withSticker(CubeFace.FRONT, 2, rColor)
            .withSticker(CubeFace.RIGHT, 0, uColor)

        val validation = CubeValidator.validate(twisted, solvabilityVerifier = solver)
        assertTrue(!validation.isValid)
        assertNotNull(validation.issues.firstOrNull())

        val result = solver.solve(twisted)
        assertTrue(result is SolveResult.Failure)
    }

    @Test
    fun `solves superflip-like deep scramble cleanly`() = runTest {
        val scramble = Move.parseSequence("U R2 F B R B2 R U2 L B2 R U' D' R2 F R' L B2 U2 F2")
        val scrambledCubie = CubieCubeState.solved().applyMoves(scramble)
        val result = solver.solve(scrambledCubie.toRubikCubeState())
        assertTrue(result is SolveResult.Success)
        val success = result as SolveResult.Success
        assertEquals(true, scrambledCubie.applyMoves(success.moves).isSolved())
    }
}
