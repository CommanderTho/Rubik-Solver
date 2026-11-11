package com.rubik.solver.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceletMapperTest {

    @Test
    fun `maps rotated cube orientation using center colors dynamically`() {
        // Suppose user holds Yellow on UP and White on DOWN (standard solved relative colors rotated 180 on X)
        val rotatedSolved = RubikCubeState(
            mapOf(
                CubeFace.UP to List(9) { CubeColor.YELLOW },
                CubeFace.DOWN to List(9) { CubeColor.WHITE },
                CubeFace.FRONT to List(9) { CubeColor.BLUE },
                CubeFace.BACK to List(9) { CubeColor.GREEN },
                CubeFace.LEFT to List(9) { CubeColor.RED },
                CubeFace.RIGHT to List(9) { CubeColor.ORANGE }
            )
        )

        val result = FaceletMapper.toFaceletString(rotatedSolved)
        assertTrue(result.isSuccess)
        assertEquals(
            "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",
            result.getOrThrow()
        )
    }
}
