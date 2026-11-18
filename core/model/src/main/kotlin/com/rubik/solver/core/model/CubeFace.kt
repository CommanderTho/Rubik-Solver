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
        displayNameVi = "Mặt Trên (Trắng)",
        orientationHintVi = "Tâm TRẮNG: Cạnh trên là XANH DƯƠNG, cạnh dưới là XANH LÁ",
        defaultColor = CubeColor.WHITE
    ),
    RIGHT(
        symbol = 'R',
        displayNameVi = "Mặt Phải (Đỏ)",
        orientationHintVi = "Tâm ĐỎ: Cạnh trên là TRẮNG, cạnh phải là XANH DƯƠNG",
        defaultColor = CubeColor.RED
    ),
    FRONT(
        symbol = 'F',
        displayNameVi = "Mặt Trước (Xanh lá)",
        orientationHintVi = "Tâm XANH LÁ: Cạnh trên là TRẮNG, cạnh phải là ĐỎ",
        defaultColor = CubeColor.GREEN
    ),
    DOWN(
        symbol = 'D',
        displayNameVi = "Mặt Dưới (Vàng)",
        orientationHintVi = "Tâm VÀNG: Cạnh trên là XANH LÁ, cạnh dưới là XANH DƯƠNG",
        defaultColor = CubeColor.YELLOW
    ),
    LEFT(
        symbol = 'L',
        displayNameVi = "Mặt Trái (Cam)",
        orientationHintVi = "Tâm CAM: Cạnh trên là TRẮNG, cạnh phải là XANH LÁ",
        defaultColor = CubeColor.ORANGE
    ),
    BACK(
        symbol = 'B',
        displayNameVi = "Mặt Sau (Xanh dương)",
        orientationHintVi = "Tâm XANH DƯƠNG: Cạnh trên là TRẮNG, cạnh phải là CAM",
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
