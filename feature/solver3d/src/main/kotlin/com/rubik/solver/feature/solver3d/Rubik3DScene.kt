package com.rubik.solver.feature.solver3d

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.Cubie
import com.rubik.solver.core.model.CubieCubeState
import com.rubik.solver.core.model.IntMatrix3
import com.rubik.solver.core.model.RotationAxis
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val CUBIE_SPACING = 0.34f
private const val CUBIE_BODY_SIZE = 0.318f

/**
 * Interactive 3D Rubik's Cube viewport built on SceneView (Google Filament).
 * Renders 26 individual cubie nodes and updates slice transforms smoothly on every VSync frame.
 */
@Composable
fun Rubik3DScene(
    uiState: Solver3DUiState,
    onFrameTick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    val latestState by rememberUpdatedState(uiState)
    val latestOnFrame by rememberUpdatedState(onFrameTick)

    val colorMaterials = remember(materialLoader) {
        buildStickerMaterials(materialLoader)
    }
    val bodyMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0xFF141821),
            metallic = 0.08f,
            roughness = 0.45f,
            reflectance = 0.35f
        )
    }

    val rootCubeNode = rememberNode(engine)
    val cubieNodesMap = remember(engine) { mutableMapOf<String, SceneNode>() }

    // Rebuild 26 cubie geometries only when a new initial cube state is loaded
    val initialCube = uiState.stepStates.firstOrNull()
    LaunchedEffect(initialCube, rootCubeNode) {
        if (initialCube == null) return@LaunchedEffect
        rebuildCubieHierarchy(
            engine = engine,
            rootNode = rootCubeNode,
            cubieNodesMap = cubieNodesMap,
            initialCube = initialCube,
            bodyMaterial = bodyMaterial,
            colorMaterials = colorMaterials
        )
        applyTransforms(
            cubieNodesMap = cubieNodesMap,
            cubeState = latestState.currentCubeState,
            activeAnimation = latestState.activeAnimation
        )
    }

    // Also sync immediately when discrete step changes via slider/reset while paused
    LaunchedEffect(uiState.currentStepIndex, uiState.activeAnimation == null) {
        applyTransforms(
            cubieNodesMap = cubieNodesMap,
            cubeState = uiState.currentCubeState,
            activeAnimation = uiState.activeAnimation
        )
    }

    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = Float3(1.55f, 1.25f, 1.85f),
        targetPosition = Float3(0f, 0f, 0f)
    )
    val cameraNode = rememberCameraNode(engine) {
        position = Float3(1.55f, 1.25f, 1.85f)
        lookAt(Float3(0f, 0f, 0f))
    }
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 110_000f
        position = Float3(3f, 4f, 4f)
    }

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        isOpaque = false,
        cameraNode = cameraNode,
        cameraManipulator = cameraManipulator,
        mainLightNode = mainLightNode,
        onFrame = { frameTimeNanos ->
            latestOnFrame(frameTimeNanos)
            applyTransforms(
                cubieNodesMap = cubieNodesMap,
                cubeState = latestState.currentCubeState,
                activeAnimation = latestState.activeAnimation
            )
        }
    ) {
        LightNode(
            type = LightManager.Type.DIRECTIONAL,
            intensity = 65_000f,
            position = Float3(-3f, -2f, -3f)
        )
        this.Node(
            apply = {
                if (rootCubeNode !in childNodes) {
                    addChildNode(rootCubeNode)
                }
            }
        )
    }
}

private fun buildStickerMaterials(materialLoader: MaterialLoader): Map<CubeColor, MaterialInstance> =
    CubeColor.entries.associateWith { color ->
        materialLoader.createColorInstance(
            color = Color(color.argb),
            metallic = 0.02f,
            roughness = 0.28f,
            reflectance = 0.55f
        )
    }

private fun rebuildCubieHierarchy(
    engine: Engine,
    rootNode: SceneNode,
    cubieNodesMap: MutableMap<String, SceneNode>,
    initialCube: CubieCubeState,
    bodyMaterial: MaterialInstance,
    colorMaterials: Map<CubeColor, MaterialInstance>
) {
    rootNode.clearChildNodes()
    cubieNodesMap.clear()

    val stickerSpan = CUBIE_BODY_SIZE * 0.82f
    val stickerThickness = 0.014f
    val offset = CUBIE_BODY_SIZE * 0.5f + stickerThickness * 0.45f

    for (cubie in initialCube.cubies) {
        val cubieNode = SceneNode(engine)
        val bodyNode = CubeNode(
            engine = engine,
            size = Float3(CUBIE_BODY_SIZE, CUBIE_BODY_SIZE, CUBIE_BODY_SIZE),
            center = Float3(0f, 0f, 0f),
            materialInstance = bodyMaterial
        )
        cubieNode.addChildNode(bodyNode)

        for ((face, color) in cubie.initialStickers) {
            val (size, center) = stickerGeometry(face, stickerSpan, stickerThickness, offset)
            val stickerNode = CubeNode(
                engine = engine,
                size = size,
                center = center,
                materialInstance = colorMaterials.getValue(color)
            )
            cubieNode.addChildNode(stickerNode)
        }

        rootNode.addChildNode(cubieNode)
        cubieNodesMap[cubie.id] = cubieNode
    }
}

