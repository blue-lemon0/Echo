package com.lemon.echo.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.lemon.echo.scanner.ScanHistoryItem
import com.lemon.echo.scanner.ScanHistoryManager
import com.lemon.echo.scanner.ScanResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    historyManager: ScanHistoryManager,
    soundEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    autoCopyEnabled: Boolean = false,
    onSoundToggle: (Boolean) -> Unit = {},
    onVibrationToggle: (Boolean) -> Unit = {},
    onAutoCopyToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Scan state
    var isScanning by remember { mutableStateOf(true) }
    var detectedResult by remember { mutableStateOf<ScanResult?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        ) {
            showPermissionDeniedDialog = true
        }
    }

    // Keep settings params up-to-date for DisposableEffect / lambda capture
    val currentSoundEnabled by rememberUpdatedState(soundEnabled)
    val currentVibrationEnabled by rememberUpdatedState(vibrationEnabled)
    val currentAutoCopyEnabled by rememberUpdatedState(autoCopyEnabled)

    // Keep screen on while scanning
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Image picker for gallery QR scanning
    var galleryProcessing by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        galleryProcessing = true
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                scanBitmapForResult(context, bitmap) { result ->
                    galleryProcessing = false
                    if (result != null) {
                        isScanning = false
                        detectedResult = result
                        showResult = true
                        historyManager.addToHistory(result)
                        triggerFeedback(context, currentSoundEnabled, currentVibrationEnabled)
                        if (currentAutoCopyEnabled) {
                            copyToClipboard(context, result.rawText, showToast = false)
                        }
                    }
                }
            } else {
                galleryProcessing = false
                Toast.makeText(context, "无法读取图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            galleryProcessing = false
            Toast.makeText(context, "读取图片失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun scanAgain() {
        showResult = false
        detectedResult = null
        isScanning = true
    }

    // --- UI ---
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            // Camera preview (CameraX + scan overlay + focus indicator)
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                isScanning = isScanning && !showResult,
                isTorchOn = isTorchOn,
                onBarcodeDetected = { result ->
                    isScanning = false
                    detectedResult = result
                    showResult = true
                    historyManager.addToHistory(result)
                    triggerFeedback(context, currentSoundEnabled, currentVibrationEnabled)
                    if (currentAutoCopyEnabled) {
                        copyToClipboard(context, result.rawText, showToast = false)
                    }
                }
            )
        } else {
            // Permission request view
            PermissionRequestContent(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        // Toolbar: torch (only when hasPermission), settings, history
        ScannerToolbar(
            isTorchOn = isTorchOn,
            onToggleTorch = { isTorchOn = !isTorchOn },
            onOpenSettings = { showSettings = true },
            onOpenHistory = { showHistory = true },
            showTorch = hasPermission
        )

        // Bottom controls: gallery button + hint (only when camera is active)
        if (hasPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(enabled = !galleryProcessing) {
                            imagePicker.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (galleryProcessing) Icons.Default.QrCodeScanner
                        else Icons.Default.Image,
                        contentDescription = "从相册选择二维码",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.size(10.dp))

                Text(
                    text = if (galleryProcessing) "识别中…" else "将二维码对准框内",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
            }
        }
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
                autoCopyEnabled = autoCopyEnabled,
                onToggleAutoCopy = onAutoCopyToggle,
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

    // Settings bottom sheet
    SettingsSheet(
        show = showSettings,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        autoCopyEnabled = autoCopyEnabled,
        onSoundToggle = onSoundToggle,
        onVibrationToggle = onVibrationToggle,
        onAutoCopyToggle = onAutoCopyToggle,
        onDismiss = { showSettings = false }
    )

    // Permission denied dialog
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("需要相机权限") },
            text = {
                Text("扫码功能需要相机权限。您之前拒绝了权限请求，请在系统设置中手动开启相机权限。")
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDeniedDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDeniedDialog = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("再试一次")
                }
            }
        )
    }
}
// ====== Toolbar ======

@Composable
private fun ScannerToolbar(
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    showTorch: Boolean = true
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // History button (top-left)
        IconButton(
            onClick = onOpenHistory,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp)
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

        // Torch + settings (top-right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showTorch) {
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
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
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置"
                )
            }
        }
    }
}

// ====== Permission Request View ======

@Composable
private fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.size(24.dp))
        Text(
            text = "需要相机权限才能扫码",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "点击下方按钮授权相机权限",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.size(32.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("授予相机权限")
        }
    }
}

private fun triggerFeedback(context: Context, playSound: Boolean, playVibration: Boolean) {
    // Ensure on main thread for vibration
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post { triggerFeedback(context, playSound, playVibration) }
        return
    }

    // === Vibration ===
    if (playVibration) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST)
                        .build()
                    v.vibrate(
                        VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE),
                        attrs
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v.vibrate(VibrationEffect.createOneShot(120, 128))
                }
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
                    longArrayOf(0, 120), -1
                )
            } catch (_: Exception) {
            }
        }
    }

    // === Sound ===
    if (playSound) {
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

/** Scan a Bitmap using ML Kit barcode scanner and invoke [onResult] when done.
 * [onResult] is ALWAYS called, with null if no barcode was found or an error occurred. */
private fun scanBitmapForResult(
    context: Context,
    bitmap: android.graphics.Bitmap,
    onResult: (ScanResult?) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val scanner = BarcodeScanning.getClient()
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrBlank()) {
                    onResult(ScanResult.fromBarcodeText(rawValue))
                    return@addOnSuccessListener
                }
            }
            // No scannable barcode found
            onResult(null)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "未识别到二维码", Toast.LENGTH_SHORT).show()
            }
        }
        .addOnFailureListener {
            onResult(null)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "识别失败", Toast.LENGTH_SHORT).show()
            }
        }
}
