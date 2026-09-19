package io.haru.assistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Battery-aware voice layer.
 *
 * Speech recognition and TTS engines are created only when the user requests
 * them and are released as soon as the interaction completes.
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
        val speechInput: String = "On demand",
        val speechOutput: String = "On demand",
    )

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onRuntimeChanged(
                VoiceRuntimeStatus(
                    speechInput = "Unavailable",
                    speechOutput = "On demand",
                )
            )
            return null
        }

        val onDeviceAvailable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        val created = if (onDeviceAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                callbacks.onListening()
            }

            override fun onResults(results: Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                releaseRecognizer()
                if (spoken.isNullOrBlank()) {
                    callbacks.onVoiceError("I didn't catch that.")
                } else {
                    callbacks.onTranscript(spoken)
                }
            }

            override fun onError(error: Int) {
                releaseRecognizer()
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

        recognizer = created
        callbacks.onRuntimeChanged(
            VoiceRuntimeStatus(
                speechInput = if (onDeviceAvailable) "On-device" else "System recognizer",
                speechOutput = "On demand",
            )
        )
        return created
    }

    fun startListening() {
        val speechRecognizer = ensureRecognizer()
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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        if (ttsReady && tts != null) {
            speakNow(clean)
            return
        }

        pendingSpeech = clean
        if (tts == null) {
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            pendingSpeech = null
            releaseTts()
            callbacks.onRuntimeChanged(
                VoiceRuntimeStatus(
                    speechInput = inputCapability(),
                    speechOutput = "Unavailable",
                )
            )
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
            ?.maxByOrNull { voice -> voice.quality }

        if (offlineVoice != null) {
            tts?.voice = offlineVoice
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post { releaseTts() }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                mainHandler.post { releaseTts() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { releaseTts() }
            }
        })

        ttsReady = true
        callbacks.onRuntimeChanged(
            VoiceRuntimeStatus(
                speechInput = inputCapability(),
                speechOutput = if (offlineVoice != null) "Offline TTS" else "System TTS",
            )
        )

        pendingSpeech?.let { pending ->
            pendingSpeech = null
            speakNow(pending)
        }
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun inputCapability(): String =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> "On-device"
            SpeechRecognizer.isRecognitionAvailable(context) -> "System recognizer"
            else -> "Unavailable"
        }

    private fun releaseRecognizer() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun releaseTts() {
        ttsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    fun releaseTransientResources() {
        pendingSpeech = null
        releaseRecognizer()
        releaseTts()
    }

    fun shutdown() {
        releaseTransientResources()
        mainHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val UTTERANCE_ID = "haru-response"
    }
}
