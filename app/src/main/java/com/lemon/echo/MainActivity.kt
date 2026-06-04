package com.lemon.echo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lemon.echo.ui.EchoApp
import com.lemon.echo.ui.theme.EchoTheme

class MainActivity : ComponentActivity() {

    private var cameraGranted by mutableStateOf(false)
    private var permissionDeniedForever by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            // Check if "Don't ask again" was checked
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                permissionDeniedForever = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!cameraGranted) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // Permission was denied forever previously
                permissionDeniedForever = true
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        setContent {
            EchoTheme {
                if (cameraGranted) {
                    EchoApp()
                } else if (permissionDeniedForever) {
                    PermissionDeniedDialog(
                        onGoToSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        },
                        onRetry = {
                            permissionDeniedForever = false
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                } else {
                    PermissionRequestView(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PermissionDeniedDialog(
    onGoToSettings: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("需要相机权限") },
        text = {
            Text("扫码功能需要相机权限。您之前拒绝了权限请求，请在系统设置中手动开启相机权限。")
        },
        confirmButton = {
            Button(onClick = onGoToSettings) {
                Text("前往设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text("再试一次")
            }
        }
    )
}

@androidx.compose.runtime.Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要相机权限才能扫码",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("授予相机权限")
        }
    }
}
