package com.rubik.solver.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.RubikCubeState
import com.rubik.solver.core.solver.SolveResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBackToScanner: () -> Unit,
    onNavigateToSolver3D: (RubikCubeState, SolveResult.Success) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Kiểm tra & Sửa màu 2D") },
                navigationIcon = {
                    IconButton(onClick = onBackToScanner) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại quét")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadSampleScramble) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Tạo trạng thái mẫu")
                    }
                    IconButton(onClick = viewModel::clearAll) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Xóa tất cả")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.solveError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = { viewModel.solveCube(onNavigateToSolver3D) },
                    enabled = uiState.canSolve,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState.isSolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Đang tìm lời giải Kociemba...")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (uiState.validationResult.isValid) {
                                "Giải Rubik & Xem 3D"
                            } else {
                                "Chưa hợp lệ (${uiState.validationResult.filledCount}/54 ô)"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ValidationBanner(uiState = uiState)

            // 2D Unfolded Cube Net (T/Cross layout)
            Text(
                text = "Sơ đồ 6 mặt (Chạm vào một mặt để phóng to & sửa)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            UnfoldedCubeNet(
                state = uiState.cubeState,
                selectedFace = uiState.selectedFace,
                lowConfidenceByFace = uiState.lowConfidenceByFace,
                onSelectFace = viewModel::selectFace
            )

            // Zoomed 3x3 Grid for Selected Face
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chỉnh sửa: ${uiState.selectedFace.displayNameVi}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = viewModel::resetSolved) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mặc định đã giải", style = MaterialTheme.typography.labelMedium)
                }
            }

            ZoomedFaceGrid(
                face = uiState.selectedFace,
                stickers = uiState.cubeState.getFace(uiState.selectedFace),
                lowConfidenceIndices = uiState.lowConfidenceByFace[uiState.selectedFace].orEmpty(),
                onTapSticker = { index -> viewModel.paintSticker(uiState.selectedFace, index) },
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally)
            )

            // 6-Color Palette with real-time x/9 counters
            Text(
                text = "Bảng màu mẫu (Chọn màu rồi chạm vào ô cần sửa)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPaletteBar(
                activeColor = uiState.activePaletteColor,
                colorCounts = uiState.validationResult.colorCounts,
                onSelectColor = viewModel::selectPaletteColor
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ValidationBanner(uiState: EditorUiState) {
    val validation = uiState.validationResult
    val containerColor = if (validation.isValid) {
        Color(0xFF16A34A).copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
    }
    val contentColor = if (validation.isValid) {
        Color(0xFF15803D)
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (validation.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = contentColor
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (validation.isValid) {
                        "Trạng thái hợp lệ (54/54 ô, mỗi màu đúng 9 ô & đủ điều kiện giải)."
                    } else {
                        "Chưa thể giải — vui lòng kiểm tra lại:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                validation.issues.forEach { issue ->
                    Text(
                        text = "• ${issue.messageVi}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun UnfoldedCubeNet(
    state: RubikCubeState,
    selectedFace: CubeFace,
    lowConfidenceByFace: Map<CubeFace, Set<Int>>,
    onSelectFace: (CubeFace) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: [Empty] [UP] [Empty] [Empty]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Spacer(Modifier.weight(1f))
            MiniFaceCell(
                face = CubeFace.UP,
                stickers = state.getFace(CubeFace.UP),
                isSelected = selectedFace == CubeFace.UP,
                hasWarning = lowConfidenceByFace[CubeFace.UP].orEmpty().isNotEmpty(),
                onClick = { onSelectFace(CubeFace.UP) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }

        // Row 2: [LEFT] [FRONT] [RIGHT] [BACK]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(CubeFace.LEFT, CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK).forEach { face ->
                MiniFaceCell(
                    face = face,
                    stickers = state.getFace(face),
                    isSelected = selectedFace == face,
                    hasWarning = lowConfidenceByFace[face].orEmpty().isNotEmpty(),
                    onClick = { onSelectFace(face) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 3: [Empty] [DOWN] [Empty] [Empty]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Spacer(Modifier.weight(1f))
            MiniFaceCell(
                face = CubeFace.DOWN,
                stickers = state.getFace(CubeFace.DOWN),
                isSelected = selectedFace == CubeFace.DOWN,
                hasWarning = lowConfidenceByFace[CubeFace.DOWN].orEmpty().isNotEmpty(),
                onClick = { onSelectFace(CubeFace.DOWN) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniFaceCell(
    face: CubeFace,
    stickers: List<CubeColor?>,
    isSelected: Boolean,
    hasWarning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        hasWarning -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (isSelected) 2.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val color = stickers[idx]?.let { Color(it.argb) } ?: Color(0xFF475569)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (idx == RubikCubeState.CENTER_INDEX) {
                            Text(
                                text = face.symbol.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomedFaceGrid(
    face: CubeFace,
    stickers: List<CubeColor?>,
    lowConfidenceIndices: Set<Int>,
    onTapSticker: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0..2) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val stickerColor = stickers[idx]
                    val bg = stickerColor?.let { Color(it.argb) } ?: Color(0xFF334155)
                    val isUncertain = idx in lowConfidenceIndices
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(
                                width = if (isUncertain) 3.dp else 1.dp,
                                color = if (isUncertain) Color(0xFFF59E0B) else Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onTapSticker(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (idx == RubikCubeState.CENTER_INDEX) {
                            Text(
                                text = "Tâm ${face.symbol}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        } else if (stickerColor == null) {
                            Text(
                                text = "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteBar(
    activeColor: CubeColor,
    colorCounts: Map<CubeColor, Int>,
    onSelectColor: (CubeColor) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CubeColor.entries.forEach { color ->
            val count = colorCounts.getValue(color)
            val isSelected = color == activeColor
            val isCountExact = count == RubikCubeState.STICKERS_PER_COLOR
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectColor(color) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color.argb))
                        .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                )
                Text(
                    text = color.displayNameVi,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    text = "$count/9",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCountExact -> Color(0xFF16A34A)
                        count > 9 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
