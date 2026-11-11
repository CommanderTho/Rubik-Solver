package com.rubik.solver.feature.scanner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
}

private data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    fun normalized(): Quat {
        val len = sqrt(x * x + y * y + z * z + w * w).coerceAtLeast(1e-6f)
        return Quat(x / len, y / len, z / len, w / len)
    }

    operator fun times(o: Quat): Quat = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z
    )

    fun rotate(v: Vec3): Vec3 {
        val qv = Quat(v.x, v.y, v.z, 0f)
        val conj = Quat(-x, -y, -z, w)
        val res = this * qv * conj
        return Vec3(res.x, res.y, res.z)
    }

    companion object {
        fun fromAxisAngle(ax: Float, ay: Float, az: Float, degrees: Float): Quat {
            val half = ((degrees * PI / 180.0) * 0.5).toFloat()
            val s = sin(half)
            val c = cos(half)
            return Quat(ax * s, ay * s, az * s, c).normalized()
        }

        fun slerp(a: Quat, b: Quat, t: Float): Quat {
            var dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
            var target = b
            if (dot < 0f) {
                dot = -dot
                target = Quat(-b.x, -b.y, -b.z, -b.w)
            }
            if (dot > 0.9995f) {
                return Quat(
                    a.x + (target.x - a.x) * t,
                    a.y + (target.y - a.y) * t,
                    a.z + (target.z - a.z) * t,
                    a.w + (target.w - a.w) * t
                ).normalized()
            }
            val theta0 = acos(dot.coerceIn(-1f, 1f))
            val theta = theta0 * t
            val sinTheta = sin(theta)
            val sinTheta0 = sin(theta0)
            val s0 = cos(theta) - dot * sinTheta / sinTheta0
            val s1 = sinTheta / sinTheta0
            return Quat(
                s0 * a.x + s1 * target.x,
                s0 * a.y + s1 * target.y,
                s0 * a.z + s1 * target.z,
                s0 * a.w + s1 * target.w
            ).normalized()
        }
    }
}

private data class FaceBasis(
    val normal: Vec3,
    val right: Vec3,
    val down: Vec3
)

private fun faceBasis(face: CubeFace): FaceBasis = when (face) {
    CubeFace.FRONT -> FaceBasis(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, -1f, 0f))
    CubeFace.BACK -> FaceBasis(Vec3(0f, 0f, -1f), Vec3(-1f, 0f, 0f), Vec3(0f, -1f, 0f))
    CubeFace.RIGHT -> FaceBasis(Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f), Vec3(0f, -1f, 0f))
    CubeFace.LEFT -> FaceBasis(Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, -1f, 0f))
    CubeFace.UP -> FaceBasis(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f))
    CubeFace.DOWN -> FaceBasis(Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, -1f))
}

/**
 * Returns the 3D rotation that brings [face] directly in front of the viewer (+Z)
 * with a gentle 3D perspective tilt so adjacent faces remain visible.
 */
private fun targetViewQuaternion(face: CubeFace): Quat {
    val faceToFront = when (face) {
        CubeFace.FRONT -> Quat(0f, 0f, 0f, 1f)
        CubeFace.RIGHT -> Quat.fromAxisAngle(0f, 1f, 0f, -90f)
        CubeFace.BACK -> Quat.fromAxisAngle(0f, 1f, 0f, -180f)
        CubeFace.LEFT -> Quat.fromAxisAngle(0f, 1f, 0f, 90f)
        CubeFace.UP -> Quat.fromAxisAngle(1f, 0f, 0f, 90f)
        CubeFace.DOWN -> Quat.fromAxisAngle(1f, 0f, 0f, -90f)
    }
    // Slight 3D isometric tilt so the user perceives cube depth clearly
    val tiltX = Quat.fromAxisAngle(1f, 0f, 0f, 15f)
    val tiltY = Quat.fromAxisAngle(0f, 1f, 0f, -20f)
    return (tiltX * tiltY * faceToFront).normalized()
}

/**
 * Interactive 3D guide Rubik's Cube rendered over the camera preview:
 * - Unscanned faces default to dark/black tiles so progress is visually clear.
 * - Smoothly rotates in 3D via quaternion SLERP toward [targetFace] so the user
 *   can rotate their physical cube along with the 3D model.
 * - Immediately paints each captured face with its detected [capturedFaces] colors.
 */
