package com.rubik.solver.core.model

/**
 * Immutable 54-sticker state of a 3x3 Rubik's Cube.
 * Each face holds 9 stickers ordered row-major (top-left to bottom-right),
 * where `null` indicates an unscanned/unset cell.
 */
data class RubikCubeState(
    val faces: Map<CubeFace, List<CubeColor?>> = emptyStateMap()
) {
    init {
        require(faces.size == CubeFace.entries.size) { "Cần đủ 6 mặt cho khối Rubik." }
        faces.forEach { (face, stickers) ->
            require(stickers.size == 9) { "Mặt $face phải có đúng 9 ô." }
        }
    }

    fun getFace(face: CubeFace): List<CubeColor?> = faces.getValue(face)

    fun getSticker(face: CubeFace, index: Int): CubeColor? {
        require(index in 0..8) { "Chỉ số ô phải từ 0..8, nhận được $index" }
        return faces.getValue(face)[index]
    }

    fun isFaceScanned(face: CubeFace): Boolean = faces.getValue(face).none { it == null }

    fun scannedFaceCount(): Int = CubeFace.entries.count { isFaceScanned(it) }

    fun withFace(face: CubeFace, stickers: List<CubeColor?>): RubikCubeState {
        require(stickers.size == 9) { "Mỗi mặt phải có đúng 9 ô." }
        return copy(faces = faces + (face to stickers.toList()))
    }

    fun withSticker(face: CubeFace, index: Int, color: CubeColor): RubikCubeState {
        require(index in 0..8) { "Chỉ số ô phải từ 0..8" }
        val updated = faces.getValue(face).toMutableList().apply { this[index] = color }
        return copy(faces = faces + (face to updated))
    }

    fun colorCounts(): Map<CubeColor, Int> {
        val counts = CubeColor.entries.associateWith { 0 }.toMutableMap()
        faces.values.flatten().filterNotNull().forEach { color ->
            counts[color] = counts.getValue(color) + 1
        }
        return counts
    }

    fun filledCount(): Int = faces.values.sumOf { list -> list.count { it != null } }

    companion object {
        const val TOTAL_STICKERS = 54
        const val STICKERS_PER_COLOR = 9
        const val CENTER_INDEX = 4

        fun emptyStateMap(): Map<CubeFace, List<CubeColor?>> =
            CubeFace.entries.associateWith { List(9) { null } }

        fun solved(): RubikCubeState = RubikCubeState(
            faces = CubeFace.entries.associateWith { face -> List(9) { face.defaultColor } }
        )

        fun fromFaceletString(facelets: String): RubikCubeState {
            require(facelets.length == TOTAL_STICKERS) {
                "Chuỗi facelet phải có đúng 54 ký tự, nhận được ${facelets.length}"
            }
            val symbolToColor = CubeFace.entries.associate { it.symbol to it.defaultColor }
            val mapped = mutableMapOf<CubeFace, List<CubeColor?>>()
            CubeFace.KOCIEMBA_ORDER.forEachIndexed { faceIdx, face ->
                val slice = facelets.substring(faceIdx * 9, (faceIdx + 1) * 9)
                mapped[face] = slice.map { ch ->
                    symbolToColor[ch]
                        ?: throw IllegalArgumentException("Ký tự facelet không hợp lệ: '$ch'")
                }
            }
            return RubikCubeState(mapped)
        }
    }
}
