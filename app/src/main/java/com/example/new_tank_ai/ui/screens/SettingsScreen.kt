package com.example.new_tank_ai.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.new_tank_ai.bluetooth.BluetoothManager
import com.example.new_tank_ai.bluetooth.ConnectionStatus
import com.example.new_tank_ai.components.*
import com.example.new_tank_ai.model.RobotExpression
import com.example.new_tank_ai.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentExpression: RobotExpression,
    onExpressionSelected: (RobotExpression) -> Unit,
    bluetoothManager: BluetoothManager,
    isFemaleVoice: Boolean,
    onVoiceGenderChanged: (Boolean) -> Unit,
    autoStopDuration: Float,
    onAutoStopDurationChanged: (Float) -> Unit,
    onNavigateToControl: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val connectionStatus by bluetoothManager.connectionStatus.collectAsState()
    val pairedDevices by bluetoothManager.pairedDevices.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var companionName by remember { mutableStateOf("Mini Cyber Truck") }
    var animationsEnabled by remember { mutableStateOf(true) }
    var animationSpeed by remember { mutableStateOf(1.0f) }
    var darkThemeOverride by remember { mutableStateOf(true) }

    // Permissions for Bluetooth
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberSurface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyberWhite
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "SETTINGS & BLUETOOTH",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = CyberWhite
                    )
                    Text(
                        text = "Manage Robot Connection & Preferences",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberSteelLight
                    )
                }
            }

            // 1. Bluetooth Connection Management Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberSurface)
                    .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth",
                                tint = CyberElectricBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Robot Connection",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = CyberWhite
                                )
                                Text(
                                    text = when (connectionStatus) {
                                        is ConnectionStatus.Connected -> "Connected to ${(connectionStatus as ConnectionStatus.Connected).deviceName}"
                                        is ConnectionStatus.Connecting -> "Connecting to PicoRobot..."
                                        is ConnectionStatus.Error -> "Connection Error"
                                        else -> "Disconnected from PicoRobot"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (connectionStatus) {
                                        is ConnectionStatus.Connected -> CyberElectricBlue
                                        is ConnectionStatus.Error -> CyberRed
                                        else -> CyberSteelLight
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasPermissions) {
                                    permissionLauncher.launch(permissionsToRequest)
                                } else if (!bluetoothManager.isBluetoothEnabled) {
                                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } else {
                                    bluetoothManager.refreshPairedDevices()
                                    showDeviceDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select PicoRobot", color = CyberBlack, fontWeight = FontWeight.Bold)
                        }

                        if (connectionStatus is ConnectionStatus.Connected) {
                            Button(
                                onClick = { bluetoothManager.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Disconnect", color = CyberWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Expression Change Section
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "EXPRESSIONS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CyberSteelLight,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ExpressionSelector(
                    selectedExpression = currentExpression,
                    onExpressionSelected = onExpressionSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Command Center (DRIVE manual D-pad)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "COMMAND CENTER",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CyberSteelLight,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CyberTruckActionButton(
                        title = "DRIVE CONTROL",
                        icon = Icons.Default.DirectionsCar,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        onNavigateToControl()
                    }
                }
            }

            // 4. System Telemetry
            VehicleStatusSection(modifier = Modifier.fillMaxWidth())

            // 5. Emergency Stop
            EmergencyStopButton(
                onClick = {
                    scope.launch {
                        bluetoothManager.sendCommand('S')
                        snackbarHostState.showSnackbar("Emergency stop 'S' sent to robot.")
                    }
                }
            )

            // 6. System Preferences
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "SYSTEM PREFERENCES",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CyberSteelLight,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Motor Auto-Stop Duration Slider
                SettingCard(
                    title = "Motor Auto-Stop Timer",
                    subtitle = "Auto-stop pulse duration: ${String.format(Locale.ROOT, "%.1f", autoStopDuration)}s",
                    icon = Icons.Default.Timer
                ) {
                    Slider(
                        value = autoStopDuration,
                        onValueChange = onAutoStopDurationChanged,
                        valueRange = 0.5f..5.0f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberElectricBlue,
                            activeTrackColor = CyberElectricBlue,
                            inactiveTrackColor = CyberSurfaceVariant
                        )
                    )
                }

                // Voice Gender Profile Switch
                SettingSwitchCard(
                    title = "Female Voice Profile",
                    subtitle = if (isFemaleVoice) "Current Voice: Female Companion" else "Current Voice: Male Companion",
                    icon = Icons.Default.RecordVoiceOver,
                    checked = isFemaleVoice,
                    onCheckedChange = onVoiceGenderChanged
                )

                // Companion Name
                SettingCard(
                    title = "Companion Name",
                    subtitle = "Customize your robotic vehicle name",
                    icon = Icons.Default.Info
                ) {
                    OutlinedTextField(
                        value = companionName,
                        onValueChange = { companionName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberElectricBlue,
                            unfocusedBorderColor = CyberSteel,
                            focusedTextColor = CyberWhite,
                            unfocusedTextColor = CyberWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Face Animation Toggle
                SettingSwitchCard(
                    title = "Face Animations",
                    subtitle = "Enable blinking and expression motion",
                    icon = Icons.Default.Speed,
                    checked = animationsEnabled,
                    onCheckedChange = { animationsEnabled = it }
                )

                // Dark Theme Override
                SettingSwitchCard(
                    title = "Cyber Dark Mode",
                    subtitle = "Lock interface to industrial dark theme",
                    icon = Icons.Default.Palette,
                    checked = darkThemeOverride,
                    onCheckedChange = { darkThemeOverride = it }
                )

                // Animation Speed Slider
                SettingCard(
                    title = "Animation Speed",
                    subtitle = "Adjust reaction and blink timing: ${String.format(Locale.ROOT, "%.1f", animationSpeed)}x",
                    icon = Icons.Default.Speed
                ) {
                    Slider(
                        value = animationSpeed,
                        onValueChange = { animationSpeed = it },
                        valueRange = 0.5f..2.0f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberElectricBlue,
                            activeTrackColor = CyberElectricBlue,
                            inactiveTrackColor = CyberSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Device Selection Dialog
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            containerColor = CyberSurface,
            title = {
                Text(
                    text = "Select PicoRobot",
                    color = CyberWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose your paired Mini Cyber Truck device:",
                        color = CyberSteelLight,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "No paired Bluetooth devices found.\nPlease pair 'PicoRobot' in Android Bluetooth settings first.",
                            color = CyberRed,
                            fontSize = 13.sp
                        )
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
                                        .border(
                                            width = if (isPicoRobot) 2.dp else 1.dp,
                                            color = if (isPicoRobot) CyberElectricBlue else CyberSurfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
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
                                            Text(
                                                text = deviceName,
                                                color = if (isPicoRobot) CyberElectricBlue else CyberWhite,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = device.address,
                                                color = CyberSteelLight,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (isPicoRobot) {
                                            Badge(containerColor = CyberElectricBlue) {
                                                Text("Target", color = CyberBlack, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text("Cancel", color = CyberElectricBlue)
                }
            }
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = CyberElectricBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = CyberWhite
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberSteelLight
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = CyberElectricBlue,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = CyberWhite
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberSteelLight
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberBlack,
                    checkedTrackColor = CyberElectricBlue,
                    uncheckedThumbColor = CyberSteelLight,
                    uncheckedTrackColor = CyberSurfaceVariant
                )
            )
        }
    }
}
