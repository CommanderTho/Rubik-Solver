package com.rubik.solver.core.solver

import com.rubik.solver.core.model.Move
import com.rubik.solver.core.model.RubikCubeState
import com.rubik.solver.core.model.SolvabilityVerifier

sealed interface SolveResult {
    data class Success(
        val moves: List<Move>,
        val faceletString: String,
        val solveDurationMs: Long
    ) : SolveResult {
        val isAlreadySolved: Boolean get() = moves.isEmpty()
    }

    data class Failure(
        val errorMessageVi: String
    ) : SolveResult
}

interface RubikSolver : SolvabilityVerifier {
    suspend fun warmUp()
    suspend fun solve(
        state: RubikCubeState,
        maxSearchTimeMs: Long = DEFAULT_SEARCH_TIME_MS
    ): SolveResult
    suspend fun solveFacelets(
        faceletString: String,
        maxSearchTimeMs: Long = DEFAULT_SEARCH_TIME_MS
    ): SolveResult

    companion object {
        const val DEFAULT_SEARCH_TIME_MS = 2000L
        const val MIN_SEARCH_TIME_MS = 500L
        const val MAX_SEARCH_TIME_MS = 15000L
    }
}
