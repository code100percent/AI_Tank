package com.example.new_tank_ai

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.new_tank_ai.ai.GeminiCompanionManager
import com.example.new_tank_ai.ai.GeminiResult
import com.example.new_tank_ai.bluetooth.BluetoothManager
import com.example.new_tank_ai.bluetooth.VoiceRecognizerManager
import com.example.new_tank_ai.camera.CameraManager
import com.example.new_tank_ai.model.RobotExpression
import com.example.new_tank_ai.ui.screens.CompanionScreen
import com.example.new_tank_ai.ui.screens.ControlScreen
import com.example.new_tank_ai.ui.screens.HomeScreen
import com.example.new_tank_ai.ui.screens.SettingsScreen
import com.example.new_tank_ai.ui.theme.New_tank_aiTheme
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    COMPANION,
    SETTINGS,
    CONTROL
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            New_tank_aiTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                
                val bluetoothManager = remember { BluetoothManager(context) }
                val geminiManager = remember { GeminiCompanionManager(context) }
                val cameraManager = remember { CameraManager(context) }
                val coroutineScope = rememberCoroutineScope()
                
                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                var currentExpression by remember { mutableStateOf(RobotExpression.NEUTRAL) }
                var geminiReplyText by remember { mutableStateOf("") }
                var isFemaleVoice by remember { mutableStateOf(geminiManager.isFemaleVoice) }
                var autoStopDuration by remember { mutableFloatStateOf(bluetoothManager.autoStopDurationSeconds) }

                var voiceRecognizerManagerRef by remember { mutableStateOf<VoiceRecognizerManager?>(null) }

                // Bind camera manager to activity lifecycle
                DisposableEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.addObserver(cameraManager)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(cameraManager)
                    }
                }

                // Camera permission request launcher
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        cameraManager.bindCamera(lifecycleOwner)
                    }
                }

                LaunchedEffect(Unit) {
                    if (!cameraManager.hasCameraPermission()) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                val voiceRecognizer = remember {
                    VoiceRecognizerManager(
                        context = context,
                        onCommandRecognized = { commandChar, durationSec ->
                            val secInt = durationSec.toInt()
                            val secLabel = if (secInt == 1) "1 second" else "$secInt seconds"
                            val vibeMessage = when (commandChar) {
                                'F' -> "Engaging drive! Moving forward for $secLabel."
                                'B' -> "Reversing trajectory for $secLabel."
                                'L' -> "Pivoting left for $secLabel."
                                'R' -> "Pivoting right for $secLabel."
                                'G' -> "Curving left for $secLabel."
                                'I' -> "Curving right for $secLabel."
                                'S' -> "Halting systems."
                                else -> "Executing movement."
                            }

                            geminiReplyText = vibeMessage
                            currentExpression = when (commandChar) {
                                'F' -> RobotExpression.HAPPY
                                'B' -> RobotExpression.SAD
                                'L', 'R', 'G', 'I' -> RobotExpression.EXCITED
                                'S' -> RobotExpression.NEUTRAL
                                else -> currentExpression
                            }

                            // Speak vibe response FIRST, then trigger Bluetooth motor pulse AFTER speech finishes
                            voiceRecognizerManagerRef?.pauseForSpeechOutput()
                            coroutineScope.launch {
                                var hasStartedMoving = false
                                geminiManager.sendMessage(vibeMessage) { isSpeaking ->
                                    if (isSpeaking) {
                                        currentExpression = RobotExpression.SPEAKING
                                    } else {
                                        if (!hasStartedMoving) {
                                            hasStartedMoving = true
                                            // Send Bluetooth command to motors after speech finishes
                                            bluetoothManager.sendMovementCommand(commandChar, durationSec)
                                        }
                                        voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                    }
                                }
                            }
                        },
                        onVisionQuery = { query ->
                            Log.d("TankAI", "Vision query matched: '$query'")
                            voiceRecognizerManagerRef?.pauseForSpeechOutput()

                            if (!cameraManager.hasCameraPermission()) {
                                Log.w("TankAI", "Camera permission missing for vision query")
                                geminiReplyText = "I can't see right now."
                                currentExpression = RobotExpression.CONFUSED
                                coroutineScope.launch {
                                    geminiManager.sendMessage("I can't see right now.") { isSpeaking ->
                                        if (isSpeaking) {
                                            currentExpression = RobotExpression.SPEAKING
                                        } else {
                                            currentExpression = RobotExpression.SAD
                                            voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                        }
                                    }
                                }
                                return@VoiceRecognizerManager
                            }

                            currentExpression = RobotExpression.THINKING
                            coroutineScope.launch {
                                Log.d("TankAI", "Capturing camera frame for vision query...")
                                val jpegFrame = cameraManager.captureFrame()
                                if (jpegFrame == null) {
                                    Log.e("TankAI", "Camera frame capture failed or timed out")
                                    geminiReplyText = "I can't see right now."
                                    currentExpression = RobotExpression.CONFUSED
                                    geminiManager.sendMessage("I can't see right now.") { isSpeaking ->
                                        if (isSpeaking) {
                                            currentExpression = RobotExpression.SPEAKING
                                        } else {
                                            currentExpression = RobotExpression.SAD
                                            voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                        }
                                    }
                                    return@launch
                                }

                                Log.d("TankAI", "Sending frame (${jpegFrame.size / 1024} KB) to Gemini Vision...")
                                when (val result = geminiManager.describeImage(jpegFrame, query) { isSpeaking ->
                                    if (isSpeaking) {
                                        currentExpression = RobotExpression.SPEAKING
                                    } else {
                                        currentExpression = RobotExpression.HAPPY
                                        voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                    }
                                }) {
                                    is GeminiResult.Success -> {
                                        geminiReplyText = result.text
                                    }
                                    is GeminiResult.Error -> {
                                        geminiReplyText = result.message
                                        currentExpression = RobotExpression.CONFUSED
                                        voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                    }
                                }
                            }
                        },
                        onConversationalQuery = { query ->
                            voiceRecognizerManagerRef?.pauseForSpeechOutput()
                            currentExpression = RobotExpression.THINKING
                            coroutineScope.launch {
                                when (val result = geminiManager.sendMessage(query) { isSpeaking ->
                                    if (isSpeaking) {
                                        currentExpression = RobotExpression.SPEAKING
                                    } else {
                                        currentExpression = RobotExpression.HAPPY
                                        voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                    }
                                }) {
                                    is GeminiResult.Success -> {
                                        geminiReplyText = result.text
                                    }
                                    is GeminiResult.Error -> {
                                        geminiReplyText = result.message
                                        currentExpression = RobotExpression.CONFUSED
                                        voiceRecognizerManagerRef?.resumeAfterSpeechOutput(600)
                                    }
                                }
                            }
                        }
                    )
                }

                LaunchedEffect(voiceRecognizer) {
                    voiceRecognizerManagerRef = voiceRecognizer
                }

                DisposableEffect(Unit) {
                    onDispose {
                        voiceRecognizer.destroy()
                        geminiManager.shutdown()
                        bluetoothManager.cleanup()
                    }
                }

                when (currentScreen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            currentExpression = currentExpression,
                            onExpressionSelected = { expr -> currentExpression = expr },
                            bluetoothManager = bluetoothManager,
                            voiceRecognizer = voiceRecognizer,
                            geminiReplyText = geminiReplyText,
                            onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                            onNavigateToCompanion = { currentScreen = AppScreen.COMPANION }
                        )
                    }
                    AppScreen.COMPANION -> {
                        CompanionScreen(
                            currentExpression = currentExpression,
                            onExpressionSelected = { expr -> currentExpression = expr },
                            onBackClick = { currentScreen = AppScreen.HOME }
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            currentExpression = currentExpression,
                            onExpressionSelected = { expr -> currentExpression = expr },
                            bluetoothManager = bluetoothManager,
                            isFemaleVoice = isFemaleVoice,
                            onVoiceGenderChanged = { isFemale ->
                                isFemaleVoice = isFemale
                                geminiManager.setVoiceGender(isFemale)
                            },
                            autoStopDuration = autoStopDuration,
                            onAutoStopDurationChanged = { duration ->
                                autoStopDuration = duration
                                bluetoothManager.autoStopDurationSeconds = duration
                            },
                            onNavigateToControl = { currentScreen = AppScreen.CONTROL },
                            onBackClick = { currentScreen = AppScreen.HOME }
                        )
                    }
                    AppScreen.CONTROL -> {
                        ControlScreen(
                            onBackClick = { currentScreen = AppScreen.SETTINGS }
                        )
                    }
                }
            }
        }
    }
}
