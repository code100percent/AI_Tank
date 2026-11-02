package com.example.new_tank_ai.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.new_tank_ai.bluetooth.BluetoothManager
import com.example.new_tank_ai.bluetooth.ConnectionStatus
import com.example.new_tank_ai.bluetooth.VoiceRecognizerManager
import com.example.new_tank_ai.ui.theme.*

@SuppressLint("MissingPermission")
@Composable
fun ControlScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bluetoothManager = remember { BluetoothManager(context) }
    
    val connectionStatus by bluetoothManager.connectionStatus.collectAsState()
    val pairedDevices by bluetoothManager.pairedDevices.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableIntStateOf(5) } // Speed 0-9

    // Voice recognition manager
    val voiceRecognizer = remember {
        VoiceRecognizerManager(
            context = context,
            onCommandRecognized = { commandChar, durationSec ->
                bluetoothManager.sendMovementCommand(commandChar, durationSec)
            },
            onVisionQuery = { _ ->
                // No-op in control screen
            },
            onConversationalQuery = { _ ->
                // No-op in control screen
            }
        )
    }
    val isListening by voiceRecognizer.isListening.collectAsState()
    val recognizedText by voiceRecognizer.recognizedText.collectAsState()

    // Permissions check for Bluetooth & Audio
    val permissionsToRequest = mutableListOf(
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    var hasPermissions by remember {
        mutableStateOf(permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            bluetoothManager.refreshPairedDevices()
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            bluetoothManager.refreshPairedDevices()
            showDeviceDialog = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecognizer.destroy()
            bluetoothManager.cleanup()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CyberBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            voiceRecognizer.destroy()
                            bluetoothManager.cleanup()
                            onBackClick()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurface)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberWhite)
                    }
                    Column {
                        Text(
                            text = "VEHICLE DRIVE",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                            color = CyberWhite
                        )
                        StatusBadge(status = connectionStatus)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Control Mic Button
                    IconButton(
                        onClick = {
                            if (!hasPermissions) {
                                permissionLauncher.launch(permissionsToRequest)
                            } else {
                                if (isListening || voiceRecognizer.isVoiceSessionActive) {
                                    voiceRecognizer.stopSession()
                                } else {
                                    voiceRecognizer.startSession()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isListening) CyberElectricBlue else CyberSurface)
                            .border(1.dp, if (isListening) CyberCyan else CyberSurfaceVariant, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Voice Control",
                            tint = if (isListening) CyberBlack else CyberWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Connect / Disconnect button
                    Button(
                        onClick = {
                            if (!hasPermissions) {
                                permissionLauncher.launch(permissionsToRequest)
                            } else if (!bluetoothManager.isBluetoothEnabled) {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else {
                                when (connectionStatus) {
                                    is ConnectionStatus.Connected -> bluetoothManager.disconnect()
                                    else -> {
                                        bluetoothManager.refreshPairedDevices()
                                        showDeviceDialog = true
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (connectionStatus) {
                                is ConnectionStatus.Connected -> CyberRed
                                is ConnectionStatus.Connecting -> CyberSteel
                                else -> CyberElectricBlue
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (connectionStatus) {
                                is ConnectionStatus.Connected -> "Disconnect"
                                is ConnectionStatus.Connecting -> "Connecting..."
                                else -> "Connect"
                            },
                            color = CyberBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Voice feedback overlay indicator
            if (isListening || recognizedText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberSurfaceVariant)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isListening) CyberElectricBlue else CyberSteelLight)
                        )
                        Text(
                            text = if (isListening) "Listening for voice command... (say 'move forward', 'backward', 'turn left', 'turn right', 'stop')" else "Recognized: \"$recognizedText\"",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = CyberWhite
                        )
                    }
                }
            }

            // D-Pad Control Interface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Forward (F)
                    DpadButton(
                        label = "▲",
                        modifier = Modifier.align(Alignment.TopCenter).size(72.dp),
                        onAction = { isDown -> bluetoothManager.sendCommand(if (isDown) 'F' else 'S') }
                    )
                    // Backward (B)
                    DpadButton(
                        label = "▼",
                        modifier = Modifier.align(Alignment.BottomCenter).size(72.dp),
                        onAction = { isDown -> bluetoothManager.sendCommand(if (isDown) 'B' else 'S') }
                    )
                    // Left (L)
                    DpadButton(
                        label = "◀",
                        modifier = Modifier.align(Alignment.CenterStart).size(72.dp),
                        onAction = { isDown -> bluetoothManager.sendCommand(if (isDown) 'L' else 'S') }
                    )
                    // Right (R)
                    DpadButton(
                        label = "▶",
                        modifier = Modifier.align(Alignment.CenterEnd).size(72.dp),
                        onAction = { isDown -> bluetoothManager.sendCommand(if (isDown) 'R' else 'S') }
                    )
                    // Curve Left (G)
                    DpadButton(
                        label = "◤",
                        modifier = Modifier.align(Alignment.TopStart).size(56.dp),
                        onAction = { isDown -> bluetoothManager.sendCommand(if (isDown) 'G' else 'S') }
                    )
                    // Curve Right (I)
                    DpadButton(
                        label = "◥",
                        modifier = Modifier.align(Alignment.TopEnd).size(56.dp),
                        onAction = { isDown -> bluetoothManager.sendCommand(if (isDown) 'I' else 'S') }
                    )
                    // Center Stop (S)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(8.dp, CircleShape, spotColor = CyberRed)
                            .clip(CircleShape)
                            .background(CyberRedDark)
                            .border(2.dp, CyberRed, CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        bluetoothManager.sendCommand('S')
                                        tryAwaitRelease()
                                        bluetoothManager.sendCommand('S')
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("STOP", color = CyberWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Speed Slider (0-9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CyberSurface)
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ROBOT SPEED", color = CyberSteelLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Level $currentSpeed (0-9)", color = CyberElectricBlue, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = currentSpeed.toFloat(),
                            onValueChange = { newValue ->
                                currentSpeed = newValue.toInt()
                                bluetoothManager.sendCommand(currentSpeed.digitToChar())
                            },
                            valueRange = 0f..9f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = CyberElectricBlue, activeTrackColor = CyberElectricBlue)
                        )
                    }
                }
            }
        }
    }

    // Device Picker Dialog
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            containerColor = CyberSurface,
            title = { Text("Select PicoRobot", color = CyberWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose your paired Mini Cyber Truck device:", color = CyberSteelLight, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (pairedDevices.isEmpty()) {
                        Text("No paired Bluetooth devices found.\nPlease pair 'PicoRobot' in Android Bluetooth settings first.", color = CyberRed, fontSize = 13.sp)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 250.dp)
                        ) {
                            items(pairedDevices) { device ->
                                val deviceName = device.name ?: "Unknown"
                                val isPicoRobot = deviceName == "PicoRobot"
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isPicoRobot) CyberSurfaceVariant else CyberBlack)
                                        .border(width = if (isPicoRobot) 2.dp else 1.dp, color = if (isPicoRobot) CyberElectricBlue else CyberSurfaceVariant, shape = RoundedCornerShape(12.dp))
                                        .clickable {
                                            showDeviceDialog = false
                                            bluetoothManager.connectToDevice(device)
                                        }
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(deviceName, color = if (isPicoRobot) CyberElectricBlue else CyberWhite, fontWeight = FontWeight.Bold)
                                            Text(device.address, color = CyberSteelLight, fontSize = 11.sp)
                                        }
                                        if (isPicoRobot) {
                                            Badge(containerColor = CyberElectricBlue) { Text("Target", color = CyberBlack, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) { Text("Cancel", color = CyberElectricBlue) }
            }
        )
    }
}

@Composable
fun DpadButton(
    label: String,
    modifier: Modifier = Modifier,
    onAction: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = CyberElectricBlue)
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onAction(true) // Pressed down
                        tryAwaitRelease()
                        onAction(false) // Released
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = CyberWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
fun StatusBadge(status: ConnectionStatus) {
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> "Connected (${status.deviceName})" to CyberElectricBlue
        is ConnectionStatus.Connecting -> "Connecting..." to CyberCyan
        is ConnectionStatus.Error -> "Connection Error" to CyberRed
        is ConnectionStatus.Disconnected -> "Disconnected" to CyberSteelLight
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = color)
    }
}
