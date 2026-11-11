package com.rubik.solver.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeValidatorTest {

    @Test
    fun `solved cube state is valid with 9 stickers per color`() {
        val state = RubikCubeState.solved()
        val result = CubeValidator.validate(state)

        assertTrue(result.isValid)
        assertEquals(54, result.filledCount)
        assertEquals(0, result.missingCount)
        assertTrue(result.issues.isEmpty())
        assertEquals(
            "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",
            result.faceletString
        )
        CubeColor.entries.forEach { color ->
            assertEquals(9, result.colorCounts[color])
        }
    }

    @Test
    fun `incomplete cube state fails validation with missing count`() {
        val empty = RubikCubeState()
        val result = CubeValidator.validate(empty)

        assertFalse(result.isValid)
        assertEquals(0, result.filledCount)
        assertEquals(54, result.missingCount)
        assertTrue(result.issues.any { it.type == ValidationIssueType.INCOMPLETE_STICKERS })
        assertNull(result.faceletString)
    }

    @Test
    fun `state with 10 white and 8 yellow stickers fails color count validation`() {
        val modified = RubikCubeState.solved()
            .withSticker(CubeFace.DOWN, 0, CubeColor.WHITE)

        val result = CubeValidator.validate(modified)

        assertFalse(result.isValid)
        assertEquals(10, result.colorCounts[CubeColor.WHITE])
        assertEquals(8, result.colorCounts[CubeColor.YELLOW])
        assertTrue(result.issues.any { it.type == ValidationIssueType.INVALID_COLOR_COUNT })
        assertNull(result.faceletString)
    }

    @Test
    fun `duplicate center colors fail validation even if counts are 9 each`() {
        // Swap UP center (WHITE) with FRONT top-left (GREEN) and keep 9 of each color
        val state = RubikCubeState.solved()
            .withSticker(CubeFace.UP, RubikCubeState.CENTER_INDEX, CubeColor.GREEN)
            .withSticker(CubeFace.FRONT, 0, CubeColor.WHITE)

        val result = CubeValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.type == ValidationIssueType.DUPLICATE_CENTER_COLORS })
        assertNull(result.faceletString)
    }

    @Test
    fun `solvability verifier error is surfaced as parity issue`() {
        val state = RubikCubeState.solved()
        val result = CubeValidator.validate(state) { _ ->
            "Góc bị xoay sai chiều (corner twist)."
        }

        assertFalse(result.isValid)
        assertEquals(1, result.issues.size)
        assertEquals(ValidationIssueType.UNSOLVABLE_CUBE_PARITY, result.issues.first().type)
        assertNull(result.faceletString)
    }

    @Test
    fun `valid scrambled state passes count and center checks`() {
        val scrambled = CubieCubeState.solved()
            .applyMoves(Move.parseSequence("R U R' U' F2 D' L B2"))
            .toRubikCubeState()

        val result = CubeValidator.validate(scrambled)
        assertTrue(result.isValid)
        assertNotNull(result.faceletString)
        assertEquals(54, result.faceletString?.length)
    }
}
