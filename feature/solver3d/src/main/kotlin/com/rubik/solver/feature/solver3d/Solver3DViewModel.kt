package com.rubik.solver.feature.solver3d

import androidx.lifecycle.ViewModel
import com.rubik.solver.core.model.CubieCubeState
import com.rubik.solver.core.model.Move
import com.rubik.solver.core.model.RubikCubeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActiveMoveAnimation(
    val logicalSliceMove: Move,
    val visualMove: Move,
    val targetStepIndex: Int,
    val progress: Float = 0f
)

data class Solver3DUiState(
    val stepStates: List<CubieCubeState> = listOf(CubieCubeState.solved()),
    val moves: List<Move> = emptyList(),
    val solveDurationMs: Long = 0L,
    val currentStepIndex: Int = 0,
    val activeAnimation: ActiveMoveAnimation? = null,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f
) {
    val totalSteps: Int get() = moves.size
    val currentCubeState: CubieCubeState get() = stepStates[currentStepIndex.coerceIn(0, stepStates.lastIndex)]
    val isAtStart: Boolean get() = currentStepIndex == 0 && activeAnimation == null
    val isAtEnd: Boolean get() = currentStepIndex >= moves.size && activeAnimation == null
    val highlightedStepNumber: Int?
        get() = when {
            activeAnimation != null -> maxOf(currentStepIndex, activeAnimation.targetStepIndex)
            currentStepIndex in 1..moves.size -> currentStepIndex
            else -> null
        }
    val currentMove: Move?
        get() = highlightedStepNumber?.let { moves.getOrNull(it - 1) }
    val nextMoveToExecute: Move? get() = moves.getOrNull(currentStepIndex)
}

/**
 * Synchronizes discrete 26-cubie logical state with smooth 60+ FPS VSync rotation animation
 * and player controls (Play/Pause, Next, Previous, Reset, Timeline Scrubbing).
 */
class Solver3DViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(Solver3DUiState())
    val uiState: StateFlow<Solver3DUiState> = _uiState.asStateFlow()

    private var lastFrameNanos: Long = 0L

    fun loadSolution(
        initialState: RubikCubeState,
        moves: List<Move>,
        solveDurationMs: Long = 0L
    ) {
        val initialCubie = CubieCubeState.fromRubikCubeState(initialState)
        val precomputed = ArrayList<CubieCubeState>(moves.size + 1)
        var cursor = initialCubie
        precomputed += cursor
        for (move in moves) {
            cursor = cursor.applyMove(move)
            precomputed += cursor
        }

        lastFrameNanos = 0L
        _uiState.value = Solver3DUiState(
            stepStates = precomputed,
            moves = moves,
            solveDurationMs = solveDurationMs,
            currentStepIndex = 0,
            activeAnimation = null,
            isPlaying = false,
            playbackSpeed = _uiState.value.playbackSpeed
        )
    }

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.moves.isEmpty()) return

        if (state.isPlaying) {
            _uiState.update { it.copy(isPlaying = false) }
            return
        }

        // If at the end, restart from step 0 automatically when Play is pressed
        val resetToStart = state.isAtEnd
        val startIndex = if (resetToStart) 0 else state.currentStepIndex
        val nextMove = state.moves.getOrNull(startIndex) ?: return
        val anim = state.activeAnimation ?: ActiveMoveAnimation(
            logicalSliceMove = nextMove,
            visualMove = nextMove,
            targetStepIndex = startIndex + 1,
            progress = 0f
        )

        lastFrameNanos = 0L
        _uiState.update {
            it.copy(
                currentStepIndex = startIndex,
                activeAnimation = anim,
                isPlaying = true
            )
        }
    }

    fun nextStep() {
        val state = _uiState.value
        if (state.moves.isEmpty()) return

        // If currently animating, snap to completion first so rapid taps stay crisp
        val baseIndex = if (state.activeAnimation != null) {
            state.activeAnimation.targetStepIndex
        } else {
            state.currentStepIndex
        }
        if (baseIndex >= state.moves.size) {
            _uiState.update { it.copy(currentStepIndex = state.moves.size, activeAnimation = null, isPlaying = false) }
            return
        }

        val move = state.moves[baseIndex]
        lastFrameNanos = 0L
        _uiState.update {
            it.copy(
                currentStepIndex = baseIndex,
                isPlaying = false,
                activeAnimation = ActiveMoveAnimation(
                    logicalSliceMove = move,
                    visualMove = move,
                    targetStepIndex = baseIndex + 1,
                    progress = 0f
                )
            )
        }
    }

    fun previousStep() {
        val state = _uiState.value
        if (state.moves.isEmpty()) return

        val baseIndex = if (state.activeAnimation != null) {
            state.activeAnimation.targetStepIndex
        } else {
            state.currentStepIndex
        }
        if (baseIndex <= 0) {
            _uiState.update { it.copy(currentStepIndex = 0, activeAnimation = null, isPlaying = false) }
            return
        }

        val moveToUndo = state.moves[baseIndex - 1]
        lastFrameNanos = 0L
        _uiState.update {
            it.copy(
                currentStepIndex = baseIndex,
                isPlaying = false,
                activeAnimation = ActiveMoveAnimation(
                    logicalSliceMove = moveToUndo,
                    visualMove = moveToUndo.inverse(),
                    targetStepIndex = baseIndex - 1,
                    progress = 0f
                )
            )
        }
    }

    fun reset() {
        lastFrameNanos = 0L
        _uiState.update {
            it.copy(
                currentStepIndex = 0,
                activeAnimation = null,
                isPlaying = false
            )
        }
    }

    fun seekToStep(stepIndex: Int) {
        val state = _uiState.value
        val clamped = stepIndex.coerceIn(0, state.moves.size)
        lastFrameNanos = 0L
        _uiState.update {
            it.copy(
                currentStepIndex = clamped,
                activeAnimation = null,
                isPlaying = false
            )
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        _uiState.update { state ->
            val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - state.playbackSpeed) < 0.05f }
            val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
            state.copy(playbackSpeed = nextSpeed)
        }
    }

    /**
     * Called directly from SceneView's VSync `onFrame` callback to advance active animations at 60+ FPS.
     */
    fun onFrame(frameTimeNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos
            return
        }
        val deltaSeconds = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = frameTimeNanos
        advanceAnimation(deltaSeconds)
    }

    internal fun advanceAnimation(deltaSeconds: Float) {
        val state = _uiState.value
        val anim = state.activeAnimation ?: return

        val baseDuration = if (kotlin.math.abs(anim.visualMove.angleDegrees) > 100f) 0.52f else 0.36f
        val duration = (baseDuration / state.playbackSpeed).coerceAtLeast(0.12f)
        val nextProgress = anim.progress + (deltaSeconds / duration)

        if (nextProgress < 1.0f) {
            _uiState.update { current ->
                val active = current.activeAnimation ?: return@update current
                current.copy(activeAnimation = active.copy(progress = nextProgress))
            }
            return
        }

        // Move completed: commit discrete state and queue next move if auto-playing
        val completedStep = anim.targetStepIndex
        val canContinue = state.isPlaying && completedStep < state.moves.size
        val nextAnim = if (canContinue) {
            val nextMove = state.moves[completedStep]
            ActiveMoveAnimation(
                logicalSliceMove = nextMove,
                visualMove = nextMove,
                targetStepIndex = completedStep + 1,
                progress = 0f
            )
        } else {
            null
        }

        _uiState.update { current ->
            current.copy(
                currentStepIndex = completedStep,
                activeAnimation = nextAnim,
                isPlaying = canContinue
            )
        }
    }
}
