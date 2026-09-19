package io.haru.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.companion.ReminderScheduler
import io.haru.assistant.localai.AndroidLocalAiManager
import io.haru.assistant.localai.LocalAiStatus
import io.haru.assistant.ui.HaruScreen
import io.haru.assistant.ui.HaruTheme
import io.haru.assistant.voice.HaruVoiceController
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity(), HaruVoiceController.Callbacks {

    private var activeViewModel: HaruViewModel? = null
    private lateinit var voiceController: HaruVoiceController
    private lateinit var localAiManager: AndroidLocalAiManager
    private lateinit var companionStore: AndroidCompanionStore
    private var pendingVoiceStart = false

    private var voiceStatus by mutableStateOf(
        HaruVoiceController.VoiceRuntimeStatus()
    )

    private var localAiStatus by mutableStateOf(LocalAiStatus())
    private var localAiBusy by mutableStateOf(false)
    private var companionSnapshot by mutableStateOf(CompanionSnapshot())

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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        companionStore = AndroidCompanionStore(applicationContext)
        companionSnapshot = companionStore.load()
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
                    todayLines = companionSnapshot.todayLines(),
                    onSubmitClick = {
                        submitWithLocalAi(haruViewModel, speakResult = false)
                    },
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

    private fun submitWithLocalAi(
        viewModel: HaruViewModel,
        speakResult: Boolean,
    ) {
        val command = viewModel.uiState.command.trim()
        val companionReply = handleCompanionCommand(command)
        if (companionReply != null) {
            viewModel.updateCommand("")
            viewModel.completeLocalAi(companionReply, success = true)
            if (speakResult) {
                voiceController.speak(companionReply)
            }
            return
        }

        val activeModel = localAiStatus.activeModel
        val prompt = viewModel.submitOrPrepareLocalAi(activeModel.isNotBlank()) ?: run {
            if (speakResult) {
                voiceController.speak(viewModel.uiState.message)
            }
            return
        }

        lifecycleScope.launch {
            try {
                val reply = localAiManager.generate(activeModel, prompt)
                viewModel.completeLocalAi(reply, success = true)
                if (speakResult) {
                    voiceController.speak(reply)
                }
            } catch (exc: Exception) {
                val message = exc.message ?: "Local AI inference failed."
                viewModel.completeLocalAi(message, success = false)
                if (speakResult) {
                    voiceController.speak(message)
                }
            }
        }
    }

    private fun handleCompanionCommand(command: String): String? {
        if (command.isBlank()) return null
        val clean = command.trim()
        val low = clean.lowercase()

        when (low) {
            "show notes", "list notes", "my notes" -> {
                val notes = companionSnapshot.notes
                return if (notes.isEmpty()) {
                    "You have no saved notes."
                } else {
                    "Notes:\n" + notes.mapIndexed { index, note ->
                        (index + 1).toString() + ". " + note
                    }.joinToString("\n")
                }
            }
            "clear notes", "delete all notes" -> {
                companionSnapshot = companionStore.clearNotes()
                return "All notes cleared."
            }
            "show tasks", "list tasks", "my tasks" -> {
                val tasks = companionSnapshot.tasks
                return if (tasks.isEmpty()) {
                    "Your task list is empty."
                } else {
                    "Tasks:\n" + tasks.mapIndexed { index, task ->
                        val mark = if (task.done) "✓" else "○"
                        (index + 1).toString() + ". " + mark + " " + task.text
                    }.joinToString("\n")
                }
            }
            "clear tasks", "delete all tasks" -> {
                companionSnapshot = companionStore.clearTasks()
                return "All tasks cleared."
            }
            "show reminders", "list reminders", "my reminders" -> {
                val reminders = companionSnapshot.reminders
                    .filter { it.dueAt > System.currentTimeMillis() }
                    .sortedBy { it.dueAt }
                return if (reminders.isEmpty()) {
                    "You have no upcoming reminders."
                } else {
                    "Reminders:\n" + reminders.mapIndexed { index, reminder ->
                        (index + 1).toString() + ". " + reminder.text + " — " +
                            DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(reminder.dueAt))
                    }.joinToString("\n")
                }
            }
            "clear reminders", "delete all reminders" -> {
                companionSnapshot.reminders.forEach {
                    ReminderScheduler.cancel(this, it.id)
                }
                companionSnapshot = companionStore.clearReminders()
                return "All reminders cleared."
            }
        }

        Regex("(?i)^(?:remember that|remember|note|save note)\\s+(.+)$")
            .matchEntire(clean)
            ?.let { match ->
                companionSnapshot = companionStore.addNote(match.groupValues[1])
                return "Noted: " + match.groupValues[1].trim()
            }

        Regex("(?i)^(?:add task|todo|to-do|add to tasks)\\s+(.+)$")
            .matchEntire(clean)
            ?.let { match ->
                companionSnapshot = companionStore.addTask(match.groupValues[1])
                return "Added task: " + match.groupValues[1].trim()
            }

        Regex("(?i)^(?:done|complete|finish)\\s+(?:task\\s+)?(\\d+)$")
            .matchEntire(clean)
            ?.let { match ->
                val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
                if (index !in companionSnapshot.tasks.indices) {
                    return "That task number doesn't exist."
                }
                val taskText = companionSnapshot.tasks[index].text
                companionSnapshot = companionStore.completeTask(index)
                return "Completed: " + taskText
            }

        companionStore.parseRelativeReminder(clean)?.let { (text, dueAt) ->
            val reminder = companionStore.addReminder(text, dueAt)
            companionSnapshot = companionStore.load()
            ReminderScheduler.schedule(this, reminder)
            requestNotificationPermissionIfNeeded()

            val whenText = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
            ).format(Date(dueAt))
            return "Reminder set for " + whenText + ": " + text
        }

        return null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
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
                localAiStatus = localAiManager.inspect().copy(
                    activeModel = "",
                    state = "SETUP",
                    message = name + " imported. Tap Validate before using it.",
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
                localAiStatus = localAiManager.inspect().copy(
                    activeModel = "",
                    state = "SETUP",
                    message = name + " downloaded. Tap Validate before using it.",
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
        viewModel.updateCommand(text)
        submitWithLocalAi(viewModel, speakResult = true)
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
