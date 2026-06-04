package com.lemon.echo.ui

import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemon.echo.scanner.BarcodeAnalyzer
import com.lemon.echo.scanner.ScanResult
import java.util.concurrent.Executors

/**
 * Camera preview with tap-to-focus, scan overlay, and focus indicator.
 * Manages CameraX lifecycle internally and reports barcode detections via [onBarcodeDetected].
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isScanning: Boolean,
    isTorchOn: Boolean,
    onBarcodeDetected: (ScanResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera state
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Remember updated state for DisposableEffect lambda capture
    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)

    // Focus indicator state
    var focusEvent by remember { mutableLongStateOf(0L) }
    var focusX by remember { mutableFloatStateOf(0f) }
    var focusY by remember { mutableFloatStateOf(0f) }

    // --- Camera setup ---
    DisposableEffect(lifecycleOwner, isScanning) {
        if (!isScanning) return@DisposableEffect onDispose {}

        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .build()
            .also {
                previewView?.let { view ->
                    it.setSurfaceProvider(view.surfaceProvider)
                }
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val analyzer = BarcodeAnalyzer { result ->
            if (isScanning) {
                currentOnBarcodeDetected(result)
            }
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        try {
            cameraProvider.unbindAll()
            val cam = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            cam.cameraControl.enableTorch(isTorchOn)
            camera = cam
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    // React to torch toggle while camera is running
    LaunchedEffect(isTorchOn) {
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    // --- UI ---
    Box(modifier = modifier) {
        // Camera preview with tap-to-focus
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = view

                    view.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            camera?.let { cam ->
                                val factory = view.meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                cam.cameraControl.startFocusAndMetering(action)
                            }
                            focusX = event.x
                            focusY = event.y
                            focusEvent = System.currentTimeMillis()
                            view.performClick()
                            true
                        } else false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scan overlay (dark edges + scan frame + animated scan line)
        ScanOverlay(isScanning = isScanning)

        // Tap-to-focus visual indicator
        FocusIndicator(
            trigger = focusEvent,
            x = focusX,
            y = focusY
        )
    }
}

// ====== Corner Brackets (reusable) ======

/** Draws four L-shaped corner brackets using a Canvas. */
@Composable
internal fun CornerBrackets(
    modifier: Modifier = Modifier,
    cornerLength: Dp,
    strokeWidth: Dp,
    color: Color
) {
    Canvas(modifier = modifier) {
        val cLen = cornerLength.toPx()
        val sw = strokeWidth.toPx()
        val path = Path().apply {
            // Top-left
            moveTo(0f, cLen)
            lineTo(0f, 0f)
            lineTo(cLen, 0f)
            // Top-right
            moveTo(size.width - cLen, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, cLen)
            // Bottom-right
            moveTo(size.width, size.height - cLen)
            lineTo(size.width, size.height)
            lineTo(size.width - cLen, size.height)
            // Bottom-left
            moveTo(cLen, size.height)
            lineTo(0f, size.height)
            lineTo(0f, size.height - cLen)
        }
        drawPath(path, color = color, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

// ====== Scan Overlay ======

@Composable
internal fun ScanOverlay(isScanning: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent dark background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // Clear scanning frame with corner brackets
        val frameSize = 260.dp
        val cornerLen = 28.dp  // length of each corner arm
        val strokeWidth = 3.dp
        val cornerColor = if (isScanning) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.6f)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(frameSize)
        ) {
            // Four corner brackets
            CornerBrackets(
                modifier = Modifier.fillMaxSize(),
                cornerLength = cornerLen,
                strokeWidth = strokeWidth,
                color = cornerColor
            )

            // Animated scan line — radar-sweep style up and down
            if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
                val scanLineOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanLineOffset"
                )

                // lineY ranges from 2.dp to frameSize-2.dp
                val lineY = 2.dp + (frameSize - 4.dp) * scanLineOffset
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .offset(y = lineY)
                        .padding(horizontal = 6.dp)
                        .background(Color(0xFF4FC3F7), RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

// ====== Tap-to-Focus Visual Indicator ======

@Composable
internal fun FocusIndicator(trigger: Long, x: Float, y: Float) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger != 0L) {
            shown = true
            kotlinx.coroutines.delay(500)
            shown = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(400),
        label = "focusAlpha"
    )

    if (alpha > 0f) {
        val indicatorSize = 60.dp
        val cornerLen = 14.dp
        val strokeWidth = 2.dp
        val indicatorColor = Color(0xFF4FC3F7).copy(alpha = alpha)

        Box(
            modifier = Modifier
                .offset { IntOffset(x.toInt() - (indicatorSize / 2).roundToPx(), y.toInt() - (indicatorSize / 2).roundToPx()) }
                .size(indicatorSize)
        ) {
            CornerBrackets(
                modifier = Modifier.fillMaxSize(),
                cornerLength = cornerLen,
                strokeWidth = strokeWidth,
                color = indicatorColor
            )
        }
    }
}
