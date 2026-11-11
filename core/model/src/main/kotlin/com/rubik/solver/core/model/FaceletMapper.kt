package com.rubik.solver.core.model

/**
 * Converts between a scanned/edited [RubikCubeState] (colored stickers)
 * and Kociemba's 54-character facelet string (`U1..U9 R1..R9 F1..F9 D1..D9 L1..L9 B1..B9`).
 *
 * Uses the 6 center stickers (index 4 on each face) as the canonical color-to-face mapping,
 * allowing users to hold the cube in any color orientation as long as the 6 center colors are distinct.
 */
object FaceletMapper {

    fun toFaceletString(state: RubikCubeState): Result<String> {
        val colorToFaceSymbol = mutableMapOf<CubeColor, Char>()
        for (face in CubeFace.KOCIEMBA_ORDER) {
            val centerColor = state.getSticker(face, RubikCubeState.CENTER_INDEX)
                ?: return Result.failure(
                    IllegalStateException("Thiếu màu tâm ở ${face.displayNameVi}")
                )
            if (centerColor in colorToFaceSymbol) {
                return Result.failure(
                    IllegalStateException("Màu tâm '${centerColor.displayNameVi}' bị trùng lặp giữa các mặt")
                )
            }
            colorToFaceSymbol[centerColor] = face.symbol
        }

        val builder = StringBuilder(RubikCubeState.TOTAL_STICKERS)
        for (face in CubeFace.KOCIEMBA_ORDER) {
            val stickers = state.getFace(face)
            for ((index, color) in stickers.withIndex()) {
                if (color == null) {
                    return Result.failure(
                        IllegalStateException("Ô #${index + 1} của ${face.displayNameVi} chưa có màu")
                    )
                }
                val symbol = colorToFaceSymbol[color]
                    ?: return Result.failure(
                        IllegalStateException("Màu '${color.displayNameVi}' không khớp với bất kỳ tâm mặt nào")
                    )
                builder.append(symbol)
            }
        }
        return Result.success(builder.toString())
    }
}
