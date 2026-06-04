package com.lemon.echo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lemon.echo.scanner.ScanHistoryManager
import com.lemon.echo.scanner.SettingsManager

private enum class Tab(val label: String) {
    SCAN("扫一扫"),
    CREATE("生成")
}

@Composable
fun EchoApp() {
    val context = LocalContext.current
    val historyManager = remember { ScanHistoryManager(context) }
    val settingsManager = remember { SettingsManager(context) }

    var currentTab by remember { mutableStateOf(Tab.SCAN) }

    // Local state synced with SharedPreferences for Compose recomposition
    var soundEnabled by remember { mutableStateOf(settingsManager.soundEnabled) }
    var vibrationEnabled by remember { mutableStateOf(settingsManager.vibrationEnabled) }
    var autoCopyEnabled by remember { mutableStateOf(settingsManager.autoCopyEnabled) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == Tab.SCAN,
                    onClick = { currentTab = Tab.SCAN },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "扫一扫"
                        )
                    },
                    label = { Text("扫一扫") }
                )
                NavigationBarItem(
                    selected = currentTab == Tab.CREATE,
                    onClick = { currentTab = Tab.CREATE },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "生成"
                        )
                    },
                    label = { Text("生成") }
                )
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            Tab.SCAN -> ScannerScreen(
                modifier = Modifier.padding(innerPadding),
                historyManager = historyManager,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                autoCopyEnabled = autoCopyEnabled,
                onSoundToggle = {
                    soundEnabled = it
                    settingsManager.soundEnabled = it
                },
                onVibrationToggle = {
                    vibrationEnabled = it
                    settingsManager.vibrationEnabled = it
                },
                onAutoCopyToggle = {
                    autoCopyEnabled = it
                    settingsManager.autoCopyEnabled = it
                }
            )
            Tab.CREATE -> CreateQRScreen()
        }
    }
}
