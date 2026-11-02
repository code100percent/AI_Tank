package com.example.new_tank_ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.new_tank_ai.bluetooth.BluetoothManager
import com.example.new_tank_ai.bluetooth.ConnectionStatus
import com.example.new_tank_ai.bluetooth.VoiceRecognizerManager
import com.example.new_tank_ai.components.*
import com.example.new_tank_ai.model.RobotExpression
import com.example.new_tank_ai.ui.theme.*

@Composable
fun HomeScreen(
    currentExpression: RobotExpression,
    onExpressionSelected: (RobotExpression) -> Unit,
    bluetoothManager: BluetoothManager,
    voiceRecognizer: VoiceRecognizerManager,
    geminiReplyText: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToCompanion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionStatus by bluetoothManager.connectionStatus.collectAsState()
    val isListening by voiceRecognizer.isListening.collectAsState()
    val recognizedText by voiceRecognizer.recognizedText.collectAsState()

    // Permissions for audio
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceRecognizer.startSession()
            onExpressionSelected(RobotExpression.LISTENING)
        }
    }

    // Update expression when listening state changes
    LaunchedEffect(isListening) {
        if (isListening) {
            onExpressionSelected(RobotExpression.LISTENING)
        } else if (currentExpression == RobotExpression.LISTENING) {
            onExpressionSelected(RobotExpression.NEUTRAL)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = CyberBlack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Top Bar: Connection status indicator on left; Microphone and Settings icons on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Bluetooth Connection Badge
                ConnectionBadge(status = connectionStatus)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Small Microphone Icon Button
                    IconButton(
                        onClick = {
                            if (!hasAudioPermission) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                if (isListening || voiceRecognizer.isVoiceSessionActive) {
                                    voiceRecognizer.stopSession()
                                    onExpressionSelected(RobotExpression.NEUTRAL)
                                } else {
                                    voiceRecognizer.startSession()
                                    onExpressionSelected(RobotExpression.LISTENING)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isListening) CyberElectricBlue else CyberSurface)
                            .border(1.dp, if (isListening) CyberCyan else CyberSurfaceVariant, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Voice Control",
                            tint = if (isListening) CyberBlack else CyberWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Settings Icon Button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CyberSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = CyberWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Center: Emotion (Face) and text response from Gemini or expression message
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clickable { onNavigateToCompanion() },
                    contentAlignment = Alignment.Center
                ) {
                    RobotFace(
                        expression = currentExpression,
                        size = 240.dp,
                        isAnimated = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Text bubble showing Gemini response or expression message
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CyberSurface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            geminiReplyText.isNotEmpty() -> "\"$geminiReplyText\""
                            isListening && recognizedText.isNotEmpty() -> "Hearing: \"$recognizedText\""
                            else -> "\"${currentExpression.defaultMessage}\""
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        ),
                        color = if (isListening) CyberElectricBlue else CyberWhite,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionBadge(status: ConnectionStatus) {
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> "Connected (${status.deviceName})" to CyberElectricBlue
        is ConnectionStatus.Connecting -> "Connecting..." to CyberCyan
        is ConnectionStatus.Error -> "Error" to CyberRed
        is ConnectionStatus.Disconnected -> "Disconnected" to CyberSteelLight
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CyberSurface)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = CyberWhite
            )
        }
    }
}
