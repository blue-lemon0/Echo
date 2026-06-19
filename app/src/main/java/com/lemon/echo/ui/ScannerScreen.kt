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
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.lemon.echo.scanner.HistoryLabel
import com.lemon.echo.scanner.ScanHistoryItem
import com.lemon.echo.scanner.ScanHistoryManager
import com.lemon.echo.scanner.ScanResult
import com.lemon.echo.scanner.playFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import com.lemon.echo.scanner.chain.ScanMode
import com.lemon.echo.scanner.chain.ScanSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    modifier: Modifier = Modifier,
    session: ScanSession,
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
    var viewingHistoryItem by remember { mutableStateOf<ScanHistoryItem?>(null) }

    // Continuous scan mode state
    var scanMode by remember { mutableStateOf(ScanMode.SINGLE) }
    val sessionState by session.state.collectAsState()
    var sessionResult by remember { mutableStateOf<String?>(null) }
    var showSessionResult by remember { mutableStateOf(false) }

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

    // React to session state changes (continuous mode)
    val currentHistoryManager by rememberUpdatedState(historyManager)
    LaunchedEffect(sessionState) {
        when (val s = sessionState) {
            is ScanSession.State.Done -> {
                playFeedback(context, sound = currentSoundEnabled, vibration = currentVibrationEnabled)
                if (currentAutoCopyEnabled) {
                    copyToClipboard(context, s.text, showToast = false)
                }
                currentHistoryManager.addToHistory(s.text, HistoryLabel.CHAIN)
                sessionResult = s.text
                showSessionResult = true
            }
            is ScanSession.State.SoloScanned -> {
                playFeedback(context, sound = currentSoundEnabled, vibration = currentVibrationEnabled)
            }
            is ScanSession.State.Rejected -> {
                Toast.makeText(context, s.reason, Toast.LENGTH_SHORT).show()
            }
            is ScanSession.State.Stopped -> {
                val text = s.segments.joinToString("") { it.second }
                if (text.isNotBlank()) {
                    currentHistoryManager.addToHistory(text, HistoryLabel.CHAIN)
                    sessionResult = text
                    showSessionResult = true
                }
            }
            else -> {}
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
            if (bitmap == null) {
                galleryProcessing = false
                Toast.makeText(context, "无法读取图片", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            scanBitmapForResult(context, bitmap) { result ->
                galleryProcessing = false
                if (result != null) {
                    isScanning = false
                    detectedResult = result
                    showResult = true
                    historyManager.addToHistory(result)
                    playFeedback(context, sound = currentSoundEnabled, vibration = currentVibrationEnabled)
                    if (currentAutoCopyEnabled) {
                        copyToClipboard(context, result.rawText, showToast = false)
                    }
                }
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
            if (scanMode == ScanMode.SINGLE) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isScanning = isScanning && !showResult,
                    isTorchOn = isTorchOn,
                    onBarcodeDetected = { result ->
                        isScanning = false
                        detectedResult = result
                        showResult = true
                        historyManager.addToHistory(result)
                        playFeedback(context, sound = currentSoundEnabled, vibration = currentVibrationEnabled)
                        if (currentAutoCopyEnabled) {
                            copyToClipboard(context, result.rawText, showToast = false)
                        }
                    }
                )
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isScanning = true,
                    isTorchOn = isTorchOn,
                    onBarcodeDetected = { result ->
                        session.accept(result.rawText)
                    }
                )
            }
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

        // Bottom controls: gallery button + mode switch + hint (only when camera is active)
        if (hasPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode switcher: SINGLE / CONTINUOUS
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF4FC3F7),
                    selectedLabelColor = Color.White,
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    labelColor = Color.White
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = scanMode == ScanMode.SINGLE,
                        onClick = {
                            scanMode = ScanMode.SINGLE
                            session.reset()
                        },
                        label = { Text("单次", fontSize = 12.sp) },
                        colors = chipColors,
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = scanMode == ScanMode.CONTINUOUS,
                        onClick = {
                            scanMode = ScanMode.CONTINUOUS
                            session.start()
                        },
                        label = { Text("连续", fontSize = 12.sp) },
                        colors = chipColors,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

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
                    text = when {
                        galleryProcessing -> "识别中…"
                        scanMode == ScanMode.CONTINUOUS -> "连续扫码中，依次扫描各二维码"
                        else -> "将二维码对准框内"
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
            }
        }

        // Continuous mode: progress card (top center, integrated stop button)
        if (scanMode == ScanMode.CONTINUOUS && hasPermission) {
            when (val s = sessionState) {
                is ScanSession.State.Collecting -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 132.dp, start = 48.dp, end = 48.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "连续扫码",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${s.got}/${s.total}",
                                color = Color(0xFF4FC3F7),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { session.stop() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "停止连续扫码",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (i in 0 until s.total) {
                                val done = i in s.collected
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (done) Color(0xFF4CAF50)
                                            else Color.White.copy(alpha = 0.15f)
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (done) Color(0xFF4CAF50)
                                            else Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${i + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (done) Color.White
                                        else Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.size(6.dp))

                        val missing = (0 until s.total).filter { it !in s.collected }
                        if (missing.isNotEmpty()) {
                            Text(
                                text = "待扫: ${formatRanges(missing)}",
                                color = Color(0xFFFFCC80),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is ScanSession.State.Scanning -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 132.dp, start = 48.dp, end = 48.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "连续扫码",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable {
                                        session.reset()
                                        scanMode = ScanMode.SINGLE
                                        isScanning = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "退出连续扫码",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "等待首张二维码…",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
                else -> {}
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

    // Session result bottom sheet (continuous mode)
    if (showSessionResult && sessionResult != null) {
        val text = sessionResult!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showSessionResult = false
                sessionResult = null
                if (scanMode == ScanMode.CONTINUOUS) session.start()
            },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SessionResultContent(
                text = text,
                autoCopyEnabled = autoCopyEnabled,
                onCopy = { copyToClipboard(context, text) },
                onShare = { shareText(context, text) },
                onContinue = {
                    showSessionResult = false
                    sessionResult = null
                    session.start()
                },
                onStop = {
                    showSessionResult = false
                    sessionResult = null
                    session.reset()
                    scanMode = ScanMode.SINGLE
                    isScanning = true
                }
            )
        }
    }

    // History bottom sheet
    if (showHistory) {
        val historyList by historyManager.historyFlow.collectAsState()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            HistoryContent(
                history = historyList,
                onClear = {
                    historyManager.clearHistory()
                    showHistory = false
                },
                onItemClick = { item ->
                    showHistory = false
                    viewingHistoryItem = item
                },
                onItemCopy = { item ->
                    copyToClipboard(context, item.text)
                }
            )
        }
    }

    // History item detail bottom sheet
    viewingHistoryItem?.let { item ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewingHistoryItem = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HistoryItemDetailContent(
                text = item.text,
                label = item.label,
                onCopy = { copyToClipboard(context, item.text) },
                onShare = { shareText(context, item.text) },
                onOpenUrl = { openUrl(context, item.text) },
                onDismiss = { viewingHistoryItem = null }
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

// ====== Toolbar Icon Button (reusable) ======

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

// ====== Scanner Toolbar ======

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
        ToolbarIconButton(
            onClick = onOpenHistory,
            icon = Icons.Default.History,
            contentDescription = "扫描历史",
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp)
        )

        // Torch + settings (top-right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showTorch) {
                ToolbarIconButton(
                    onClick = onToggleTorch,
                    icon = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (isTorchOn) "关闭手电筒" else "打开手电筒",
                    tint = if (isTorchOn) Color(0xFFFFD700) else Color.White
                )
            }

            ToolbarIconButton(
                onClick = onOpenSettings,
                icon = Icons.Default.Settings,
                contentDescription = "设置"
            )
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
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
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

        // Raw text — scrollable with max height, buttons stay visible
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = result.rawText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                textAlign = TextAlign.Start,
            )
        }

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

        Spacer(modifier = Modifier.height(8.dp))

        // Share + Scan again on the same row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
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

            Button(
                onClick = onScanAgain,
                modifier = Modifier.weight(1f),
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
}

// ====== Session Result Content (continuous mode) ======

@Composable
private fun SessionResultContent(
    text: String,
    autoCopyEnabled: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
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
                text = "连续扫码结果",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                textAlign = TextAlign.Start,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
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

            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("继续扫码")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "退出连续扫码",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
    }
}

// ====== History Content ======

@Composable
private fun HistoryContent(
    history: List<ScanHistoryItem>,
    onClear: () -> Unit,
    onItemClick: (ScanHistoryItem) -> Unit,
    onItemCopy: (ScanHistoryItem) -> Unit = {}
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
            return
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(history) { item ->
                HistoryItem(
                    item = item,
                    onClick = { onItemClick(item) },
                    onCopy = { onItemCopy(item) }
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(item: ScanHistoryItem, onClick: () -> Unit, onCopy: () -> Unit = {}) {
    val timeFormat = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(
            imageVector = if (item.label == HistoryLabel.CHAIN)
                Icons.Default.Link else Icons.Default.QrCodeScanner,
            contentDescription = item.label.name,
            tint = if (item.label == HistoryLabel.CHAIN)
                MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
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
                .clickable { onCopy() }
        )
    }
}

@Composable
private fun HistoryItemDetailContent(
    text: String,
    label: HistoryLabel,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (label == HistoryLabel.CHAIN)
                    Icons.Default.Link else Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = if (label == HistoryLabel.CHAIN)
                    MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (label == HistoryLabel.CHAIN) "链式扫码结果" else "扫码结果",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                textAlign = TextAlign.Start,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
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
            if (text.startsWith("http://") || text.startsWith("https://")) {
                Button(
                    onClick = onOpenUrl,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
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
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("关闭", fontSize = 13.sp)
        }
    }
}

/** Compress a sorted list of 0‑based indices to compact 1‑based ranges: [0,1,4] → "1-2, 5" */
private fun formatRanges(indices: List<Int>): String {
    if (indices.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var i = 0
    while (i < indices.size) {
        val start = indices[i]
        var end = start
        while (i + 1 < indices.size && indices[i + 1] == end + 1) {
            end = indices[i + 1]
            i++
        }
        parts.add(if (start == end) "${start + 1}" else "${start + 1}-${end + 1}")
        i++
    }
    return parts.joinToString(", ")
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
