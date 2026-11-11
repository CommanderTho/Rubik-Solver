package com.rubik.solver.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CubieCubeEngineTest {

    @Test
    fun `four identical quarter turns return cube to solved state`() {
        val quarterMoves = listOf(
            Move.U, Move.D, Move.L, Move.R, Move.F, Move.B,
            Move.U_PRIME, Move.D_PRIME, Move.L_PRIME, Move.R_PRIME, Move.F_PRIME, Move.B_PRIME
        )
        for (move in quarterMoves) {
            var cube = CubieCubeState.solved()
            repeat(4) { cube = cube.applyMove(move) }
            assertTrue("4x ${move.notation} should restore solved state", cube.isSolved())
        }
    }

    @Test
    fun `move followed by its inverse returns cube to solved state`() {
        for (move in Move.entries) {
            val cube = CubieCubeState.solved()
                .applyMove(move)
                .applyMove(move.inverse())
            assertTrue("${move.notation} + ${move.inverse().notation} should be solved", cube.isSolved())
        }
    }

    @Test
    fun `sexy move repeated 6 times returns cube to solved state`() {
        val sequence = Move.parseSequence("R U R' U'")
        var cube = CubieCubeState.solved()
        cube = cube.applyMoves(sequence)
        assertFalse(cube.isSolved())

        repeat(5) { cube = cube.applyMoves(sequence) }
        assertTrue(cube.isSolved())
    }

    @Test
    fun `U move cycles Front top row to Left top row`() {
        val afterU = CubieCubeState.solved().applyMove(Move.U).toRubikCubeState()
        // Default: FRONT=GREEN, RIGHT=RED, BACK=BLUE, LEFT=ORANGE
        // Turning U clockwise (from top) moves RIGHT top row -> FRONT top row
        val front = afterU.getFace(CubeFace.FRONT)
        assertEquals(CubeColor.RED, front[0])
        assertEquals(CubeColor.RED, front[1])
        assertEquals(CubeColor.RED, front[2])
        assertEquals(CubeColor.GREEN, front[4])

        val left = afterU.getFace(CubeFace.LEFT)
        assertEquals(CubeColor.GREEN, left[0])
        assertEquals(CubeColor.GREEN, left[1])
        assertEquals(CubeColor.GREEN, left[2])
    }

    @Test
    fun `R move moves Front right column to Up right column`() {
        val afterR = CubieCubeState.solved().applyMove(Move.R).toRubikCubeState()
        val up = afterR.getFace(CubeFace.UP)
        assertEquals(CubeColor.GREEN, up[2])
        assertEquals(CubeColor.GREEN, up[5])
        assertEquals(CubeColor.GREEN, up[8])
    }

    @Test
    fun `F move moves Up bottom row to Right left column`() {
        val afterF = CubieCubeState.solved().applyMove(Move.F).toRubikCubeState()
        val right = afterF.getFace(CubeFace.RIGHT)
        assertEquals(CubeColor.WHITE, right[0])
        assertEquals(CubeColor.WHITE, right[3])
        assertEquals(CubeColor.WHITE, right[6])
    }
}
