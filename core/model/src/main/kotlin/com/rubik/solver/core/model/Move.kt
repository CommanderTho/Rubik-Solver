package com.rubik.solver.core.model

enum class RotationAxis {
    X, Y, Z
}

/**
 * Half-Turn Metric (HTM) Rubik's Cube move with 3D right-handed rotation metadata.
 * Coordinate convention: +X = Right, +Y = Up, +Z = Front.
 */
enum class Move(
    val notation: String,
    val face: CubeFace,
    val axis: RotationAxis,
    val layerCoord: Int,
    val angleDegrees: Float,
    val quarterTurnsClockwiseFromFace: Int,
    val descriptionVi: String
) {
    U("U", CubeFace.UP, RotationAxis.Y, 1, -90f, 1, "Xoay mặt Trên theo chiều kim đồng hồ 90°"),
    U_PRIME("U'", CubeFace.UP, RotationAxis.Y, 1, 90f, 3, "Xoay mặt Trên ngược chiều kim đồng hồ 90°"),
    U2("U2", CubeFace.UP, RotationAxis.Y, 1, -180f, 2, "Xoay mặt Trên 180°"),

    R("R", CubeFace.RIGHT, RotationAxis.X, 1, -90f, 1, "Xoay mặt Phải theo chiều kim đồng hồ 90°"),
    R_PRIME("R'", CubeFace.RIGHT, RotationAxis.X, 1, 90f, 3, "Xoay mặt Phải ngược chiều kim đồng hồ 90°"),
    R2("R2", CubeFace.RIGHT, RotationAxis.X, 1, -180f, 2, "Xoay mặt Phải 180°"),

    F("F", CubeFace.FRONT, RotationAxis.Z, 1, -90f, 1, "Xoay mặt Trước theo chiều kim đồng hồ 90°"),
    F_PRIME("F'", CubeFace.FRONT, RotationAxis.Z, 1, 90f, 3, "Xoay mặt Trước ngược chiều kim đồng hồ 90°"),
    F2("F2", CubeFace.FRONT, RotationAxis.Z, 1, -180f, 2, "Xoay mặt Trước 180°"),

    D("D", CubeFace.DOWN, RotationAxis.Y, -1, 90f, 1, "Xoay mặt Dưới theo chiều kim đồng hồ 90°"),
    D_PRIME("D'", CubeFace.DOWN, RotationAxis.Y, -1, -90f, 3, "Xoay mặt Dưới ngược chiều kim đồng hồ 90°"),
    D2("D2", CubeFace.DOWN, RotationAxis.Y, -1, 180f, 2, "Xoay mặt Dưới 180°"),

    L("L", CubeFace.LEFT, RotationAxis.X, -1, 90f, 1, "Xoay mặt Trái theo chiều kim đồng hồ 90°"),
    L_PRIME("L'", CubeFace.LEFT, RotationAxis.X, -1, -90f, 3, "Xoay mặt Trái ngược chiều kim đồng hồ 90°"),
    L2("L2", CubeFace.LEFT, RotationAxis.X, -1, 180f, 2, "Xoay mặt Trái 180°"),

    B("B", CubeFace.BACK, RotationAxis.Z, -1, 90f, 1, "Xoay mặt Sau theo chiều kim đồng hồ 90°"),
    B_PRIME("B'", CubeFace.BACK, RotationAxis.Z, -1, -90f, 3, "Xoay mặt Sau ngược chiều kim đồng hồ 90°"),
    B2("B2", CubeFace.BACK, RotationAxis.Z, -1, 180f, 2, "Xoay mặt Sau 180°");

    fun inverse(): Move = when (this) {
        U -> U_PRIME
        U_PRIME -> U
        U2 -> U2
        R -> R_PRIME
        R_PRIME -> R
        R2 -> R2
        F -> F_PRIME
        F_PRIME -> F
        F2 -> F2
        D -> D_PRIME
        D_PRIME -> D
        D2 -> D2
        L -> L_PRIME
        L_PRIME -> L
        L2 -> L2
        B -> B_PRIME
        B_PRIME -> B
        B2 -> B2
    }

    companion object {
        private val byNotation = entries.associateBy { it.notation }

        fun parse(token: String): Move =
            byNotation[token.trim()]
                ?: throw IllegalArgumentException("Ký hiệu bước xoay không hợp lệ: '$token'")

        fun parseSequence(sequence: String): List<Move> =
            sequence.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { parse(it) }
    }
}
