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

    override suspend fun solve(state: RubikCubeState): SolveResult {
        val validation = CubeValidator.validate(state, solvabilityVerifier = this)
        if (!validation.isValid || validation.faceletString == null) {
            val message = validation.issues.firstOrNull()?.messageVi
                ?: "Trạng thái khối Rubik chưa hợp lệ."
            return SolveResult.Failure(message)
        }
        return solveFacelets(validation.faceletString!!)
    }

    override suspend fun solveFacelets(faceletString: String): SolveResult = withContext(dispatcher) {
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

        // Request <= 20 moves first with generous probe budget; fall back to 21 if needed.
        val search = Search()
        var raw = search.solution(faceletString, 20, 5_000_000L, 500L, 0)
        if (raw.startsWith("Error 7") || raw.startsWith("Error 8")) {
            raw = search.solution(faceletString, 21, 10_000_000L, 0L, 0)
        }
        val durationMs = System.currentTimeMillis() - startTime

        if (raw.startsWith("Error")) {
            return@withContext SolveResult.Failure(mapSolverError(raw))
        }

        val moves = Move.parseSequence(raw)
        SolveResult.Success(
            moves = moves,
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
