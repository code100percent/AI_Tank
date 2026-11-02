package com.example.new_tank_ai.ai

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.example.new_tank_ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val MODEL_CHAIN = listOf(
    "gemini-3.5-flash-lite",     // main: fast, not restricted
    "gemini-3.8-flash",          // fallback if the first is busy
    "gemini-3.1-flash-lite",     // second fallback
    "gemini-flash-lite-latest"   // alias, last resort
)

sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Error(val message: String) : GeminiResult
}

class GeminiCompanionManager(context: Context) {

    private val conversationHistory = JSONArray()
    private val systemInstructionText = "You are the digital AI personality of a Mini Cyber Truck, a cool robotic car powered by a Raspberry Pi Pico W. Your tone is friendly, futuristic, clever, and concise (1-2 sentences max)."

    private var tts: TextToSpeech? = null
    var isTtsInitialized = false
        private set

    var isFemaleVoice: Boolean = true
        private set

    private val pendingSpeechQueue = mutableListOf<String>()

    // Cache the working endpoint once discovered
    private var cachedWorkingUrl: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            Log.d("TankAI", "TTS Init status: $status (SUCCESS=${TextToSpeech.SUCCESS})")
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.US)
                Log.d("TankAI", "TTS setLanguage result: $langResult")

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                isTtsInitialized = true
                applyVoiceConfiguration()

                if (pendingSpeechQueue.isNotEmpty()) {
                    val queued = pendingSpeechQueue.toList()
                    pendingSpeechQueue.clear()
                    for (text in queued) {
                        speakOut(text) {}
                    }
                }
            } else {
                Log.e("TankAI", "TTS Initialization failed with status: $status")
            }
        }
    }

    fun setVoiceGender(isFemale: Boolean) {
        isFemaleVoice = isFemale
        applyVoiceConfiguration()
    }

    private fun applyVoiceConfiguration() {
        if (!isTtsInitialized || tts == null) return
        try {
            val voices = tts?.voices
            if (isFemaleVoice) {
                val femaleVoice = voices?.firstOrNull { 
                    !it.isNetworkConnectionRequired && (
                        it.name.lowercase(Locale.ROOT).contains("female") || 
                        it.name.lowercase(Locale.ROOT).contains("woman") ||
                        it.name.lowercase(Locale.ROOT).contains("en-us-x-sfg#female") ||
                        it.name.lowercase(Locale.ROOT).contains("en-us-x-tpf-local")
                    )
                } ?: voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale == Locale.US }

                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                }
                tts?.setPitch(1.25f)
                tts?.setSpeechRate(1.05f)
            } else {
                val maleVoice = voices?.firstOrNull { 
                    !it.isNetworkConnectionRequired && (
                        it.name.lowercase(Locale.ROOT).contains("male") || 
                        it.name.lowercase(Locale.ROOT).contains("man") ||
                        it.name.lowercase(Locale.ROOT).contains("en-us-x-sfg#male") ||
                        it.name.lowercase(Locale.ROOT).contains("en-us-x-smg-local")
                    )
                } ?: voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale == Locale.US }

                if (maleVoice != null) {
                    tts?.voice = maleVoice
                }
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(1.0f)
            }
            Log.d("TankAI", "TTS configured for ${if (isFemaleVoice) "Female" else "Male"} voice")
        } catch (e: Exception) {
            Log.e("TankAI", "Error configuring TTS voice gender", e)
        }
    }

    private fun getCandidateUrls(apiKey: String): List<String> {
        cachedWorkingUrl?.let { return listOf(it) }
        val urls = mutableListOf<String>()
        for (model in MODEL_CHAIN) {
            urls.add("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            urls.add("https://generativelanguage.googleapis.com/v1/models/$model:generateContent?key=$apiKey")
        }
        return urls
    }

    suspend fun sendMessage(userPrompt: String, onSpeakingStateChanged: (Boolean) -> Unit): GeminiResult {
        return withContext(Dispatchers.IO) {
            try {
                val userContent = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                }
                conversationHistory.put(userContent)

                val requestBody = JSONObject().apply {
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText))))
                    put("contents", conversationHistory)
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 200)
                    })
                }

                val apiKey = BuildConfig.GEMINI_API_KEY
                val candidateUrls = getCandidateUrls(apiKey)
                var lastErrorMessage = "Unknown error"

                for (urlString in candidateUrls) {
                    try {
                        val url = URL(urlString)
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("x-goog-api-key", apiKey)
                            connectTimeout = 15000
                            readTimeout = 15000
                            doOutput = true
                        }

                        connection.outputStream.use { os ->
                            os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonResponse = JSONObject(responseString)
                            val candidates = jsonResponse.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val contentObj = firstCandidate.optJSONObject("content")
                                val parts = contentObj?.optJSONArray("parts")
                                val replyText = parts?.optJSONObject(0)?.optString("text")

                                if (!replyText.isNullOrBlank()) {
                                    cachedWorkingUrl = urlString
                                    Log.d("TankAI", "Successfully generated response using: $urlString")

                                    val modelContent = JSONObject().apply {
                                        put("role", "model")
                                        put("parts", JSONArray().put(JSONObject().put("text", replyText)))
                                    }
                                    conversationHistory.put(modelContent)

                                    withContext(Dispatchers.Main) {
                                        if (isTtsInitialized) {
                                            speakOut(replyText, onSpeakingStateChanged)
                                        } else {
                                            Log.w("TankAI", "TTS not initialized yet, queueing reply")
                                            pendingSpeechQueue.add(replyText)
                                            onSpeakingStateChanged(false)
                                        }
                                    }

                                    return@withContext GeminiResult.Success(replyText)
                                }
                            }
                        } else {
                            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $responseCode"
                            lastErrorMessage = "API Error ($responseCode): $errorStream"
                            Log.w("TankAI", "Candidate endpoint failed [$urlString]: $lastErrorMessage")
                        }
                    } catch (e: Exception) {
                        Log.w("TankAI", "Failed candidate request [$urlString]", e)
                        lastErrorMessage = e.localizedMessage ?: "Connection error"
                    }
                }

                Log.e("TankAI", "All Gemini endpoints failed. Last error: $lastErrorMessage")
                withContext(Dispatchers.Main) {
                    onSpeakingStateChanged(false)
                }
                GeminiResult.Error(lastErrorMessage)
            } catch (e: Exception) {
                Log.e("TankAI", "Exception during Gemini request", e)
                withContext(Dispatchers.Main) {
                    onSpeakingStateChanged(false)
                }
                GeminiResult.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    suspend fun describeImage(jpeg: ByteArray, question: String, onSpeakingStateChanged: (Boolean) -> Unit): GeminiResult {
        return withContext(Dispatchers.IO) {
            try {
                val visionSystemInstruction = "You are looking through the phone's front camera on a mini cyber truck. Describe what you see in 1-2 short, friendly sentences."
                val base64Image = Base64.encodeToString(jpeg, Base64.NO_WRAP)

                val imagePart = JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    })
                }
                val textPart = JSONObject().apply {
                    put("text", question)
                }

                val userContentWithImage = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(textPart).put(imagePart))
                }

                val currentContents = JSONArray()
                for (i in 0 until conversationHistory.length()) {
                    currentContents.put(conversationHistory.get(i))
                }
                currentContents.put(userContentWithImage)

                val requestBody = JSONObject().apply {
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", visionSystemInstruction))))
                    put("contents", currentContents)
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 200)
                    })
                }

                val startTime = System.currentTimeMillis()
                val apiKey = BuildConfig.GEMINI_API_KEY
                val candidateUrls = getCandidateUrls(apiKey)
                var lastErrorMessage = "Unknown error"

                for (urlString in candidateUrls) {
                    try {
                        val url = URL(urlString)
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("x-goog-api-key", apiKey)
                            connectTimeout = 20000
                            readTimeout = 20000
                            doOutput = true
                        }

                        connection.outputStream.use { os ->
                            os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonResponse = JSONObject(responseString)
                            val candidates = jsonResponse.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val contentObj = firstCandidate.optJSONObject("content")
                                val parts = contentObj?.optJSONArray("parts")
                                val replyText = parts?.optJSONObject(0)?.optString("text")

                                if (!replyText.isNullOrBlank()) {
                                    cachedWorkingUrl = urlString
                                    val duration = System.currentTimeMillis() - startTime
                                    Log.d("TankAI", "Gemini vision response received in ${duration}ms using: $urlString")

                                    val textUserContent = JSONObject().apply {
                                        put("role", "user")
                                        put("parts", JSONArray().put(JSONObject().put("text", question)))
                                    }
                                    conversationHistory.put(textUserContent)

                                    val modelContent = JSONObject().apply {
                                        put("role", "model")
                                        put("parts", JSONArray().put(JSONObject().put("text", replyText)))
                                    }
                                    conversationHistory.put(modelContent)

                                    withContext(Dispatchers.Main) {
                                        if (isTtsInitialized) {
                                            speakOut(replyText, onSpeakingStateChanged)
                                        } else {
                                            Log.w("TankAI", "TTS not initialized yet, queueing reply")
                                            pendingSpeechQueue.add(replyText)
                                            onSpeakingStateChanged(false)
                                        }
                                    }

                                    return@withContext GeminiResult.Success(replyText)
                                }
                            }
                        } else {
                            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $responseCode"
                            lastErrorMessage = "API Error ($responseCode): $errorStream"
                            Log.w("TankAI", "Vision request failed [$urlString]: $lastErrorMessage")
                        }
                    } catch (e: Exception) {
                        Log.w("TankAI", "Failed vision request [$urlString]", e)
                        lastErrorMessage = e.localizedMessage ?: "Connection error"
                    }
                }

                Log.e("TankAI", "All Gemini vision endpoints failed. Last error: $lastErrorMessage")
                withContext(Dispatchers.Main) {
                    onSpeakingStateChanged(false)
                }
                GeminiResult.Error(lastErrorMessage)
            } catch (e: Exception) {
                Log.e("TankAI", "Exception during Gemini vision request", e)
                withContext(Dispatchers.Main) {
                    onSpeakingStateChanged(false)
                }
                GeminiResult.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    private fun speakOut(text: String, onSpeakingStateChanged: (Boolean) -> Unit) {
        val cleanText = text.replace(Regex("[#*_~-]+"), "").trim()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingStateChanged(true)
            }
            override fun onDone(utteranceId: String?) {
                onSpeakingStateChanged(false)
            }
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                onSpeakingStateChanged(false)
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "gemini_speech_id")
        }
        val speakResult = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "gemini_speech_id")
        Log.d("TankAI", "tts.speak result: $speakResult (SUCCESS=${TextToSpeech.SUCCESS})")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
