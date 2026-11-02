package com.example.new_tank_ai.bluetooth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceRecognizerManager(
    private val context: Context,
    private val onCommandRecognized: (Char, Float) -> Unit,
    private val onVisionQuery: (String) -> Unit,
    private val onConversationalQuery: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    var isVoiceSessionActive = false
    private var isPaused = false

    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        createRecognizer()
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        Log.w("TankAI", "SpeechRecognizer error: $error")
                        _isListening.value = false
                        
                        if (isVoiceSessionActive && !isPaused && error != SpeechRecognizer.ERROR_CLIENT) {
                            scope.launch {
                                delay(400)
                                startListeningInternal()
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = matches[0].lowercase(Locale.ROOT)
                            _recognizedText.value = spokenText
                            processVoiceInput(spokenText)
                        } else if (isVoiceSessionActive && !isPaused) {
                            scope.launch {
                                delay(300)
                                startListeningInternal()
                            }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e("TankAI", "Error creating SpeechRecognizer", e)
        }
    }

    fun startSession() {
        isVoiceSessionActive = true
        isPaused = false
        startListeningInternal()
    }

    fun stopSession() {
        isVoiceSessionActive = false
        isPaused = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun pauseForSpeechOutput() {
        isPaused = true
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun resumeAfterSpeechOutput(delayMillis: Long = 600) {
        if (!isVoiceSessionActive) return
        isPaused = false
        scope.launch {
            delay(delayMillis)
            if (isVoiceSessionActive && !isPaused && !_isListening.value) {
                startListeningInternal()
            }
        }
    }

    private fun startListeningInternal() {
        if (speechRecognizer == null || !isVoiceSessionActive || isPaused) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("TankAI", "Failed to start listening", e)
            _isListening.value = false
            createRecognizer()
            scope.launch {
                delay(500)
                if (isVoiceSessionActive && !isPaused) startListeningInternal()
            }
        }
    }

    private fun parseDurationInSeconds(text: String): Float {
        val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:sec|second|seconds|secs)?")
        val matches = regex.findAll(text).toList()
        for (match in matches) {
            val numStr = match.groupValues[1]
            val value = numStr.toFloatOrNull()
            if (value != null && value in 0.5f..30.0f) {
                return value
            }
        }
        return 1.0f // Default duration = 1.0 sec
    }

    private fun processVoiceInput(text: String) {
        val lowerText = text.trim().lowercase(Locale.ROOT)

        // 1. Vision Queries
        val visionPhrases = listOf(
            "what do you see", "what can you see", "what are you seeing",
            "what is this", "describe this", "look at this", "what am i holding"
        )
        if (visionPhrases.any { lowerText.contains(it) }) {
            Log.d("TankAI", "Vision trigger matched: '$text'")
            pauseForSpeechOutput()
            onVisionQuery(text)
            return
        }

        // 2. Natural Language Movement Intent Matching
        val stopPhrases = listOf("stop", "stopped", "halt", "freeze", "pause", "wait", "stand still", "dont move", "don't move", "brake")
        val forwardPhrases = listOf("forward", "ahead", "advance", "straight", "step forward", "show your step", "go front", "come forward", "move front", "go ahead", "move ahead")
        val backwardPhrases = listOf("backward", "back up", "reverse", "retreat", "come back", "step back", "go back", "move back")
        val leftPhrases = listOf("turn left", "spin left", "rotate left", "steer left", "go left", "bear left", "left")
        val rightPhrases = listOf("turn right", "spin right", "rotate right", "steer right", "go right", "bear right", "right")

        val commandChar: Char? = when {
            stopPhrases.any { lowerText.contains(it) } -> 'S'
            forwardPhrases.any { lowerText.contains(it) } -> 'F'
            backwardPhrases.any { lowerText.contains(it) } -> 'B'
            leftPhrases.any { lowerText.contains(it) } -> 'L'
            rightPhrases.any { lowerText.contains(it) } -> 'R'
            else -> null
        }

        if (commandChar != null) {
            val durationSec = if (commandChar == 'S') 0f else parseDurationInSeconds(lowerText)
            Log.d("TankAI", "Natural movement intent matched ('$commandChar', duration: ${durationSec}s) for text: '$text'")
            onCommandRecognized(commandChar, durationSec)
        } else {
            // 3. Conversational Queries -> Gemini AI Assistant
            pauseForSpeechOutput()
            onConversationalQuery(text)
        }
    }

    fun destroy() {
        isVoiceSessionActive = false
        isPaused = false
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
