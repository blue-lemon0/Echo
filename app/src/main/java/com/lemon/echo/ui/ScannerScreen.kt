package com.lemon.echo.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemon.echo.scanner.BarcodeAnalyzer
import com.lemon.echo.scanner.ScanResult
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Scan state
    var isScanning by remember { mutableStateOf(true) }
    var detectedResult by remember { mutableStateOf<ScanResult?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Camera state
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

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
            // Apply initial torch state
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    // Toggle torch
    fun toggleTorch() {
        isTorchOn = !isTorchOn
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    // Resume scanning
    fun scanAgain() {
        showResult = false
        detectedResult = null
        isScanning = true
    }

    // Copy to clipboard
    fun copyToClipboard(text: String) {
        val clipboard = ContextCompat.getSystemService(
            context,
            android.content.ClipboardManager::class.java
        )
        val clip = android.content.ClipData.newPlainText("Echo", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    // Open URL in browser
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = view
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scan overlay
        ScanOverlay(isScanning = isScanning && !showResult)

        // Torch button (top-right)
        IconButton(
            onClick = { toggleTorch() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
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
                onCopy = { copyToClipboard(result.rawText) },
                onOpenUrl = { openUrl(result.rawText) },
                onScanAgain = { scanAgain() }
            )
        }
    }
}

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
                .aspectRatio(1f)
        ) {
            // Transparent cutout
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

            // Corner decorations
            CornerLine(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 4.dp),
                angle = 0f
            )
            CornerLine(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 4.dp),
                angle = 90f
            )
            CornerLine(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp),
                angle = -90f
            )
            CornerLine(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp),
                angle = 180f
            )
        }
    }
}

@Composable
private fun CornerLine(modifier: Modifier, angle: Float) {
    Box(
        modifier = modifier
            .size(24.dp, 4.dp)
            .background(Color(0xFF4FC3F7), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun ResultContent(
    result: ScanResult,
    onCopy: () -> Unit,
    onOpenUrl: () -> Unit,
    onScanAgain: () -> Unit
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
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "扫描结果",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Raw text display — selectable, preserves all whitespace
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

        // Action buttons
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

        // Open in browser (only if it's a URL)
        if (result.isUrl) {
            Spacer(modifier = Modifier.height(12.dp))
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