@Composable
fun Rubik3DGuideCube(
    targetFace: CubeFace,
    capturedFaces: Map<CubeFace, List<CubeColor>>,
    modifier: Modifier = Modifier
) {
    var fromQuat by remember { mutableStateOf(targetViewQuaternion(targetFace)) }
    var toQuat by remember { mutableStateOf(targetViewQuaternion(targetFace)) }
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(targetFace) {
        val current = Quat.slerp(fromQuat, toQuat, animProgress.value)
        fromQuat = current
        toQuat = targetViewQuaternion(targetFace)
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
    }

    val activeQuat = Quat.slerp(fromQuat, toQuat, animProgress.value)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val cubeHalfSize = minOf(size.width, size.height) * 0.29f
        val cameraDistance = 4.6f
        val focalScale = cubeHalfSize * (cameraDistance - 1.0f)

        fun project(p: Vec3): Offset {
            val denom = (cameraDistance - p.z).coerceAtLeast(0.5f)
            val s = focalScale / denom
            return Offset(cx + p.x * s, cy - p.y * s)
        }

        // Back-face cull & sort visible faces back-to-front
        val visibleFaces = CubeFace.entries.mapNotNull { face ->
            val basis = faceBasis(face)
            val rotNormal = activeQuat.rotate(basis.normal)
            if (rotNormal.z <= 0.02f) return@mapNotNull null
            Triple(face, basis, rotNormal)
        }.sortedBy { it.third.z }

        for ((face, basis, rotNormal) in visibleFaces) {
            val isTarget = face == targetFace
            val scannedColors = capturedFaces[face]

            // Draw dark plastic face backing plate
            val c0 = project(activeQuat.rotate(basis.normal + basis.right * -1f + basis.down * -1f))
            val c1 = project(activeQuat.rotate(basis.normal + basis.right * 1f + basis.down * -1f))
            val c2 = project(activeQuat.rotate(basis.normal + basis.right * 1f + basis.down * 1f))
            val c3 = project(activeQuat.rotate(basis.normal + basis.right * -1f + basis.down * 1f))

            val platePath = Path().apply {
                moveTo(c0.x, c0.y)
                lineTo(c1.x, c1.y)
                lineTo(c2.x, c2.y)
                lineTo(c3.x, c3.y)
                close()
            }
            drawPath(platePath, color = Color(0xFF090D16))

            // Subtle directional shading based on normal Z
            val shadeFactor = (0.72f + 0.28f * rotNormal.z).coerceIn(0.6f, 1f)
            val step = 2f / 3f
            val inset = 0.065f

            for (row in 0..2) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val u0 = -1f + col * step + inset
                    val u1 = -1f + (col + 1) * step - inset
                    val v0 = -1f + row * step + inset
                    val v1 = -1f + (row + 1) * step - inset

                    val p0 = project(activeQuat.rotate(basis.normal * 1.005f + basis.right * u0 + basis.down * v0))
                    val p1 = project(activeQuat.rotate(basis.normal * 1.005f + basis.right * u1 + basis.down * v0))
                    val p2 = project(activeQuat.rotate(basis.normal * 1.005f + basis.right * u1 + basis.down * v1))
                    val p3 = project(activeQuat.rotate(basis.normal * 1.005f + basis.right * u0 + basis.down * v1))

                    val tilePath = Path().apply {
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }

                    // Unscanned faces stay black/dark charcoal by default; scanned faces show real captured color
                    val baseColor = scannedColors?.getOrNull(idx)?.let { Color(it.argb) }
                        ?: Color(0xFF1A202C)
                    val shadedColor = Color(
                        red = (baseColor.red * shadeFactor).coerceIn(0f, 1f),
                        green = (baseColor.green * shadeFactor).coerceIn(0f, 1f),
                        blue = (baseColor.blue * shadeFactor).coerceIn(0f, 1f),
                        alpha = 1f
                    )

                    drawPath(tilePath, color = shadedColor)
                    drawPath(
                        tilePath,
                        color = if (scannedColors == null) Color(0xFF334155) else Color.Black.copy(alpha = 0.35f),
                        style = Stroke(width = 1.2f, join = StrokeJoin.Round)
                    )
                }
            }

            // Highlight active face border so user clearly sees which side should face the camera
            if (isTarget) {
                drawPath(
                    platePath,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = 3.2f, join = StrokeJoin.Round)
                )
            }
        }
    }
}
