package com.rubik.solver.core.model

import kotlin.math.roundToInt

/**
 * Integer 3x3 orthogonal rotation matrix (row-major) representing exact 90-degree cubie orientation.
 */
data class IntMatrix3(
    val m00: Int, val m01: Int, val m02: Int,
    val m10: Int, val m11: Int, val m12: Int,
    val m20: Int, val m21: Int, val m22: Int
) {
    operator fun times(other: IntMatrix3): IntMatrix3 = IntMatrix3(
        m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
        m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
        m00 * other.m02 + m01 * other.m12 + m02 * other.m22,

        m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
        m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
        m10 * other.m02 + m11 * other.m12 + m12 * other.m22,

        m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
        m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
        m20 * other.m02 + m21 * other.m12 + m22 * other.m22
    )

    fun transformVector(x: Int, y: Int, z: Int): Triple<Int, Int, Int> = Triple(
        m00 * x + m01 * y + m02 * z,
        m10 * x + m11 * y + m12 * z,
        m20 * x + m21 * y + m22 * z
    )

    fun toFloatArray(): FloatArray = floatArrayOf(
        m00.toFloat(), m01.toFloat(), m02.toFloat(),
        m10.toFloat(), m11.toFloat(), m12.toFloat(),
        m20.toFloat(), m21.toFloat(), m22.toFloat()
    )

    companion object {
        val IDENTITY = IntMatrix3(
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
        )

        fun rotationAroundAxis(axis: RotationAxis, angleDegrees: Float): IntMatrix3 {
            val steps = ((angleDegrees / 90f).roundToInt() % 4 + 4) % 4
            var result = IDENTITY
            val stepMatrix = when (axis) {
                // +90 deg around +X: (x, y, z) -> (x, -z, y)
                RotationAxis.X -> IntMatrix3(
                    1, 0, 0,
                    0, 0, -1,
                    0, 1, 0
                )
                // +90 deg around +Y: (x, y, z) -> (z, y, -x)
                RotationAxis.Y -> IntMatrix3(
                    0, 0, 1,
                    0, 1, 0,
                    -1, 0, 0
                )
                // +90 deg around +Z: (x, y, z) -> (-y, x, z)
                RotationAxis.Z -> IntMatrix3(
                    0, -1, 0,
                    1, 0, 0,
                    0, 0, 1
                )
            }
            repeat(steps) {
                result = stepMatrix * result
            }
            return result
        }
    }
}

/**
 * Represents one of the 26 physical cubies of a 3x3 Rubik's Cube.
 *
 * @property id Stable identifier based on initial grid coordinates (e.g. "c_1_1_1").
 * @property initialStickers Colors painted in local cubie space (at identity orientation).
 * @property x Current grid X coordinate in {-1, 0, 1}.
 * @property y Current grid Y coordinate in {-1, 0, 1}.
 * @property z Current grid Z coordinate in {-1, 0, 1}.
 * @property orientation Cumulative discrete 3D rotation matrix from initial local space to world space.
 */
data class Cubie(
    val id: String,
    val initialStickers: Map<CubeFace, CubeColor>,
    val x: Int,
    val y: Int,
    val z: Int,
    val orientation: IntMatrix3 = IntMatrix3.IDENTITY
) {
    /**
     * Computes which color currently faces each world [CubeFace] direction.
     */
    fun worldStickers(): Map<CubeFace, CubeColor> {
        val result = mutableMapOf<CubeFace, CubeColor>()
        for ((localFace, color) in initialStickers) {
            val (nx, ny, nz) = faceNormal(localFace)
            val (wx, wy, wz) = orientation.transformVector(nx, ny, nz)
            val worldFace = normalToFace(wx, wy, wz)
            result[worldFace] = color
        }
        return result
    }

    companion object {
        fun faceNormal(face: CubeFace): Triple<Int, Int, Int> = when (face) {
            CubeFace.RIGHT -> Triple(1, 0, 0)
            CubeFace.LEFT -> Triple(-1, 0, 0)
            CubeFace.UP -> Triple(0, 1, 0)
            CubeFace.DOWN -> Triple(0, -1, 0)
            CubeFace.FRONT -> Triple(0, 0, 1)
            CubeFace.BACK -> Triple(0, 0, -1)
        }

        fun normalToFace(x: Int, y: Int, z: Int): CubeFace = when {
            x == 1 && y == 0 && z == 0 -> CubeFace.RIGHT
            x == -1 && y == 0 && z == 0 -> CubeFace.LEFT
            x == 0 && y == 1 && z == 0 -> CubeFace.UP
            x == 0 && y == -1 && z == 0 -> CubeFace.DOWN
            x == 0 && y == 0 && z == 1 -> CubeFace.FRONT
            x == 0 && y == 0 && z == -1 -> CubeFace.BACK
            else -> error("Pháp tuyến không hợp lệ: ($x, $y, $z)")
        }
    }
}

/**
 * Snapshot of all 26 cubies and pure operations to apply moves and convert back to [RubikCubeState].
 */
