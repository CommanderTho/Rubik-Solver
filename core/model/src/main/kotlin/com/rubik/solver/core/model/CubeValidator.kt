package com.rubik.solver.core.model

enum class ValidationIssueType {
    INCOMPLETE_STICKERS,
    DUPLICATE_CENTER_COLORS,
    INVALID_COLOR_COUNT,
    UNSOLVABLE_CUBE_PARITY
}

data class ValidationIssue(
    val type: ValidationIssueType,
    val messageVi: String
)

data class ValidationResult(
    val isValid: Boolean,
    val filledCount: Int,
    val colorCounts: Map<CubeColor, Int>,
    val issues: List<ValidationIssue>,
    val faceletString: String? = null
) {
    val missingCount: Int get() = RubikCubeState.TOTAL_STICKERS - filledCount
}

/**
 * Functional interface for checking physical Rubik's Cube parity/orientation solvability
 * (e.g., via Kociemba/min2phase verification).
 */
fun interface SolvabilityVerifier {
    /**
     * Returns null if solvable, or a Vietnamese error description if physically impossible.
     */
    fun verify(faceletString: String): String?
}

/**
 * Real-time validator for [RubikCubeState].
 * Checks:
 * 1. Exactly 54 filled stickers (no nulls).
 * 2. All 6 center stickers (index 4) are unique colors.
 * 3. Every one of the 6 colors appears exactly 9 times.
 * 4. Optional physical solvability check (corner twist, edge flip, permutation parity).
 */
object CubeValidator {

    fun validate(
        state: RubikCubeState,
        solvabilityVerifier: SolvabilityVerifier? = null
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val filledCount = state.filledCount()
        val colorCounts = state.colorCounts()

        if (filledCount < RubikCubeState.TOTAL_STICKERS) {
            val missing = RubikCubeState.TOTAL_STICKERS - filledCount
            issues += ValidationIssue(
                type = ValidationIssueType.INCOMPLETE_STICKERS,
                messageVi = "Còn thiếu $missing ô màu ($filledCount/54 ô đã điền)."
            )
        }

        // Check center uniqueness
        val centerColors = CubeFace.entries.mapNotNull { face ->
            state.getSticker(face, RubikCubeState.CENTER_INDEX)?.let { face to it }
        }
        val duplicateCenters = centerColors
            .groupBy { it.second }
            .filter { it.value.size > 1 }
        if (duplicateCenters.isNotEmpty()) {
            val names = duplicateCenters.keys.joinToString(", ") { it.displayNameVi }
            issues += ValidationIssue(
                type = ValidationIssueType.DUPLICATE_CENTER_COLORS,
                messageVi = "Màu tâm (ô giữa) bị trùng: $names. Mỗi mặt phải có 1 màu tâm riêng."
            )
        }

        // Check color counts = 9 each
        val wrongCounts = CubeColor.entries.filter { color ->
            colorCounts.getValue(color) != RubikCubeState.STICKERS_PER_COLOR
        }
        if (wrongCounts.isNotEmpty()) {
            val detail = wrongCounts.joinToString(", ") { color ->
                "${color.displayNameVi}: ${colorCounts.getValue(color)}/9"
            }
            issues += ValidationIssue(
                type = ValidationIssueType.INVALID_COLOR_COUNT,
                messageVi = "Mỗi màu cần đúng 9 ô ($detail)."
            )
        }

        var faceletString: String? = null
        if (issues.isEmpty()) {
            val mapped = FaceletMapper.toFaceletString(state)
            if (mapped.isSuccess) {
                val candidate = mapped.getOrThrow()
                val parityError = solvabilityVerifier?.verify(candidate)
                if (parityError != null) {
                    issues += ValidationIssue(
                        type = ValidationIssueType.UNSOLVABLE_CUBE_PARITY,
                        messageVi = parityError
                    )
                } else {
                    faceletString = candidate
                }
            } else {
                issues += ValidationIssue(
                    type = ValidationIssueType.DUPLICATE_CENTER_COLORS,
                    messageVi = mapped.exceptionOrNull()?.message ?: "Không thể ánh xạ trạng thái."
                )
            }
        }

        return ValidationResult(
            isValid = issues.isEmpty(),
            filledCount = filledCount,
            colorCounts = colorCounts,
            issues = issues,
            faceletString = faceletString
        )
    }
}
