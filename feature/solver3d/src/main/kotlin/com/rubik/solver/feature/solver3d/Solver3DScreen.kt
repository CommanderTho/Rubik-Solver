package com.rubik.solver.feature.solver3d

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Solver3DScreen(
    viewModel: Solver3DViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mô phỏng Lời giải 3D")
                        Text(
                            text = if (uiState.totalSteps == 0) {
                                "Khối Rubik đã ở trạng thái giải!"
                            } else {
                                "Tổng ${uiState.totalSteps} bước • Kociemba (${uiState.solveDurationMs} ms)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::cyclePlaybackSpeed) {
                        Text(
                            text = "${uiState.playbackSpeed}x",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 3D Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                        )
                    )
            ) {
                Rubik3DScene(
                    uiState = uiState,
                    onFrameTick = viewModel::onFrame,
                    modifier = Modifier.fillMaxSize()
                )

                // Current Move Banner Overlay
                StepInstructionBanner(
                    uiState = uiState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                )

                // Gesture hint
                Text(
                    text = "Kéo để xoay góc nhìn • Chụm để phóng to/thu nhỏ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            // Player Controller Panel
            PlayerControllerPanel(
                uiState = uiState,
                onSeek = viewModel::seekToStep,
                onReset = viewModel::reset,
                onPrevious = viewModel::previousStep,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::nextStep
            )
        }
    }
}

@Composable
private fun StepInstructionBanner(
    uiState: Solver3DUiState,
    modifier: Modifier = Modifier
) {
    val activeMove = uiState.currentMove
    val isSolvedBanner = uiState.isAtEnd && (uiState.totalSteps == 0 || uiState.currentStepIndex == uiState.totalSteps)

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSolvedBanner && uiState.currentStepIndex == uiState.totalSteps) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF22C55E)
                )
                Text(
                    text = "Hoàn thành! Khối Rubik đã được giải.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            } else if (activeMove != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeMove.notation,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "Bước ${uiState.currentStepIndex.coerceAtLeast(1)} / ${uiState.totalSteps}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = activeMove.descriptionVi,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = "Bước tiếp theo: ${uiState.nextMoveToExecute?.notation ?: "—"} • Nhấn Play để bắt đầu",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PlayerControllerPanel(
    uiState: Solver3DUiState,
    onSeek: (Int) -> Unit,
    onReset: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Scrollable Move Chips Strip
            if (uiState.moves.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.moves.forEachIndexed { idx, move ->
                        val stepNumber = idx + 1
                        val isCurrent = stepNumber == uiState.currentStepIndex ||
                            uiState.activeAnimation?.targetStepIndex == stepNumber
                        val isDone = stepNumber <= uiState.currentStepIndex

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        isDone -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 0.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSeek(stepNumber) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = move.notation,
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.onPrimary
                                    isDone -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Timeline Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tiến trình",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${uiState.currentStepIndex} / ${uiState.totalSteps}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = uiState.currentStepIndex.toFloat(),
                    onValueChange = { onSeek(it.roundToInt()) },
                    valueRange = 0f..uiState.totalSteps.coerceAtLeast(1).toFloat(),
                    steps = (uiState.totalSteps - 1).coerceAtLeast(0),
                    enabled = uiState.totalSteps > 0
                )
            }

            // Transport Buttons: Reset, Prev, Play/Pause, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onReset,
                    enabled = !uiState.isAtStart
                ) {
                    Icon(Icons.Default.Replay, contentDescription = "Đặt lại từ đầu")
                }

                FilledIconButton(
                    onClick = onPrevious,
                    enabled = uiState.currentStepIndex > 0 || uiState.activeAnimation != null
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Bước trước")
                }

                FloatingActionButton(
                    onClick = onPlayPause,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Tạm dừng" else "Phát tự động"
                    )
                }

                FilledIconButton(
                    onClick = onNext,
                    enabled = !uiState.isAtEnd
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Bước tiếp theo")
                }

                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
