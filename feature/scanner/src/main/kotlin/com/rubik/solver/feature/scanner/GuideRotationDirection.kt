package com.rubik.solver.feature.scanner

import com.rubik.solver.core.model.CubeFace

enum class GuideRotationDirection {
    NONE,
    ROTATE_LEFT,   // Xoay khối sang TRÁI (để mặt bên phải quay ra trước)
    ROTATE_RIGHT,  // Xoay khối sang PHẢI (để mặt bên trái quay ra trước)
    TILT_DOWN,     // Nghiêng khối XUỐNG (để mặt trên quay ra trước)
    TILT_UP,       // Nghiêng khối LÊN (để mặt dưới quay ra trước)
    FLIP_180       // Lật ngược khối 180°
}

object FaceAdjacencyGuide {

    /**
     * Danh sách 4 mặt liền kề của từng mặt Rubik theo thứ tự ưu tiên xoay tự nhiên:
     * 1. Mặt liền kề bên phải (xoay ngang sang trái)
     * 2. Mặt liền kề bên trái (xoay ngang sang phải)
     * 3. Mặt liền kề phía trên (nghiêng xuống)
     * 4. Mặt liền kề phía dưới (nghiêng lên)
     */
    fun getAdjacentFaces(face: CubeFace): List<CubeFace> = when (face) {
        CubeFace.FRONT -> listOf(CubeFace.RIGHT, CubeFace.LEFT, CubeFace.UP, CubeFace.DOWN)
        CubeFace.RIGHT -> listOf(CubeFace.BACK, CubeFace.FRONT, CubeFace.UP, CubeFace.DOWN)
        CubeFace.BACK  -> listOf(CubeFace.LEFT, CubeFace.RIGHT, CubeFace.UP, CubeFace.DOWN)
        CubeFace.LEFT  -> listOf(CubeFace.FRONT, CubeFace.BACK, CubeFace.UP, CubeFace.DOWN)
        CubeFace.UP    -> listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)
        CubeFace.DOWN  -> listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)
    }

    /**
     * Tìm mặt tiếp theo chưa chụp ưu tiên tối đa các mặt liền kề với mặt vừa chụp.
     * Người dùng có thể bắt đầu chụp từ BẤT KỲ mặt nào!
     */
    fun findNextGuidedFace(
        justScanned: CubeFace,
        capturedFaces: Set<CubeFace>
    ): CubeFace? {
        if (capturedFaces.size >= CubeFace.entries.size) return null

        // Ưu tiên 1: Chọn mặt liền kề chưa chụp gần nhất
        val adjacent = getAdjacentFaces(justScanned)
        val nextAdjacent = adjacent.firstOrNull { it !in capturedFaces }
        if (nextAdjacent != null) return nextAdjacent

        // Ưu tiên 2: Nếu cả 4 mặt liền kề đều đã chụp (chỉ còn mặt đối diện), chọn mặt còn lại
        return CubeFace.entries.firstOrNull { it !in capturedFaces }
    }

    /**
     * Xác định hướng xoay từ [from] sang [to]
     */
    fun getRotationDirection(from: CubeFace, to: CubeFace): GuideRotationDirection {
        if (from == to) return GuideRotationDirection.NONE
        return when (from to to) {
            // Xoay sang TRÁI (để mặt bên phải quay ra trước)
            CubeFace.FRONT to CubeFace.RIGHT,
            CubeFace.RIGHT to CubeFace.BACK,
            CubeFace.BACK to CubeFace.LEFT,
            CubeFace.LEFT to CubeFace.FRONT,
            CubeFace.UP to CubeFace.RIGHT,
            CubeFace.DOWN to CubeFace.RIGHT -> GuideRotationDirection.ROTATE_LEFT

            // Xoay sang PHẢI (để mặt bên trái quay ra trước)
            CubeFace.RIGHT to CubeFace.FRONT,
            CubeFace.BACK to CubeFace.RIGHT,
            CubeFace.LEFT to CubeFace.BACK,
            CubeFace.FRONT to CubeFace.LEFT,
            CubeFace.UP to CubeFace.LEFT,
            CubeFace.DOWN to CubeFace.LEFT -> GuideRotationDirection.ROTATE_RIGHT

            // Nghiêng khối XUỐNG (mặt trên quay ra trước)
            CubeFace.FRONT to CubeFace.UP,
            CubeFace.RIGHT to CubeFace.UP,
            CubeFace.BACK to CubeFace.UP,
            CubeFace.LEFT to CubeFace.UP,
            CubeFace.DOWN to CubeFace.FRONT,
            CubeFace.UP to CubeFace.BACK -> GuideRotationDirection.TILT_DOWN

            // Nghiêng khối LÊN (mặt dưới quay ra trước)
            CubeFace.FRONT to CubeFace.DOWN,
            CubeFace.RIGHT to CubeFace.DOWN,
            CubeFace.BACK to CubeFace.DOWN,
            CubeFace.LEFT to CubeFace.DOWN,
            CubeFace.UP to CubeFace.FRONT,
            CubeFace.DOWN to CubeFace.BACK -> GuideRotationDirection.TILT_UP

            // Lật ngược khối 180°
            CubeFace.UP to CubeFace.DOWN,
            CubeFace.DOWN to CubeFace.UP,
            CubeFace.FRONT to CubeFace.BACK,
            CubeFace.BACK to CubeFace.FRONT,
            CubeFace.LEFT to CubeFace.RIGHT,
            CubeFace.RIGHT to CubeFace.LEFT -> GuideRotationDirection.FLIP_180

            else -> GuideRotationDirection.ROTATE_LEFT
        }
    }

    /**
     * Câu lệnh hướng dẫn người dùng xoay khối trực quan
     */
    fun rotationPrompt(from: CubeFace, to: CubeFace): String {
        return when (getRotationDirection(from, to)) {
            GuideRotationDirection.ROTATE_LEFT ->
                "Xoay khối sang TRÁI 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            GuideRotationDirection.ROTATE_RIGHT ->
                "Xoay khối sang PHẢI 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            GuideRotationDirection.TILT_DOWN ->
                "Nghiêng khối XUỐNG 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            GuideRotationDirection.TILT_UP ->
                "Nghiêng khối LÊN 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            GuideRotationDirection.FLIP_180 ->
                "Lật ngược khối 180° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            GuideRotationDirection.NONE ->
                "Đưa mặt ${to.displayNameVi} vào khung và chụp"
        }
    }
}