data class CubieCubeState(
    val cubies: List<Cubie>
) {
    init {
        require(cubies.size == 26) { "Khối Rubik 3x3 phải có đúng 26 cubie, nhận được ${cubies.size}" }
    }

    fun isInSlice(cubie: Cubie, move: Move): Boolean = when (move.axis) {
        RotationAxis.X -> cubie.x == move.layerCoord
        RotationAxis.Y -> cubie.y == move.layerCoord
        RotationAxis.Z -> cubie.z == move.layerCoord
    }

    fun applyMove(move: Move): CubieCubeState {
        val rot = IntMatrix3.rotationAroundAxis(move.axis, move.angleDegrees)
        val updated = cubies.map { cubie ->
            if (!isInSlice(cubie, move)) {
                cubie
            } else {
                val (nx, ny, nz) = rot.transformVector(cubie.x, cubie.y, cubie.z)
                cubie.copy(
                    x = nx,
                    y = ny,
                    z = nz,
                    orientation = rot * cubie.orientation
                )
            }
        }
        return CubieCubeState(updated)
    }

    fun applyMoves(moves: List<Move>): CubieCubeState =
        moves.fold(this) { acc, move -> acc.applyMove(move) }

    fun toRubikCubeState(): RubikCubeState {
        val byCoord = cubies.associateBy { Triple(it.x, it.y, it.z) }
        val facesMap = mutableMapOf<CubeFace, List<CubeColor?>>()

        for (face in CubeFace.entries) {
            val stickers = MutableList<CubeColor?>(9) { null }
            for (row in 0..2) {
                for (col in 0..2) {
                    val coord = faceCellToCoord(face, row, col)
                    val cubie = byCoord.getValue(coord)
                    stickers[row * 3 + col] = cubie.worldStickers().getValue(face)
                }
            }
            facesMap[face] = stickers
        }
        return RubikCubeState(facesMap)
    }

    fun isSolved(): Boolean {
        val state2D = toRubikCubeState()
        return CubeFace.entries.all { face ->
            val stickers = state2D.getFace(face)
            val first = stickers.first()
            first != null && stickers.all { it == first }
        }
    }

    companion object {
        fun solved(): CubieCubeState = fromRubikCubeState(RubikCubeState.solved())

        /**
         * Builds the 26 cubies directly from a complete 54-sticker [RubikCubeState].
         */
        fun fromRubikCubeState(state: RubikCubeState): CubieCubeState {
            val stickerMaps = mutableMapOf<Triple<Int, Int, Int>, MutableMap<CubeFace, CubeColor>>()
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x == 0 && y == 0 && z == 0) continue
                        stickerMaps[Triple(x, y, z)] = mutableMapOf()
                    }
                }
            }

            for (face in CubeFace.entries) {
                val faceStickers = state.getFace(face)
                for (row in 0..2) {
                    for (col in 0..2) {
                        val color = faceStickers[row * 3 + col]
                            ?: error("Không thể dựng mô hình 3D khi thiếu màu ở mặt $face ô ${row * 3 + col}")
                        val coord = faceCellToCoord(face, row, col)
                        stickerMaps.getValue(coord)[face] = color
                    }
                }
            }

            val cubies = stickerMaps.entries.map { (coord, stickers) ->
                val (x, y, z) = coord
                Cubie(
                    id = "c_${x}_${y}_${z}",
                    initialStickers = stickers.toMap(),
                    x = x,
                    y = y,
                    z = z
                )
            }
            return CubieCubeState(cubies)
        }

        /**
         * Maps Kociemba face + (row, col) in 0..2 to 3D cubie coordinate (x, y, z) in {-1, 0, 1}.
         */
        fun faceCellToCoord(face: CubeFace, row: Int, col: Int): Triple<Int, Int, Int> {
            return when (face) {
                // Looking from +Y down: top row is Back (z=-1), bottom row is Front (z=+1), left->right is x=-1..+1
                CubeFace.UP -> Triple(col - 1, 1, row - 1)
                // Looking from -Y up: top row is Front (z=+1), bottom row is Back (z=-1), left->right is x=-1..+1
                CubeFace.DOWN -> Triple(col - 1, -1, 1 - row)
                // Looking from +Z: top row is Up (y=+1), bottom row is Down (y=-1), left->right is x=-1..+1
                CubeFace.FRONT -> Triple(col - 1, 1 - row, 1)
                // Looking from -Z: top row is Up (y=+1), bottom row is Down (y=-1), left->right is x=+1..-1
                CubeFace.BACK -> Triple(1 - col, 1 - row, -1)
                // Looking from +X: top row is Up (y=+1), bottom row is Down (y=-1), left->right is z=+1..-1
                CubeFace.RIGHT -> Triple(1, 1 - row, 1 - col)
                // Looking from -X: top row is Up (y=+1), bottom row is Down (y=-1), left->right is z=-1..+1
                CubeFace.LEFT -> Triple(-1, 1 - row, col - 1)
            }
        }
    }
}