private fun stickerGeometry(
    face: CubeFace,
    span: Float,
    thickness: Float,
    offset: Float
): Pair<Float3, Float3> = when (face) {
    CubeFace.UP -> Float3(span, thickness, span) to Float3(0f, offset, 0f)
    CubeFace.DOWN -> Float3(span, thickness, span) to Float3(0f, -offset, 0f)
    CubeFace.RIGHT -> Float3(thickness, span, span) to Float3(offset, 0f, 0f)
    CubeFace.LEFT -> Float3(thickness, span, span) to Float3(-offset, 0f, 0f)
    CubeFace.FRONT -> Float3(span, span, thickness) to Float3(0f, 0f, offset)
    CubeFace.BACK -> Float3(span, span, thickness) to Float3(0f, 0f, -offset)
}

private fun applyTransforms(
    cubieNodesMap: Map<String, SceneNode>,
    cubeState: CubieCubeState,
    activeAnimation: ActiveMoveAnimation?
) {
    if (cubieNodesMap.isEmpty()) return

    val sliceQuat = if (activeAnimation != null) {
        val eased = easeInOutCubic(activeAnimation.progress.coerceIn(0f, 1f))
        val angleDeg = activeAnimation.visualMove.angleDegrees * eased
        axisAngleQuaternion(activeAnimation.visualMove.axis, angleDeg)
    } else {
        null
    }

    for (cubie in cubeState.cubies) {
        val node = cubieNodesMap[cubie.id] ?: continue
        val basePos = Float3(
            cubie.x * CUBIE_SPACING,
            cubie.y * CUBIE_SPACING,
            cubie.z * CUBIE_SPACING
        )
        val baseQuat = matrixToQuaternion(cubie.orientation)

        if (activeAnimation != null && sliceQuat != null && cubeState.isInSlice(cubie, activeAnimation.logicalSliceMove)) {
            node.position = sliceQuat * basePos
            node.quaternion = normalize(sliceQuat * baseQuat)
        } else {
            node.position = basePos
            node.quaternion = baseQuat
        }
    }
}

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

private fun axisAngleQuaternion(axis: RotationAxis, angleDegrees: Float): Quaternion {
    val halfRad = ((angleDegrees * PI / 180.0) * 0.5).toFloat()
    val s = sin(halfRad)
    val c = cos(halfRad)
    return when (axis) {
        RotationAxis.X -> Quaternion(s, 0f, 0f, c)
        RotationAxis.Y -> Quaternion(0f, s, 0f, c)
        RotationAxis.Z -> Quaternion(0f, 0f, s, c)
    }
}

private fun matrixToQuaternion(m: IntMatrix3): Quaternion {
    val m00 = m.m00.toFloat()
    val m01 = m.m01.toFloat()
    val m02 = m.m02.toFloat()
    val m10 = m.m10.toFloat()
    val m11 = m.m11.toFloat()
    val m12 = m.m12.toFloat()
    val m20 = m.m20.toFloat()
    val m21 = m.m21.toFloat()
    val m22 = m.m22.toFloat()

    val trace = m00 + m11 + m22
    return if (trace > 0f) {
        val s = sqrt(trace + 1f) * 2f
        Quaternion(
            x = (m21 - m12) / s,
            y = (m02 - m20) / s,
            z = (m10 - m01) / s,
            w = 0.25f * s
        )
    } else if (m00 > m11 && m00 > m22) {
        val s = sqrt(1f + m00 - m11 - m22) * 2f
        Quaternion(
            x = 0.25f * s,
            y = (m01 + m10) / s,
            z = (m02 + m20) / s,
            w = (m21 - m12) / s
        )
    } else if (m11 > m22) {
        val s = sqrt(1f + m11 - m00 - m22) * 2f
        Quaternion(
            x = (m01 + m10) / s,
            y = 0.25f * s,
            z = (m12 + m21) / s,
            w = (m02 - m20) / s
        )
    } else {
        val s = sqrt(1f + m22 - m00 - m11) * 2f
        Quaternion(
            x = (m02 + m20) / s,
            y = (m12 + m21) / s,
            z = 0.25f * s,
            w = (m10 - m01) / s
        )
    }.let { normalize(it) }
}
