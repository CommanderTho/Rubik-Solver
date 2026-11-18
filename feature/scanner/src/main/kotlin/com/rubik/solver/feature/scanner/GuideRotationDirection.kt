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
     * Thứ tự các mặt vòng ngang (equatorial ring):
     * Khi xoay sang TRÁI quanh trục đứng, mặt TRẮNG (Up) luôn luôn ở trên đỉnh,
     * mặt VÀNG (Down) luôn ở đáy, không bao giờ bị lệch góc 90°!
     */
    val EQUATORIAL_RING = listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)

    /**
     * Danh sách các mặt liền kề ưu tiên theo thứ tự xoay tự nhiên bảo toàn hướng chuẩn:
     */
    fun getAdjacentFaces(face: CubeFace): List<CubeFace> = when (face) {
        CubeFace.FRONT -> listOf(CubeFace.RIGHT, CubeFace.UP, CubeFace.DOWN, CubeFace.LEFT)
        CubeFace.RIGHT -> listOf(CubeFace.BACK, CubeFace.UP, CubeFace.DOWN, CubeFace.FRONT)
        CubeFace.BACK  -> listOf(CubeFace.LEFT, CubeFace.UP, CubeFace.DOWN, CubeFace.RIGHT)
        CubeFace.LEFT  -> listOf(CubeFace.FRONT, CubeFace.UP, CubeFace.DOWN, CubeFace.BACK)
        CubeFace.UP    -> listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)
        CubeFace.DOWN  -> listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)
    }

    /**
     * Tìm mặt tiếp theo chưa chụp ưu tiên tối đa các mặt liền kề bảo toàn hướng chuẩn Kociemba.
     * Người dùng có thể bắt đầu chụp từ BẤT KỲ mặt nào!
     */
    fun findNextGuidedFace(
        justScanned: CubeFace,
        capturedFaces: Set<CubeFace>
    ): CubeFace? {
        if (capturedFaces.size >= CubeFace.entries.size) return null

        // 1. Nếu đang ở vòng ngang (F, R, B, L): ưu tiên đi hết các mặt vòng ngang theo chiều xoay trái
        if (justScanned in EQUATORIAL_RING) {
            val startIdx = EQUATORIAL_RING.indexOf(justScanned)
            for (step in 1 until EQUATORIAL_RING.size) {
                val candidate = EQUATORIAL_RING[(startIdx + step) % EQUATORIAL_RING.size]
                if (candidate !in capturedFaces) {
                    return candidate
                }
            }
            // Tất cả 4 mặt vòng ngang đã chụp xong -> Đi tiếp mặt TRÊN (Up) rồi mặt DƯỚI (Down)
            if (CubeFace.UP !in capturedFaces) return CubeFace.UP
            if (CubeFace.DOWN !in capturedFaces) return CubeFace.DOWN
        }

        // 2. Nếu đang ở mặt TRÊN (Up):
        if (justScanned == CubeFace.UP) {
            // Ưu tiên quay về FRONT nếu chưa chụp
            if (CubeFace.FRONT !in capturedFaces) return CubeFace.FRONT
            // Nếu FRONT đã chụp, đi tiếp các mặt vòng ngang chưa chụp
            val nextRing = EQUATORIAL_RING.firstOrNull { it !in capturedFaces }
            if (nextRing != null) return nextRing
            if (CubeFace.DOWN !in capturedFaces) return CubeFace.DOWN
        }

        // 3. Nếu đang ở mặt DƯỚI (Down):
        if (justScanned == CubeFace.DOWN) {
            if (CubeFace.FRONT !in capturedFaces) return CubeFace.FRONT
            val nextRing = EQUATORIAL_RING.firstOrNull { it !in capturedFaces }
            if (nextRing != null) return nextRing
            if (CubeFace.UP !in capturedFaces) return CubeFace.UP
        }

        // Dự phòng: Chọn mặt đầu tiên chưa chụp
        return CubeFace.entries.firstOrNull { it !in capturedFaces }
    }

    /**
     * Xác định hướng xoay từ [from] sang [to]
     */
    fun getRotationDirection(from: CubeFace, to: CubeFace): GuideRotationDirection {
        if (from == to) return GuideRotationDirection.NONE
        return when (from to to) {
            // Xoay sang TRÁI (mặt bên phải quay ra trước)
            CubeFace.FRONT to CubeFace.RIGHT,
            CubeFace.RIGHT to CubeFace.BACK,
            CubeFace.BACK to CubeFace.LEFT,
            CubeFace.LEFT to CubeFace.FRONT -> GuideRotationDirection.ROTATE_LEFT

            // Xoay sang PHẢI (mặt bên trái quay ra trước)
            CubeFace.RIGHT to CubeFace.FRONT,
            CubeFace.BACK to CubeFace.RIGHT,
            CubeFace.LEFT to CubeFace.BACK,
            CubeFace.FRONT to CubeFace.LEFT -> GuideRotationDirection.ROTATE_RIGHT

            // Nghiêng khối XUỐNG (mặt trên quay ra trước)
            CubeFace.FRONT to CubeFace.UP,
            CubeFace.DOWN to CubeFace.FRONT,
            CubeFace.UP to CubeFace.BACK,
            CubeFace.RIGHT to CubeFace.UP,
            CubeFace.LEFT to CubeFace.UP,
            CubeFace.BACK to CubeFace.UP -> GuideRotationDirection.TILT_DOWN

            // Nghiêng khối LÊN (mặt dưới quay ra trước)
            CubeFace.FRONT to CubeFace.DOWN,
            CubeFace.UP to CubeFace.FRONT,
            CubeFace.DOWN to CubeFace.BACK,
            CubeFace.RIGHT to CubeFace.DOWN,
            CubeFace.LEFT to CubeFace.DOWN,
            CubeFace.BACK to CubeFace.DOWN -> GuideRotationDirection.TILT_UP

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
     * Câu lệnh hướng dẫn người dùng xoay khối trực quan kèm màu nhận diện chuẩn
     */
    fun rotationPrompt(from: CubeFace, to: CubeFace): String {
        return when (from to to) {
            CubeFace.LEFT to CubeFace.UP ->
                "Xoay về mặt XANH LÁ rồi nghiêng XUỐNG ➔ Chụp ${to.displayNameVi}"
            CubeFace.LEFT to CubeFace.DOWN ->
                "Xoay về mặt XANH LÁ rồi nghiêng LÊN ➔ Chụp ${to.displayNameVi}"
            CubeFace.RIGHT to CubeFace.UP ->
                "Xoay về mặt XANH LÁ rồi nghiêng XUỐNG ➔ Chụp ${to.displayNameVi}"
            CubeFace.RIGHT to CubeFace.DOWN ->
                "Xoay về mặt XANH LÁ rồi nghiêng LÊN ➔ Chụp ${to.displayNameVi}"
            CubeFace.BACK to CubeFace.UP ->
                "Xoay về mặt XANH LÁ rồi nghiêng XUỐNG ➔ Chụp ${to.displayNameVi}"
            CubeFace.BACK to CubeFace.DOWN ->
                "Xoay về mặt XANH LÁ rồi nghiêng LÊN ➔ Chụp ${to.displayNameVi}"
            CubeFace.UP to CubeFace.DOWN ->
                "Lật ngược khối 180° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            CubeFace.DOWN to CubeFace.UP ->
                "Lật ngược khối 180° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            CubeFace.FRONT to CubeFace.UP ->
                "Nghiêng khối XUỐNG 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            CubeFace.FRONT to CubeFace.DOWN ->
                "Nghiêng khối LÊN 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            CubeFace.UP to CubeFace.FRONT ->
                "Nghiêng khối LÊN 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            CubeFace.DOWN to CubeFace.FRONT ->
                "Nghiêng khối XUỐNG 90° theo mũi tên ➔ Chụp ${to.displayNameVi}"
            else -> when (getRotationDirection(from, to)) {
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
                    "Đưa ${to.displayNameVi} vào khung và chụp"
            }
        }
    }
}
