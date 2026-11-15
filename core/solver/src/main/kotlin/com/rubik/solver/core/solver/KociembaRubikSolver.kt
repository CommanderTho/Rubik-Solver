package com.rubik.solver.core.solver

import com.rubik.solver.core.model.CubeValidator
import com.rubik.solver.core.model.Move
import com.rubik.solver.core.model.RubikCubeState
import cs.min2phase.Search
import cs.min2phase.Tools
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kociemba Two-Phase Algorithm implementation backed by Chen Shuang's `min2phase` engine.
 * Finds solutions typically <= 20 moves (HTM) in milliseconds once pruning tables are initialized.
 */
class KociembaRubikSolver(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RubikSolver {

    override suspend fun warmUp() {
        withContext(dispatcher) {
            if (!Search.isInited()) {
                Search.init()
            }
        }
    }

    override fun verify(faceletString: String): String? {
        if (faceletString.length != RubikCubeState.TOTAL_STICKERS) {
            return "Chuỗi trạng thái phải đủ 54 ký tự."
        }
        return when (val code = Tools.verify(faceletString)) {
            0 -> null
            -1 -> "Số lượng màu trên mỗi mặt không đồng đều (cần đúng 9 ô mỗi màu)."
            -2 -> "Thiếu hoặc sai định dạng cạnh (không đủ 12 cạnh hợp lệ)."
            -3 -> "Lỗi lật cạnh (edge parity): Có cạnh bị lật ngược không thể giải được."
            -4 -> "Thiếu hoặc sai định dạng góc (không đủ 8 góc hợp lệ)."
            -5 -> "Lỗi xoay góc (corner twist): Có góc bị xoay sai chiều."
            -6 -> "Lỗi hoán vị chẵn/lẻ (permutation parity): Hai viên bị đổi chỗ vật lý."
            else -> "Trạng thái Rubik không hợp lệ về mặt vật lý (mã lỗi $code)."
        }
    }

    override suspend fun solve(
        state: RubikCubeState,
        maxSearchTimeMs: Long
    ): SolveResult {
        val validation = CubeValidator.validate(state, solvabilityVerifier = this)
        if (!validation.isValid || validation.faceletString == null) {
            val message = validation.issues.firstOrNull()?.messageVi
                ?: "Trạng thái khối Rubik chưa hợp lệ."
            return SolveResult.Failure(message)
        }
        return solveFacelets(validation.faceletString!!, maxSearchTimeMs)
    }

    override suspend fun solveFacelets(
        faceletString: String,
        maxSearchTimeMs: Long
    ): SolveResult = withContext(dispatcher) {
        val verifyError = verify(faceletString)
        if (verifyError != null) {
            return@withContext SolveResult.Failure(verifyError)
        }

        if (isFaceletAlreadySolved(faceletString)) {
            return@withContext SolveResult.Success(
                moves = emptyList(),
                faceletString = faceletString,
                solveDurationMs = 0L
            )
        }

        val startTime = System.currentTimeMillis()
        if (!Search.isInited()) {
            Search.init()
        }

        val search = Search()
        // Provide probeMin to encourage finding shorter solutions on initial pass
        val probeMin = if (maxSearchTimeMs >= 3000L) 10_000L else 500L
        var raw = search.solution(faceletString, 21, 5_000_000L, probeMin, 0)
        if (raw.startsWith("Error 7") || raw.startsWith("Error 8")) {
            raw = search.solution(faceletString, 24, 10_000_000L, 0L, 0)
        }

        if (raw.startsWith("Error")) {
            return@withContext SolveResult.Failure(mapSolverError(raw))
        }

        var bestMoves = Move.parseSequence(raw)
        var bestRaw = raw

        // Iteratively search for strictly shorter solutions within time budget
        val sliceProbeMax = 500_000L
        val sliceProbeMin = 100L

        while (System.currentTimeMillis() - startTime < maxSearchTimeMs && bestMoves.size > 18) {
            val remainingMs = maxSearchTimeMs - (System.currentTimeMillis() - startTime)
            if (remainingMs < 50) break

            val nextRaw = search.next(sliceProbeMax, sliceProbeMin, 0)
            if (nextRaw.startsWith("Error 7")) {
                // No shorter solution exists in phase 1 search tree
                break
            }
            if (nextRaw.startsWith("Error")) {
                continue
            }
            val candidate = Move.parseSequence(nextRaw)
            if (candidate.isNotEmpty() && candidate.size < bestMoves.size) {
                bestMoves = candidate
                bestRaw = nextRaw
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        SolveResult.Success(
            moves = bestMoves,
            faceletString = faceletString,
            solveDurationMs = durationMs
        )
    }

    private fun isFaceletAlreadySolved(facelets: String): Boolean {
        for (faceIdx in 0 until 6) {
            val start = faceIdx * 9
            val center = facelets[start]
            for (offset in 1 until 9) {
                if (facelets[start + offset] != center) return false
            }
        }
        return true
    }

    private fun mapSolverError(rawError: String): String = when {
        rawError.contains("Error 1") -> "Không đủ đúng 9 ô cho mỗi màu."
        rawError.contains("Error 2") -> "Không tìm thấy đủ 12 cạnh hợp lệ."
        rawError.contains("Error 3") -> "Lỗi định hướng cạnh (edge flip parity)."
        rawError.contains("Error 4") -> "Không tìm thấy đủ 8 góc hợp lệ."
        rawError.contains("Error 5") -> "Lỗi xoay góc (corner twist parity)."
        rawError.contains("Error 6") -> "Lỗi hoán vị chẵn/lẻ (parity error)."
        rawError.contains("Error 7") -> "Quá giới hạn độ sâu tìm kiếm."
        rawError.contains("Error 8") -> "Hết thời gian tìm lời giải, vui lòng thử lại."
        else -> "Không thể giải khối Rubik ($rawError)."
    }
}
