package com.lemon.echo.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.widget.Toast
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemon.echo.scanner.BarcodeAnalyzer
import com.lemon.echo.scanner.ScanHistoryItem
import com.lemon.echo.scanner.ScanHistoryManager
import com.lemon.echo.scanner.ScanResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyManager = remember { ScanHistoryManager(context) }

    // Scan state
    var isScanning by remember { mutableStateOf(true) }
    var detectedResult by remember { mutableStateOf<ScanResult?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // Camera state
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Focus indicator state (pixel coords from touch event)
    var focusEvent by remember { mutableStateOf(0L) }   // incremented on each tap for focus
    var focusX by remember { mutableStateOf(0f) }
    var focusY by remember { mutableStateOf(0f) }

    // Camera provider
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Set up camera when scanning
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
                isScanning = false
                detectedResult = result
                showResult = true
                historyManager.addToHistory(result)
                triggerFeedback(context)
                if (historyManager.autoCopyEnabled) {
                    copyToClipboard(context, result.rawText, showToast = false)
                }
            }
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    // --- helper functions ---

    fun toggleTorch() {
        isTorchOn = !isTorchOn
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    fun scanAgain() {
        showResult = false
        detectedResult = null
        isScanning = true
    }

    // --- UI ---

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with tap-to-focus
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = view

                    // Tap to focus
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
                            true
                        } else false
                    }
                    // Pinch-to-zoom enabled by default on CameraX 1.4+
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scan overlay (dark edges + scan frame + animated scan line)
        ScanOverlay(isScanning = isScanning && !showResult)

        // Tap-to-focus visual indicator
        FocusIndicator(
            trigger = focusEvent,
            x = focusX,
            y = focusY
        )

        // Torch button (top-right)
        IconButton(
            onClick = { toggleTorch() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (isTorchOn) Color(0xFFFFD700) else Color.White
            )
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isTorchOn) "关闭手电筒" else "打开手电筒"
            )
        }

        // History button (top-left)
        IconButton(
            onClick = { showHistory = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "扫描历史"
            )
        }

        // Scanning hint (bottom)
        Text(
            text = "将二维码对准框内",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }

    // Result bottom sheet
    if (showResult && detectedResult != null) {
        val result = detectedResult!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { scanAgain() },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ResultContent(
                result = result,
                autoCopyEnabled = historyManager.autoCopyEnabled,
                onToggleAutoCopy = { historyManager.autoCopyEnabled = it },
                onCopy = { copyToClipboard(context, result.rawText) },
                onShare = { shareText(context, result.rawText) },
                onOpenUrl = { openUrl(context, result.rawText) },
                onScanAgain = { scanAgain() }
            )
        }
    }

    // History bottom sheet
    if (showHistory) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            HistoryContent(
                history = historyManager.history,
                onClear = {
                    historyManager.clearHistory()
                    showHistory = false
                },
                onDismiss = { showHistory = false }
            )
        }
    }
}

// ====== Feedback ======

private fun triggerFeedback(context: Context) {
    // Ensure on main thread for vibration
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post { triggerFeedback(context) }
        return
    }

    // === Vibration — try multiple approaches ===
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+ with VibrationAttributes for better compatibility
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST)
                    .build()
                v.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE),
                    attrs
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(120, 128))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(120)
            }
        }
    } catch (_: Exception) {
        // Fallback: pre-O pattern vibration
        try {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
                longArrayOf(0, 120), -1
            )
        } catch (_: Exception) {
        }
    }

    // === Sound — ToneGenerator works on all devices regardless of silent mode ===
    Thread {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            Thread.sleep(300)
            generator.release()
        } catch (_: Exception) {
        }
    }.start()
}

// ====== Clipboard ======

private fun copyToClipboard(context: Context, text: String, showToast: Boolean = true) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Echo", text))
    if (showToast) {
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}

// ====== Share ======

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享文本"))
}

// ====== Open URL ======

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}

// ====== Scan Overlay ======

@Composable
private fun ScanOverlay(isScanning: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent dark background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // Clear scanning frame in the center
        val frameSize = 260.dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(frameSize)
        ) {
            // Frame border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (isScanning) Color.White else Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
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
private fun FocusIndicator(trigger: Long, x: Float, y: Float) {
    // visibility fades from 1f → 0f over 500ms
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger != 0L) {
            shown = true
            delay(500)
            shown = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(400),
        label = "focusAlpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x.toInt() - 30.dp.roundToPx(), y.toInt() - 30.dp.roundToPx()) }
                .size(60.dp)
                .border(2.dp, Color(0xFF4FC3F7).copy(alpha = alpha), RoundedCornerShape(6.dp))
                .background(Color(0xFF4FC3F7).copy(alpha = 0.1f * alpha))
        )
    }
}

// ====== Result Content ======

@Composable
private fun ResultContent(
    result: ScanResult,
    autoCopyEnabled: Boolean,
    onToggleAutoCopy: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenUrl: () -> Unit,
    onScanAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header row: title + auto-copy switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "扫描结果",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "自动复制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(4.dp))
            Switch(
                checked = autoCopyEnabled,
                onCheckedChange = onToggleAutoCopy,
                colors = SwitchDefaults.colors()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Raw text
        Text(
            text = result.rawText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Copy button
        Button(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("复制文本")
        }

        // Share button
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("分享")
        }

        // Open in browser (only if URL)
        if (result.isUrl) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenUrl,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("在浏览器中打开")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan again
        Button(
            onClick = onScanAgain,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("继续扫描")
        }
    }
}

// ====== History Content ======

@Composable
private fun HistoryContent(
    history: List<ScanHistoryItem>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "扫描历史",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                Text(
                    text = "清空",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onClear() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Text(
                text = "暂无扫描记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(history) { item ->
                    HistoryItem(item = item, onClick = { onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(item: ScanHistoryItem, onClick: () -> Unit) {
    val timeFormat = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = timeFormat.format(Date(item.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "复制",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable {
                    // Copy handled via the onCopy callback
                }
        )
    }
}
