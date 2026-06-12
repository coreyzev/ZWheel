package com.zwheel.app.ui.ble

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwheel.app.ble.ConnectionState
import kotlinx.coroutines.launch

@Composable
fun BleDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: BleDebugViewModel = viewModel(),
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val logLines by viewModel.logLines.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val permissionsGranted by viewModel.permissionsGranted.collectAsStateWithLifecycle()
    val permanentlyDenied by viewModel.permanentlyDenied.collectAsStateWithLifecycle()
    val requiredPermissions = remember { bleScanPermissions() }
    val exporter = remember(context) { BleDebugLogExporter(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var showPairDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onInitialPermissionCheck(
            granted = hasAllRequiredPermissions(context, requiredPermissions),
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        val granted = requiredPermissions.all { permission ->
            grantResults[permission] == true || hasPermission(context, permission)
        }
        viewModel.onPermissionsResult(
            granted = granted,
            permanentlyDenied = hasPermanentlyDeniedPermission(
                context = context,
                permissions = requiredPermissions,
                requestAttempted = true,
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("BLE DEBUG", fontWeight = FontWeight.Black)
        if (!permissionsGranted) {
            Text(buildPermissionDeniedMessage())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (permissionsGranted) {
                Button(
                    enabled = connectionState != ConnectionState.Scanning &&
                        connectionState != ConnectionState.Connected,
                    onClick = viewModel::onScanClicked,
                ) {
                    Text(if (connectionState == ConnectionState.Scanning) "Scanning..." else "Scan")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.onPermissionsAttempted()
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    },
                ) {
                    Text("Grant permissions")
                }
                if (permanentlyDenied) {
                    Button(
                        onClick = { context.openAppSettings() },
                    ) {
                        Text("Open settings")
                    }
                }
            }
            if (connectionState == ConnectionState.Scanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            Button(
                enabled = selectedDevice != null &&
                    connectionState != ConnectionState.Connected,
                onClick = { selectedDevice?.deviceId?.let(viewModel::onConnectClicked) },
            ) {
                Text("Connect + Unlock")
            }
            Button(
                enabled = connectionState == ConnectionState.Connected,
                onClick = viewModel::onDisconnectClicked,
            ) {
                Text("Disconnect")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { exporter.share(viewModel.exportJsonLines()) }
                            .onSuccess(viewModel::onExportStatus)
                            .onFailure { error -> viewModel.onExportStatus("Share failed: ${error.message}") }
                    }
                },
            ) {
                Text("Share log")
            }
            Button(
                enabled = exporter.uploadSupported,
                onClick = { showPairDialog = true },
            ) {
                Text("Pair upload")
            }
            Button(
                enabled = exporter.uploadSupported,
                onClick = {
                    scope.launch {
                        runCatching { exporter.upload(viewModel.exportJsonLines()) }
                            .onSuccess(viewModel::onExportStatus)
                            .onFailure { error -> viewModel.onExportStatus("Upload failed: ${error.message}") }
                    }
                },
            ) {
                Text("Upload log")
            }
        }
        Text(exportStatus)
        if (devices.isNotEmpty()) {
            Text("Selected: ${selectedDevice?.label().orEmpty()}")
        }
        HorizontalDivider()
        logLines.takeLast(12).forEach { line ->
            Text(text = line)
        }
    }

    if (showPairDialog) {
        PairUploadDialog(
            onDismiss = { showPairDialog = false },
            onPair = { serverUrl, password ->
                showPairDialog = false
                scope.launch {
                    runCatching { exporter.pair(serverUrl, password) }
                        .onSuccess(viewModel::onExportStatus)
                        .onFailure { error -> viewModel.onExportStatus("Pair failed: ${error.message}") }
                }
            },
        )
    }
}

@Composable
private fun PairUploadDialog(
    onDismiss: () -> Unit,
    onPair: (String, String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair upload") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Pairing password") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = serverUrl.isNotBlank() && password.isNotBlank(),
                onClick = { onPair(serverUrl, password) },
            ) {
                Text("Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun buildPermissionDeniedMessage(): String =
    "Bluetooth scan permissions are required before scanning so ZWheel can find the board and keep the ride connection alive."
