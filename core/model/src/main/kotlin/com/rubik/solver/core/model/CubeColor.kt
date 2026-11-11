package com.rubik.solver.core.model

/**
 * Standard 6 colors of a 3x3 Rubik's Cube.
 */
enum class CubeColor(
    val displayNameVi: String,
    val shortCode: Char,
    val argb: Long
) {
    WHITE("Trắng", 'W', 0xFFF8FAFC),
    YELLOW("Vàng", 'Y', 0xFFFACC15),
    RED("Đỏ", 'R', 0xFFEF4444),
    ORANGE("Cam", 'O', 0xFFF97316),
    GREEN("Xanh lá", 'G', 0xFF22C55E),
    BLUE("Xanh dương", 'B', 0xFF2563EB);

    companion object {
        val ALL: List<CubeColor> = entries
    }
}
