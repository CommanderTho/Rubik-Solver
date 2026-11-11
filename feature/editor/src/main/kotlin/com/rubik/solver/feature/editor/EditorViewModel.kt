package com.rubik.solver.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.CubeValidator
import com.rubik.solver.core.model.CubieCubeState
import com.rubik.solver.core.model.Move
import com.rubik.solver.core.model.RubikCubeState
import com.rubik.solver.core.model.ValidationResult
import com.rubik.solver.core.solver.KociembaRubikSolver
import com.rubik.solver.core.solver.RubikSolver
import com.rubik.solver.core.solver.SolveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val cubeState: RubikCubeState = RubikCubeState(),
    val selectedFace: CubeFace = CubeFace.FRONT,
    val activePaletteColor: CubeColor = CubeColor.WHITE,
    val lowConfidenceByFace: Map<CubeFace, Set<Int>> = emptyMap(),
    val validationResult: ValidationResult = CubeValidator.validate(RubikCubeState()),
    val isSolving: Boolean = false,
    val solveError: String? = null
) {
    val canSolve: Boolean get() = validationResult.isValid && !isSolving
}

class EditorViewModel(
    private val solver: RubikSolver = KociembaRubikSolver()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            solver.warmUp()
        }
    }

    fun loadState(
        state: RubikCubeState,
        lowConfidence: Map<CubeFace, Set<Int>> = emptyMap()
    ) {
        val firstNeedsAttention = CubeFace.SCAN_ORDER.firstOrNull { !state.isFaceScanned(it) }
            ?: lowConfidence.entries.firstOrNull { it.value.isNotEmpty() }?.key
            ?: CubeFace.FRONT

        val validation = CubeValidator.validate(state, solvabilityVerifier = solver)
        _uiState.value = EditorUiState(
            cubeState = state,
            selectedFace = firstNeedsAttention,
            activePaletteColor = _uiState.value.activePaletteColor,
            lowConfidenceByFace = lowConfidence,
            validationResult = validation
        )
    }

    fun selectFace(face: CubeFace) {
        _uiState.update { it.copy(selectedFace = face, solveError = null) }
    }

    fun selectPaletteColor(color: CubeColor) {
        _uiState.update { it.copy(activePaletteColor = color, solveError = null) }
    }

    fun paintSticker(face: CubeFace, index: Int) {
        val current = _uiState.value
        val updatedState = current.cubeState.withSticker(face, index, current.activePaletteColor)
        val updatedLowConf = current.lowConfidenceByFace.toMutableMap()
        updatedLowConf[face] = (updatedLowConf[face].orEmpty()) - index

        val validation = CubeValidator.validate(updatedState, solvabilityVerifier = solver)
        _uiState.update {
            it.copy(
                cubeState = updatedState,
                lowConfidenceByFace = updatedLowConf,
                validationResult = validation,
                solveError = null
            )
        }
    }

    fun loadSampleScramble() {
        val scramble = Move.parseSequence("R U R' U' F2 D' L B2 U R2 D' F'")
        val scrambled = CubieCubeState.solved().applyMoves(scramble).toRubikCubeState()
        loadState(scrambled, emptyMap())
    }

    fun resetSolved() {
        loadState(RubikCubeState.solved(), emptyMap())
    }

    fun clearAll() {
        loadState(RubikCubeState(), emptyMap())
    }

    fun solveCube(onSolved: (RubikCubeState, SolveResult.Success) -> Unit) {
        val snapshot = _uiState.value
        if (!snapshot.canSolve) return

        _uiState.update { it.copy(isSolving = true, solveError = null) }
        viewModelScope.launch {
            when (val result = solver.solve(snapshot.cubeState)) {
                is SolveResult.Success -> {
                    _uiState.update { it.copy(isSolving = false) }
                    onSolved(snapshot.cubeState, result)
                }
                is SolveResult.Failure -> {
                    _uiState.update {
                        it.copy(isSolving = false, solveError = result.errorMessageVi)
                    }
                }
            }
        }
    }
}
