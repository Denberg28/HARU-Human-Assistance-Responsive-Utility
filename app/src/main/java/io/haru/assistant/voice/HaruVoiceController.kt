package io.haru.assistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Provider-agnostic HARU voice layer.
 *
 * Responsibilities:
 * - Prefer Android on-device speech recognition when available.
 * - Fall back to the system speech recognizer when an on-device recognizer is unavailable.
 * - Prefer an installed offline TTS voice for HARU responses.
 *
 * The selected AI backend never needs microphone or speaker access. It only receives text.
 */
class HaruVoiceController(
    private val context: Context,
    private val callbacks: Callbacks,
) : TextToSpeech.OnInitListener {

    interface Callbacks {
        fun onListening()
        fun onTranscript(text: String)
        fun onVoiceError(message: String)
        fun onRuntimeChanged(status: VoiceRuntimeStatus)
    }

    data class VoiceRuntimeStatus(
        val speechInput: String = "Checking…",
        val speechOutput: String = "Checking…",
    )

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    fun initialize() {
        setupRecognizer()
        tts = TextToSpeech(context, this)
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun setupRecognizer() {
        recognizer?.destroy()
        recognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onRuntimeChanged(
                VoiceRuntimeStatus(
                    speechInput = "Unavailable",
                    speechOutput = "Checking…",
                )
            )
            return
        }

        val onDeviceAvailable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        recognizer = if (onDeviceAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        callbacks.onRuntimeChanged(
            VoiceRuntimeStatus(
                speechInput = if (onDeviceAvailable) "On-device" else "System recognizer",
                speechOutput = "Checking…",
            )
        )

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                callbacks.onListening()
            }

            override fun onResults(results: Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                if (spoken.isNullOrBlank()) {
                    callbacks.onVoiceError("I didn't catch that.")
                } else {
                    callbacks.onTranscript(spoken)
                }
            }

            override fun onError(error: Int) {
                callbacks.onVoiceError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is needed for voice input."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "The system speech recognizer needs a connection right now."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Voice recognition is busy. Try again."
                        else -> "Voice input stopped. Try again."
                    }
                )
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun startListening() {
        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            callbacks.onVoiceError("Speech recognition is not available on this device.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haru-response")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            publishOutputStatus("Unavailable")
            return
        }

        val locale = Locale.getDefault()
        tts?.language = locale

        val offlineVoice = tts
            ?.voices
            ?.filter { voice ->
                !voice.isNetworkConnectionRequired &&
                    voice.locale.language == locale.language
            }
            ?.sortedByDescending { voice -> voice.quality }
            ?.firstOrNull()

        if (offlineVoice != null) {
            tts?.voice = offlineVoice
            publishOutputStatus("Offline TTS")
        } else {
            publishOutputStatus("System TTS")
        }
    }

    private fun publishOutputStatus(output: String) {
        val input =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                "On-device"
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                "System recognizer"
            } else {
                "Unavailable"
            }

        callbacks.onRuntimeChanged(
            VoiceRuntimeStatus(
                speechInput = input,
                speechOutput = output,
            )
        )
    }

    fun shutdown() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
