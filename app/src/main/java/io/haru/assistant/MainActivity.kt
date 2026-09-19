package io.haru.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.companion.ReminderScheduler
import io.haru.assistant.content.AndroidHazardBundle
import io.haru.assistant.content.AndroidHazardService
import io.haru.assistant.content.AndroidNewsBundle
import io.haru.assistant.content.AndroidNewsService
import io.haru.assistant.localai.AndroidLocalAiManager
import io.haru.assistant.localai.LocalAiStatus
import io.haru.assistant.localai.LocalModelOption
import io.haru.assistant.localai.LocalModelDownloadManager
import io.haru.assistant.localai.LocalModelDownloadState
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.location.TrustedLocationManager
import io.haru.assistant.onlineai.AndroidOnlineAiManager
import io.haru.assistant.onlineai.GeminiModel
import io.haru.assistant.onlineai.OnlineProvider
import io.haru.assistant.update.AndroidAppUpdateManager
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
    private lateinit var localModelDownloadManager: LocalModelDownloadManager
    private lateinit var onlineAiManager: AndroidOnlineAiManager
    private lateinit var appUpdateManager: AndroidAppUpdateManager
    private lateinit var companionStore: AndroidCompanionStore
    private lateinit var newsService: AndroidNewsService
    private lateinit var hazardService: AndroidHazardService
    private lateinit var trustedLocationManager: TrustedLocationManager

    private var pendingVoiceStart = false
    private var pendingShareName = "Loved one"
    private var pendingShareMinutes = 60

    private var voiceStatus by mutableStateOf(
        HaruVoiceController.VoiceRuntimeStatus()
    )
    private var localAiStatus by mutableStateOf(LocalAiStatus())
    private var localAiBusy by mutableStateOf(false)
    private var localAiDownloadProgress by mutableStateOf<Float?>(null)
    private var localAiDownloadLabel by mutableStateOf("")
    private var localAiDownloadState by mutableStateOf(LocalModelDownloadState())
    private var companionSnapshot by mutableStateOf(CompanionSnapshot())

    private var onlineProvider by mutableStateOf(OnlineProvider.ANTIGRAVITY)
    private var selectedGeminiModel by mutableStateOf(AndroidOnlineAiManager.DEFAULT_GEMINI_MODEL)
    private var onlineStatus by mutableStateOf("Antigravity is the online default.")
    private var hasGeminiKey by mutableStateOf(false)
    private var updateStatus by mutableStateOf("")
    private var updateUrl by mutableStateOf("")

    private var newsBundle by mutableStateOf(AndroidNewsBundle())
    private var hazardBundle by mutableStateOf(AndroidHazardBundle())

    private var trustedLocations by mutableStateOf(emptyList<TrustedLocation>())
    private var locationShareCode by mutableStateOf("")

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

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                captureLocationForShare()
            } else {
                locationShareCode = ""
                onlineStatus = "Location permission is needed to create a share."
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
        localModelDownloadManager = LocalModelDownloadManager(applicationContext)
        onlineAiManager = AndroidOnlineAiManager(applicationContext)
        appUpdateManager = AndroidAppUpdateManager()
        companionStore = AndroidCompanionStore(applicationContext)
        newsService = AndroidNewsService()
        hazardService = AndroidHazardService()
        trustedLocationManager = TrustedLocationManager(applicationContext)

        companionSnapshot = companionStore.load()
        val onlineSettings = onlineAiManager.settings()
        onlineProvider = onlineSettings.provider
        selectedGeminiModel = onlineSettings.geminiModel
        refreshOnlineKeyState()
        trustedLocations = trustedLocationManager.load()
        refreshLocalAiStatus()
        refreshNews()
        refreshHazards()

        localModelDownloadManager.liveData().observe(this) { workInfos ->
            localAiDownloadState = localModelDownloadManager.stateFrom(workInfos)
            localAiDownloadProgress = localAiDownloadState.progress
            localAiDownloadLabel = formatLocalDownloadLabel(localAiDownloadState)

            if (localAiDownloadState.state == "COMPLETE") {
                refreshLocalAiStatus(
                    message = localAiDownloadState.modelName + " downloaded. Tap Use model to activate.",
                    state = "READY",
                )
            }
        }

        setContent {
            HaruTheme {
                val haruViewModel: HaruViewModel = viewModel()
                activeViewModel = haruViewModel

                HaruScreen(
                    viewModel = haruViewModel,
                    voiceStatus = voiceStatus,
                    localAiStatus = localAiStatus,
                    localAiBusy = localAiBusy,
                    localModelOptions = localAiManager.curatedModels(),
                    localAiDownloadProgress = localAiDownloadProgress,
                    localAiDownloadLabel = localAiDownloadLabel,
                    localAiDownloadState = localAiDownloadState.state,
                    localAiDownloadModelId = localAiDownloadState.modelId,
                    todayLines = companionSnapshot.todayLines(),
                    onlineProvider = onlineProvider,
                    selectedGeminiModel = selectedGeminiModel,
                    geminiModels = onlineAiManager.geminiModels(),
                    onlineStatus = onlineStatus,
                    hasGeminiKey = hasGeminiKey,
                    appVersion = currentVersionName(),
                    updateStatus = updateStatus,
                    updateUrl = updateUrl,
                    newsBundle = newsBundle,
                    hazardBundle = hazardBundle,
                    trustedLocations = trustedLocations,
                    locationShareCode = locationShareCode,
                    onSubmitClick = {
                        submitWithAi(haruViewModel, speakResult = false)
                    },
                    onMicClick = { requestVoiceRecognition() },
                    onSpeakClick = {
                        voiceController.speak(haruViewModel.uiState.message)
                    },
                    onDownloadLocalModel = ::downloadCuratedLocalModel,
                    onPauseLocalModelDownload = ::pauseLocalModelDownload,
                    onResumeLocalModelDownload = ::resumeLocalModelDownload,
                    onCancelLocalModelDownload = ::cancelLocalModelDownload,
                    onValidateLocalModel = ::validateLocalModel,
                    onDeleteLocalModel = ::deleteLocalModel,
                    onSelectOnlineProvider = ::selectOnlineProvider,
                    onSelectGeminiModel = ::selectGeminiModel,
                    onSaveGeminiKey = ::saveGeminiKey,
                    onTestOnlineAi = ::testOnlineAi,
                    onCheckUpdate = ::checkForUpdate,
                    onOpenUpdate = ::openUrl,
                    onRefreshNews = ::refreshNews,
                    onRefreshHazards = ::refreshHazards,
                    onOpenUrl = ::openUrl,
                    onCreateLocationShare = ::requestLocationShare,
                    onImportLocationShare = ::importLocationShare,
                    onClearTrustedLocations = ::clearTrustedLocations,
                )
            }
        }
    }

    private fun submitWithAi(
        viewModel: HaruViewModel,
        speakResult: Boolean,
    ) {
        val command = viewModel.uiState.command.trim()
        viewModel.recordLatestUser(command)
        val companionReply = handleCompanionCommand(command)
        if (companionReply != null) {
            viewModel.updateCommand("")
            viewModel.completeLocalAi(companionReply, success = true)
            if (speakResult) {
                voiceController.speak(companionReply)
            }
            return
        }

        val prompt = viewModel.prepareAiPrompt() ?: run {
            if (speakResult) {
                voiceController.speak(viewModel.uiState.message)
            }
            return
        }

        lifecycleScope.launch {
            val activeModel = localAiStatus.activeModel
            try {
                val reply = if (onlineProvider == OnlineProvider.LOCAL_ONLY) {
                    require(activeModel.isNotBlank()) {
                        "No validated local AI model is active."
                    }
                    localAiManager.generate(activeModel, prompt)
                } else {
                    try {
                        val response = onlineAiManager.ask(
                            onlineProvider,
                            prompt,
                            SYSTEM_PROMPT,
                        )
                        onlineStatus = providerName(onlineProvider) + " connected."
                        response
                    } catch (onlineError: Exception) {
                        if (activeModel.isBlank()) throw onlineError
                        onlineStatus =
                            "Online AI unavailable; HARU used the local model."
                        localAiManager.generate(activeModel, prompt)
                    }
                }

                viewModel.completeLocalAi(reply, success = true)
                if (speakResult) {
                    voiceController.speak(reply)
                }
            } catch (exc: Exception) {
                val message = exc.message ?: "HARU could not complete that request."
                viewModel.completeLocalAi(message, success = false)
                if (speakResult) {
                    voiceController.speak(message)
                }
            }
        }
    }

    private fun selectOnlineProvider(provider: OnlineProvider) {
        onlineProvider = provider
        onlineAiManager.saveProvider(provider)
        onlineStatus = providerName(provider) + " selected."
    }

    private fun saveGeminiKey(value: String) {
        if (value.isBlank()) return
        onlineAiManager.saveGeminiKey(value)
        refreshOnlineKeyState()
        onlineStatus = "Gemini key saved securely on this phone."
    }

    private fun selectGeminiModel(model: GeminiModel) {
        selectedGeminiModel = model
        onlineAiManager.saveGeminiModel(model)
        onlineStatus = model.label + " selected."
    }

    private fun refreshOnlineKeyState() {
        hasGeminiKey = onlineAiManager.hasGeminiKey()
    }

    private fun testOnlineAi() {
        if (onlineProvider == OnlineProvider.LOCAL_ONLY) {
            onlineStatus = if (localAiStatus.activeModel.isBlank()) {
                "No validated local model is active."
            } else {
                "Local AI is ready."
            }
            return
        }

        onlineStatus = "Testing " + providerName(onlineProvider) + "…"
        lifecycleScope.launch {
            onlineStatus = try {
                val result = onlineAiManager.test(onlineProvider)
                providerName(onlineProvider) + " connected · " + result.take(80)
            } catch (exc: Exception) {
                exc.message ?: "Connection test failed."
            }
        }
    }

    private fun refreshNews() {
        lifecycleScope.launch {
            newsBundle = newsService.fetch("Philippines")
        }
    }

    private fun refreshHazards() {
        lifecycleScope.launch {
            hazardBundle = hazardService.fetch()
        }
    }


    private fun currentVersionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

    private fun checkForUpdate() {
        updateStatus = "Checking for the latest HARU release…"
        updateUrl = ""
        lifecycleScope.launch {
            updateStatus = try {
                val info = appUpdateManager.check(currentVersionName())
                if (info.updateAvailable) {
                    updateUrl = info.apkUrl.ifBlank { info.releaseUrl }
                    "HARU v" + info.remoteVersion + " is available."
                } else {
                    "HARU is up to date (v" + currentVersionName() + ")."
                }
            } catch (exc: Exception) {
                exc.message ?: "Could not check for updates."
            }
        }
    }

    private fun openUrl(url: String) {
        if (!url.startsWith("https://")) return
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            )
        }
    }

    private fun requestLocationShare(name: String, minutes: Int) {
        pendingShareName = name.trim().take(40).ifBlank { "Loved one" }
        pendingShareMinutes = minutes.coerceIn(15, 24 * 60)

        if (hasLocationPermission()) {
            captureLocationForShare()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun captureLocationForShare() {
        if (!hasLocationPermission()) return

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        if (providers.isEmpty()) {
            onlineStatus = "Turn on phone location services to create a share."
            return
        }

        val lastLocation = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        if (lastLocation != null) {
            finishLocationShare(lastLocation)
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                finishLocationShare(location)
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
        }

        runCatching {
            manager.requestSingleUpdate(
                providers.first(),
                listener,
                Looper.getMainLooper(),
            )
        }.onFailure {
            onlineStatus = "HARU could not obtain a phone location."
        }
    }

    private fun finishLocationShare(location: Location) {
        locationShareCode = runCatching {
            trustedLocationManager.createShareCode(
                name = pendingShareName,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyM = location.accuracy.toDouble(),
                expiresMinutes = pendingShareMinutes,
            )
        }.getOrElse {
            onlineStatus = it.message ?: "Could not create location share."
            ""
        }
    }

    private fun importLocationShare(code: String) {
        if (code.isBlank()) return
        runCatching {
            trustedLocationManager.importShareCode(code)
        }.onSuccess {
            trustedLocations = trustedLocationManager.load()
        }.onFailure {
            onlineStatus = it.message ?: "Could not import location share."
        }
    }

    private fun clearTrustedLocations() {
        trustedLocationManager.clear()
        trustedLocations = emptyList()
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

    private fun downloadCuratedLocalModel(option: LocalModelOption) {
        if (localAiStatus.installedModels.contains(option.fileName)) {
            validateLocalModel(option.fileName)
            return
        }

        requestNotificationPermissionIfNeeded()
        localModelDownloadManager.start(option)
        localAiDownloadState = LocalModelDownloadState(
            modelId = option.id,
            modelName = option.name,
            state = "QUEUED",
            message = "Preparing background download…",
        )
        localAiDownloadProgress = null
        localAiDownloadLabel =
            option.name + " queued. You can keep using HARU or switch apps."
    }

    private fun pauseLocalModelDownload() {
        localModelDownloadManager.pause()
        localAiDownloadState = localAiDownloadState.copy(
            state = "PAUSED",
            message = "Paused · tap Resume to continue.",
        )
        localAiDownloadLabel = formatLocalDownloadLabel(localAiDownloadState)
    }

    private fun resumeLocalModelDownload() {
        val resumed = localModelDownloadManager.resume(localAiManager.curatedModels())
        if (!resumed) {
            localAiDownloadLabel = "No paused model download to resume."
        }
    }

    private fun cancelLocalModelDownload() {
        localModelDownloadManager.cancel()
        localAiDownloadState = LocalModelDownloadState()
        localAiDownloadProgress = null
        localAiDownloadLabel = "Download cancelled. Partial file removed."
        refreshLocalAiStatus()
    }

    private fun formatLocalDownloadLabel(state: LocalModelDownloadState): String {
        val downloadedMb = state.downloadedBytes / (1024f * 1024f)
        val total = state.totalBytes
        return when {
            state.state == "IDLE" -> state.message
            total != null && total > 0L -> {
                val totalMb = total / (1024f * 1024f)
                val pct = ((state.downloadedBytes * 100L) / total).coerceIn(0L, 100L)
                String.format(
                    java.util.Locale.US,
                    "%s · %.0f / %.0f MB · %d%%",
                    state.state.lowercase().replaceFirstChar { it.uppercase() },
                    downloadedMb,
                    totalMb,
                    pct,
                )
            }
            else -> {
                val prefix = state.state.lowercase().replaceFirstChar { it.uppercase() }
                if (state.downloadedBytes > 0L) {
                    String.format(
                        java.util.Locale.US,
                        "%s · %.0f MB",
                        prefix,
                        downloadedMb,
                    )
                } else {
                    state.message.ifBlank { prefix }
                }
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


    override fun onResume() {
        super.onResume()
        if (::companionStore.isInitialized) {
            companionSnapshot = companionStore.load()
        }
        if (::trustedLocationManager.isInitialized) {
            trustedLocations = trustedLocationManager.load()
        }
    }

    override fun onListening() {
        activeViewModel?.setListening()
    }

    override fun onTranscript(text: String) {
        val viewModel = activeViewModel ?: return
        viewModel.updateCommand(text)
        submitWithAi(viewModel, speakResult = true)
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

    private fun providerName(provider: OnlineProvider): String =
        when (provider) {
            OnlineProvider.ANTIGRAVITY -> "Antigravity"
            OnlineProvider.GEMINI -> selectedGeminiModel.label
            OnlineProvider.LOCAL_ONLY -> "Local AI"
        }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are HARU, a concise and practical personal companion. " +
                "Use local device tools for notes, tasks, reminders, voice, hazards, news, and trusted locations. " +
                "Do not claim actions you did not perform."
    }
}
