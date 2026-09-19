package io.haru.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.haru.assistant.localai.AndroidLocalAiManager
import io.haru.assistant.localai.LocalAiStatus
import io.haru.assistant.ui.HaruScreen
import io.haru.assistant.ui.HaruTheme
import io.haru.assistant.voice.HaruVoiceController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), HaruVoiceController.Callbacks {

    private var activeViewModel: HaruViewModel? = null
    private lateinit var voiceController: HaruVoiceController
    private lateinit var localAiManager: AndroidLocalAiManager
    private var pendingVoiceStart = false

    private var voiceStatus by mutableStateOf(
        HaruVoiceController.VoiceRuntimeStatus()
    )

    private var localAiStatus by mutableStateOf(LocalAiStatus())
    private var localAiBusy by mutableStateOf(false)

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingVoiceStart) {
                pendingVoiceStart = false
                voiceController.startListening()
            } else if (!granted) {
                pendingVoiceStart = false
                activeViewModel?.cancelListening(
                    "Microphone permission is needed for voice input."
                )
            }
        }

    private val localModelPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importLocalModel(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceController = HaruVoiceController(
            context = this,
            callbacks = this,
        )
        voiceController.initialize()

        localAiManager = AndroidLocalAiManager(applicationContext)
        refreshLocalAiStatus()

        setContent {
            HaruTheme {
                val haruViewModel: HaruViewModel = viewModel()
                activeViewModel = haruViewModel

                HaruScreen(
                    viewModel = haruViewModel,
                    voiceStatus = voiceStatus,
                    localAiStatus = localAiStatus,
                    localAiBusy = localAiBusy,
                    onMicClick = { requestVoiceRecognition() },
                    onSpeakClick = {
                        voiceController.speak(haruViewModel.uiState.message)
                    },
                    onImportLocalModel = {
                        localModelPicker.launch(arrayOf("*/*"))
                    },
                    onDownloadLocalModel = ::downloadLocalModel,
                    onValidateLocalModel = ::validateLocalModel,
                    onDeleteLocalModel = ::deleteLocalModel,
                    onOpenModelLibrary = ::openModelLibrary,
                )
            }
        }
    }

    private fun requestVoiceRecognition() {
        activeViewModel?.setListening()

        if (voiceController.hasAudioPermission()) {
            voiceController.startListening()
        } else {
            pendingVoiceStart = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun refreshLocalAiStatus(message: String? = null, state: String? = null) {
        val refreshed = localAiManager.inspect(localAiStatus.activeModel)
        localAiStatus = refreshed.copy(
            state = state ?: refreshed.state,
            message = message ?: refreshed.message,
        )
    }

    private fun importLocalModel(uri: Uri) {
        if (localAiBusy) return
        localAiBusy = true
        localAiStatus = localAiStatus.copy(
            state = "WORKING",
            message = "Importing and validating local model…",
        )

        lifecycleScope.launch {
            try {
                val name = localAiManager.importModel(uri)
                localAiStatus = localAiManager.inspect(name).copy(
                    activeModel = name,
                    state = "READY",
                    message = name + " is validated and ready.",
                )
            } catch (exc: Exception) {
                refreshLocalAiStatus(
                    message = exc.message ?: "Model import failed.",
                    state = "ERROR",
                )
            } finally {
                localAiBusy = false
            }
        }
    }

    private fun downloadLocalModel(url: String) {
        if (localAiBusy || url.isBlank()) return
        localAiBusy = true
        localAiStatus = localAiStatus.copy(
            state = "WORKING",
            message = "Downloading model securely…",
        )

        lifecycleScope.launch {
            try {
                val name = localAiManager.downloadModel(url)
                localAiStatus = localAiManager.inspect(name).copy(
                    activeModel = name,
                    state = "READY",
                    message = name + " is downloaded, validated, and ready.",
                )
            } catch (exc: Exception) {
                refreshLocalAiStatus(
                    message = exc.message ?: "Model download failed.",
                    state = "ERROR",
                )
            } finally {
                localAiBusy = false
            }
        }
    }

    private fun validateLocalModel(name: String) {
        if (localAiBusy || name.isBlank()) return
        localAiBusy = true
        localAiStatus = localAiStatus.copy(
            state = "WORKING",
            message = "Validating " + name + "…",
        )

        lifecycleScope.launch {
            try {
                val validated = localAiManager.validateInstalledModel(name)
                localAiStatus = localAiManager.inspect(validated).copy(
                    activeModel = validated,
                    state = "READY",
                    message = validated + " is ready for local inference.",
                )
            } catch (exc: Exception) {
                refreshLocalAiStatus(
                    message = exc.message ?: "Model validation failed.",
                    state = "ERROR",
                )
            } finally {
                localAiBusy = false
            }
        }
    }

    private fun deleteLocalModel(name: String) {
        if (localAiBusy || name.isBlank()) return
        val deleted = runCatching { localAiManager.deleteModel(name) }.getOrDefault(false)
        refreshLocalAiStatus(
            message = if (deleted) name + " removed." else "Could not remove " + name + ".",
            state = if (deleted) null else "ERROR",
        )
    }

    private fun openModelLibrary() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://huggingface.co/litert-community")
        )
        startActivity(intent)
    }

    override fun onListening() {
        activeViewModel?.setListening()
    }

    override fun onTranscript(text: String) {
        val viewModel = activeViewModel ?: return
        viewModel.submitVoice(text)
        voiceController.speak(viewModel.uiState.message)
    }

    override fun onVoiceError(message: String) {
        activeViewModel?.cancelListening(message)
    }

    override fun onRuntimeChanged(status: HaruVoiceController.VoiceRuntimeStatus) {
        voiceStatus = status
    }

    override fun onDestroy() {
        voiceController.shutdown()
        super.onDestroy()
    }
}
