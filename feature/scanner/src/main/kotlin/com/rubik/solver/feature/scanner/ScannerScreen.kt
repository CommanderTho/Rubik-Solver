package com.rubik.solver.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rubik.solver.core.cv.OpenCvInitializer
import com.rubik.solver.core.cv.RubikFaceDetector
import com.rubik.solver.core.model.CubeColor
import com.rubik.solver.core.model.CubeFace
import com.rubik.solver.core.model.RubikCubeState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val OVERLAY_FRACTION = 0.64f

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onProceedToEditor: (RubikCubeState, Map<CubeFace, Set<Int>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        OpenCvInitializer.init()
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionGate(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onManualFallback = {
                onProceedToEditor(uiState.toRubikCubeState(), uiState.lowConfidenceByFace)
            },
            modifier = modifier
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { RubikFaceDetector() }
    val lastAnalyzedMs = remember { AtomicLong(0L) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            if (now - lastAnalyzedMs.get() >= 210L) {
                                lastAnalyzedMs.set(now)
                                try {
                                    val bmp = imageProxy.toBitmap()
                                    val scan = detector.detectFromBitmap(
                                        bitmap = bmp,
                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                                        overlaySizeFraction = OVERLAY_FRACTION
                                    )
                                    bmp.recycle()
                                    viewModel.onLiveFrameAnalyzed(scan)
                                } catch (t: Throwable) {
                                    Log.w("ScannerScreen", "Frame analysis skipped", t)
                                }
                            }
                            imageProxy.close()
                        }
                    }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )
                imageCapture = capture
                boundCamera = camera
            } catch (t: Throwable) {
                Log.e("ScannerScreen", "Camera bind failed", t)
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(uiState.isTorchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(uiState.isTorchEnabled)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        RubikOverlayGrid(
            previewColors = uiState.capturedFaces[uiState.currentFace] ?: uiState.livePreviewColors,
            isCaptured = uiState.currentFace in uiState.capturedFaces,
            modifier = Modifier.fillMaxSize()
        )

        // Top HUD: Interactive 3D Guide Cube + Rotation Prompt + Face Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color(0xCC0B1120),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 3D Guide Cube (starts all-black, rotates to guide, fills captured colors)
                    Rubik3DGuideCube(
                        targetFace = uiState.currentFace,
                        capturedFaces = uiState.capturedFaces,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.currentFace.displayNameVi} (${uiState.scannedCount}/6)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = uiState.rotationInstructionVi,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = uiState.currentFace.orientationHintVi,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }

                    IconButton(
                        onClick = viewModel::toggleTorch,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = if (uiState.isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Bật/tắt đèn flash",
                            tint = if (uiState.isTorchEnabled) Color(0xFFFACC15) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 6-Face Step Selector Strip (any face can be scanned in any order)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CubeFace.SCAN_ORDER.forEach { face ->
                    val scanned = face in uiState.capturedFaces
                    val selected = face == uiState.currentFace
                    FaceStatusChip(
                        face = face,
                        isScanned = scanned,
                        isSelected = selected,
                        onClick = { viewModel.selectFace(face) }
                    )
                }
            }

            // Adaptive CV warnings for low light / specular glare
            if (uiState.isLowLight || uiState.hasGlareRisk) {
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                uiState.hasGlareRisk -> "Phát hiện phản quang mạnh — nghiêng nhẹ khối Rubik để tránh chói."
                                else -> "Ánh sáng yếu — hãy bật đèn Flash hoặc kiểm tra lại màu sau khi chụp."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bottom Capture & Navigation Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            uiState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFFF87171),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { viewModel.clearFace(uiState.currentFace) },
                    enabled = uiState.currentFace in uiState.capturedFaces
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chụp lại")
                }

                FloatingActionButton(
                    onClick = {
                        val capture = imageCapture ?: return@FloatingActionButton
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val bmp = image.toBitmap()
                                        viewModel.captureFromBitmap(
                                            bitmap = bmp,
                                            rotationDegrees = image.imageInfo.rotationDegrees,
                                            overlayFraction = OVERLAY_FRACTION
                                        )
                                    } finally {
                                        image.close()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("ScannerScreen", "Capture failed", exception)
                                }
                            }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (uiState.isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Chụp mặt Rubik")
                    }
                }

                Button(
                    onClick = {
                        onProceedToEditor(uiState.toRubikCubeState(), uiState.lowConfidenceByFace)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.allFacesScanned) Color(0xFF16A34A) else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sửa (${uiState.scannedCount}/6)")
                }
            }
        }
    }
}

@Composable
private fun FaceStatusChip(
    face: CubeFace,
    isScanned: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isScanned -> Color(0xFF16A34A).copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.18f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isScanned) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = "${face.symbol}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RubikOverlayGrid(
    previewColors: List<CubeColor>?,
    isCaptured: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height) * OVERLAY_FRACTION
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f

        // Dim outside the 3x3 capture square
        val holePath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset(left, top), Size(side, side)),
                    cornerRadius = CornerRadius(18f, 18f)
                )
            )
        }
        clipPath(holePath, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.52f))
        }

        // Outer frame border
        val frameColor = if (isCaptured) Color(0xFF22C55E) else Color.White
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 4.dp.toPx())
        )

        // 3x3 inner grid lines
        val cell = side / 3f
        for (i in 1..2) {
            val x = left + i * cell
            val y = top + i * cell
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(x, top),
                end = Offset(x, top + side),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(left, y),
                end = Offset(left + side, y),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Live / captured color preview badges in each cell center
        if (previewColors != null && previewColors.size == 9) {
            for (row in 0..2) {
                for (col in 0..2) {
                    val color = Color(previewColors[row * 3 + col].argb)
                    val cx = left + col * cell + cell * 0.5f
                    val cy = top + row * cell + cell * 0.5f
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.45f),
                        radius = 12.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = color,
                        radius = 9.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionGate(
    onRequestPermission: () -> Unit,
    onManualFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Cần quyền Camera để quét 6 mặt Rubik",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Bạn cũng có thể nhập trực tiếp bảng màu 2D nếu thiết bị không có camera.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Cấp quyền Camera")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onManualFallback, modifier = Modifier.fillMaxWidth()) {
            Text("Chuyển sang Nhập màu Thủ công")
        }
    }
}
