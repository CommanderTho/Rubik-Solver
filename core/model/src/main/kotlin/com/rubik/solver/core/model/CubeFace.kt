package com.rubik.solver.core.model

/**
 * Canonical 6 faces of the Rubik's Cube in Kociemba's facelet order: U, R, F, D, L, B.
 */
enum class CubeFace(
    val symbol: Char,
    val displayNameVi: String,
    val orientationHintVi: String,
    val defaultColor: CubeColor
) {
    UP(
        symbol = 'U',
        displayNameVi = "Mặt Trên (Up)",
        orientationHintVi = "Hướng mặt Sau (Back) lên trên, mặt Trước (Front) xuống dưới",
        defaultColor = CubeColor.WHITE
    ),
    RIGHT(
        symbol = 'R',
        displayNameVi = "Mặt Phải (Right)",
        orientationHintVi = "Giữ mặt Trên (Up) hướng lên cạnh trên khung lưới",
        defaultColor = CubeColor.RED
    ),
    FRONT(
        symbol = 'F',
        displayNameVi = "Mặt Trước (Front)",
        orientationHintVi = "Giữ mặt Trên (Up) hướng lên cạnh trên khung lưới",
        defaultColor = CubeColor.GREEN
    ),
    DOWN(
        symbol = 'D',
        displayNameVi = "Mặt Dưới (Down)",
        orientationHintVi = "Hướng mặt Trước (Front) lên cạnh trên khung lưới",
        defaultColor = CubeColor.YELLOW
    ),
    LEFT(
        symbol = 'L',
        displayNameVi = "Mặt Trái (Left)",
        orientationHintVi = "Giữ mặt Trên (Up) hướng lên cạnh trên khung lưới",
        defaultColor = CubeColor.ORANGE
    ),
    BACK(
        symbol = 'B',
        displayNameVi = "Mặt Sau (Back)",
        orientationHintVi = "Giữ mặt Trên (Up) hướng lên cạnh trên khung lưới",
        defaultColor = CubeColor.BLUE
    );

    companion object {
        /**
         * Order required by Kociemba's two-phase solver (54 facelets: U, R, F, D, L, B).
         */
        val KOCIEMBA_ORDER: List<CubeFace> = listOf(UP, RIGHT, FRONT, DOWN, LEFT, BACK)

        /**
         * Ergonomic scanning order for the user (U -> D -> F -> B -> L -> R as requested).
         */
        val SCAN_ORDER: List<CubeFace> = listOf(UP, DOWN, FRONT, BACK, LEFT, RIGHT)

        fun fromSymbol(symbol: Char): CubeFace? = entries.firstOrNull { it.symbol == symbol }
    }
}
