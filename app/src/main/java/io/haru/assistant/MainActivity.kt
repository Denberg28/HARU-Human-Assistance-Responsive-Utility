package io.haru.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.location.TrustedLocationManager
import io.haru.assistant.onlineai.AndroidOnlineAiManager
import io.haru.assistant.onlineai.GeminiModel
import io.haru.assistant.onlineai.OnlineProvider
import io.haru.assistant.ui.HaruScreen
import io.haru.assistant.ui.HaruTheme
import io.haru.assistant.update.AndroidAppUpdateManager
import io.haru.assistant.voice.HaruVoiceController
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity(), HaruVoiceController.Callbacks {

    private var activeViewModel: HaruViewModel? = null

    private lateinit var voiceController: HaruVoiceController
    private lateinit var onlineAiManager: AndroidOnlineAiManager
    private lateinit var companionStore: AndroidCompanionStore
    private lateinit var newsService: AndroidNewsService
    private lateinit var hazardService: AndroidHazardService
    private lateinit var trustedLocationManager: TrustedLocationManager
    private lateinit var appUpdateManager: AndroidAppUpdateManager

    private var pendingVoiceStart = false
    private var pendingShareName = "Loved one"
    private var pendingShareMinutes = 60

    private var voiceStatus by mutableStateOf(
        HaruVoiceController.VoiceRuntimeStatus()
    )
    private var companionSnapshot by mutableStateOf(CompanionSnapshot())

    private var onlineProvider by mutableStateOf(OnlineProvider.ANTIGRAVITY)
    private var selectedGeminiModel by mutableStateOf(AndroidOnlineAiManager.FALLBACK_GEMINI_MODEL)
    private var geminiModels by mutableStateOf(listOf(AndroidOnlineAiManager.FALLBACK_GEMINI_MODEL))
    private var onlineStatus by mutableStateOf("Antigravity is the online default.")
    private var hasGeminiKey by mutableStateOf(false)
    private var updateStatus by mutableStateOf("")
    private var updateUrl by mutableStateOf("")

    private var newsBundle by mutableStateOf(AndroidNewsBundle())
    private var hazardBundle by mutableStateOf(AndroidHazardBundle())
    private var newsLoading = false
    private var hazardsLoading = false

    private var trustedLocations by mutableStateOf(emptyList<TrustedLocation>())
    private var currentDeviceLocation by mutableStateOf<TrustedLocation?>(null)
    private var mapGpsActive by mutableStateOf(false)
    private var locationShareCode by mutableStateOf("")
    private var locationShareMapUrl by mutableStateOf("")
    private var mapLocationStatus by mutableStateOf("")
    private var awaitingLocationSettings = false

    private enum class LocationRequestPurpose {
        NONE,
        SHARE,
        MAP,
    }

    private var pendingLocationPurpose = LocationRequestPurpose.NONE
    private var activeLocationListener: LocationListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
                captureRequestedLocation()
            } else {
                when (pendingLocationPurpose) {
                    LocationRequestPurpose.SHARE -> {
                        locationShareCode = ""
                        onlineStatus =
                            "Location permission is needed to create a share."
                    }
                    LocationRequestPurpose.MAP -> {
                        mapLocationStatus =
                            "Location permission is needed to show your GPS position."
                    }
                    LocationRequestPurpose.NONE -> Unit
                }
                pendingLocationPurpose = LocationRequestPurpose.NONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceController = HaruVoiceController(
            context = this,
            callbacks = this,
        )
        onlineAiManager = AndroidOnlineAiManager(applicationContext)
        companionStore = AndroidCompanionStore(applicationContext)
        newsService = AndroidNewsService()
        hazardService = AndroidHazardService()
        trustedLocationManager = TrustedLocationManager(applicationContext)
        appUpdateManager = AndroidAppUpdateManager()

        companionSnapshot = companionStore.load()
        cleanupLegacyStorageOnce()
        val onlineSettings = onlineAiManager.settings()
        onlineProvider = onlineSettings.provider
        selectedGeminiModel = onlineSettings.geminiModel
        geminiModels = onlineAiManager.geminiModels()
        refreshOnlineKeyState()
        trustedLocations = trustedLocationManager.load()

        setContent {
            HaruTheme {
                val haruViewModel: HaruViewModel = viewModel()
                activeViewModel = haruViewModel

                HaruScreen(
                    viewModel = haruViewModel,
                    voiceStatus = voiceStatus,
                    todayLines = companionSnapshot.todayLines(),
                    onlineProvider = onlineProvider,
                    selectedGeminiModel = selectedGeminiModel,
                    geminiModels = geminiModels,
                    onlineStatus = onlineStatus,
                    hasGeminiKey = hasGeminiKey,
                    appVersion = currentVersionName(),
                    updateStatus = updateStatus,
                    updateUrl = updateUrl,
                    newsBundle = newsBundle,
                    hazardBundle = hazardBundle,
                    trustedLocations = trustedLocations,
                    currentDeviceLocation = currentDeviceLocation,
                    mapGpsActive = mapGpsActive,
                    locationShareCode = locationShareCode,
                    locationShareMapUrl = locationShareMapUrl,
                    mapLocationStatus = mapLocationStatus,
                    onSubmitClick = {
                        submitWithAi(haruViewModel, speakResult = false)
                    },
                    onMicClick = { requestVoiceRecognition() },
                    onSpeakClick = {
                        voiceController.speak(haruViewModel.uiState.message)
                    },
                    onSelectOnlineProvider = ::selectOnlineProvider,
                    onSelectGeminiModel = ::selectGeminiModel,
                    onRefreshGeminiModels = ::refreshGeminiModels,
                    onSaveGeminiKey = ::saveGeminiKey,
                    onTestOnlineAi = ::testOnlineAi,
                    onCheckUpdate = ::checkForUpdate,
                    onOpenUpdate = ::openUrl,
                    onRefreshNews = ::refreshNews,
                    onRefreshHazards = ::refreshHazards,
                    onOpenUrl = ::openUrl,
                    onLocateMe = ::toggleMapGps,
                    onCreateLocationShare = ::requestLocationShare,
                    onShareLocation = ::shareLocationText,
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
            viewModel.completeAi(companionReply, success = true)
            if (speakResult) voiceController.speak(companionReply)
            return
        }

        val prompt = viewModel.prepareAiPrompt() ?: run {
            if (speakResult) voiceController.speak(viewModel.uiState.message)
            return
        }

        lifecycleScope.launch {
            try {
                val reply = onlineAiManager.ask(
                    onlineProvider,
                    prompt,
                    SYSTEM_PROMPT,
                )
                onlineStatus = providerName(onlineProvider) + " connected."
                viewModel.completeAi(reply, success = true)
                if (speakResult) voiceController.speak(reply)
            } catch (exc: Exception) {
                val message =
                    exc.message ?: "HARU could not complete that request."
                viewModel.completeAi(message, success = false)
                if (speakResult) voiceController.speak(message)
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

    private fun refreshGeminiModels() {
        onlineStatus = "Refreshing Gemini models…"
        lifecycleScope.launch {
            try {
                geminiModels = onlineAiManager.refreshGeminiModels()
                selectedGeminiModel = onlineAiManager.settings().geminiModel
                onlineStatus =
                    "Gemini models refreshed · " +
                        geminiModels.size +
                        " available."
            } catch (exc: Exception) {
                onlineStatus =
                    exc.message ?: "Could not refresh Gemini models."
            }
        }
    }

    private fun testOnlineAi() {
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
        if (newsLoading) return
        newsLoading = true
        lifecycleScope.launch {
            try {
                newsBundle = newsService.fetch("Philippines")
            } finally {
                newsLoading = false
            }
        }
    }

    private fun refreshHazards() {
        if (hazardsLoading) return
        hazardsLoading = true
        lifecycleScope.launch {
            try {
                hazardBundle = hazardService.fetch()
            } finally {
                hazardsLoading = false
            }
        }
    }


    private fun currentVersionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

    private fun checkForUpdate() {
        updateStatus = "Checking for update…"
        updateUrl = ""
        lifecycleScope.launch {
            updateStatus = try {
                val info = appUpdateManager.check(currentVersionName())
                if (info.updateAvailable) {
                    updateUrl = info.apkUrl.ifBlank { info.releaseUrl }
                    "HARU v" + info.remoteVersion + " is available."
                } else {
                    "HARU is up to date."
                }
            } catch (exc: Exception) {
                exc.message ?: "Could not check for update."
            }
        }
    }

    private fun cleanupLegacyStorageOnce() {
        val migration =
            getSharedPreferences(
                "haru_migrations",
                Context.MODE_PRIVATE,
            )

        if (migration.getBoolean("legacy_local_ai_removed", false)) {
            return
        }

        runCatching {
            java.io.File(filesDir, "models")
                .deleteRecursively()
            applicationContext
                .deleteSharedPreferences("haru_local_ai")
            applicationContext
                .deleteSharedPreferences("haru_model_download")
        }

        migration.edit()
            .putBoolean("legacy_local_ai_removed", true)
            .apply()
    }

    private fun openUrl(url: String) {
        val clean = url.trim()
        if (clean.length !in 1..MAX_EXTERNAL_URL_CHARS) return

        val uri = runCatching { Uri.parse(clean) }.getOrNull() ?: return
        if (!uri.scheme.equals("https", ignoreCase = true)) return
        if (uri.host.isNullOrBlank()) return

        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        }
    }

    private fun requestLocationShare(
        name: String,
        minutes: Int,
    ) {
        pendingShareName =
            name.trim().take(40).ifBlank { "Loved one" }
        pendingShareMinutes =
            minutes.coerceIn(15, 24 * 60)
        pendingLocationPurpose =
            LocationRequestPurpose.SHARE

        requestOrCaptureLocation()
    }

    private fun toggleMapGps() {
        if (mapGpsActive) {
            disableMapGps()
            return
        }

        pendingLocationPurpose = LocationRequestPurpose.MAP
        mapLocationStatus = "Preparing GPS…"

        if (!isLocationServiceEnabled()) {
            awaitingLocationSettings = true
            mapLocationStatus =
                "Phone location is off. Enable Location, then return to HARU."
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )
            }
            return
        }

        mapGpsActive = true
        requestOrCaptureLocation()
    }

    private fun disableMapGps() {
        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        activeLocationListener?.let {
            runCatching { manager.removeUpdates(it) }
        }
        activeLocationListener = null
        mainHandler.removeCallbacksAndMessages(LOCATION_TIMEOUT_TOKEN)
        pendingLocationPurpose = LocationRequestPurpose.NONE
        currentDeviceLocation = null
        mapGpsActive = false
        mapLocationStatus = "GPS marker off."
    }

    private fun isLocationServiceEnabled(): Boolean {
        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).any { provider ->
            runCatching {
                manager.isProviderEnabled(provider)
            }.getOrDefault(false)
        }
    }

    private fun requestOrCaptureLocation() {
        if (hasLocationPermission()) {
            captureRequestedLocation()
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

    @SuppressLint("MissingPermission")
    private fun captureRequestedLocation() {
        if (!hasLocationPermission()) return

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter { provider ->
            runCatching {
                manager.isProviderEnabled(provider)
            }.getOrDefault(false)
        }

        if (providers.isEmpty()) {
            if (pendingLocationPurpose == LocationRequestPurpose.MAP) {
                mapGpsActive = false
                awaitingLocationSettings = true
                mapLocationStatus =
                    "Phone location is off. Enable Location, then return to HARU."
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    )
                }
            } else {
                onlineStatus =
                    "Turn on phone location services to create a share."
            }
            return
        }

        activeLocationListener?.let {
            runCatching { manager.removeUpdates(it) }
        }
        activeLocationListener = null
        mainHandler.removeCallbacksAndMessages(LOCATION_TIMEOUT_TOKEN)

        val lastLocation = providers
            .mapNotNull { provider ->
                runCatching {
                    manager.getLastKnownLocation(provider)
                }.getOrNull()
            }
            .filter {
                it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0
            }
            .minWithOrNull(
                compareBy<Location> { it.accuracy }
                    .thenByDescending { it.time }
            )

        if (
            pendingLocationPurpose == LocationRequestPurpose.MAP &&
            lastLocation != null &&
            System.currentTimeMillis() - lastLocation.time <=
                MAP_CACHE_PREVIEW_MAX_AGE_MS
        ) {
            updateMapLocation(
                lastLocation,
                prefix = "Recent fix",
            )
        }

        if (
            pendingLocationPurpose == LocationRequestPurpose.SHARE &&
            lastLocation != null &&
            System.currentTimeMillis() - lastLocation.time <=
                SHARE_CACHE_MAX_AGE_MS &&
            lastLocation.accuracy <= SHARE_MAX_ACCURACY_M
        ) {
            handleCapturedLocation(lastLocation)
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (
                    location.latitude !in -90.0..90.0 ||
                    location.longitude !in -180.0..180.0
                ) {
                    return
                }

                when (pendingLocationPurpose) {
                    LocationRequestPurpose.MAP -> {
                        updateMapLocation(
                            location,
                            prefix = "GPS",
                        )

                        if (
                            location.accuracy <=
                            MAP_TARGET_ACCURACY_M
                        ) {
                            runCatching {
                                manager.removeUpdates(this)
                            }
                            activeLocationListener = null
                            mainHandler.removeCallbacksAndMessages(
                                LOCATION_TIMEOUT_TOKEN
                            )
                            pendingLocationPurpose =
                                LocationRequestPurpose.NONE
                        }
                    }

                    LocationRequestPurpose.SHARE -> {
                        runCatching {
                            manager.removeUpdates(this)
                        }
                        activeLocationListener = null
                        mainHandler.removeCallbacksAndMessages(
                            LOCATION_TIMEOUT_TOKEN
                        )
                        handleCapturedLocation(location)
                    }

                    LocationRequestPurpose.NONE -> {
                        runCatching {
                            manager.removeUpdates(this)
                        }
                        activeLocationListener = null
                    }
                }
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
        }

        activeLocationListener = listener

        val timeout = Runnable {
            if (activeLocationListener === listener) {
                runCatching {
                    manager.removeUpdates(listener)
                }
                activeLocationListener = null

                when (pendingLocationPurpose) {
                    LocationRequestPurpose.MAP -> {
                        mapLocationStatus =
                            currentDeviceLocation?.accuracyM?.let {
                                "Using best available fix · ±" +
                                    it.toInt() +
                                    " m"
                            } ?: "GPS timed out. Try again with a clearer sky view."
                    }

                    LocationRequestPurpose.SHARE -> {
                        if (lastLocation != null) {
                            handleCapturedLocation(lastLocation)
                            return@Runnable
                        }
                        onlineStatus =
                            "Location request timed out. Try again."
                    }

                    LocationRequestPurpose.NONE -> Unit
                }

                pendingLocationPurpose =
                    LocationRequestPurpose.NONE
            }
        }

        runCatching {
            manager.requestLocationUpdates(
                providers.first(),
                LOCATION_MIN_TIME_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )

            mainHandler.postAtTime(
                timeout,
                LOCATION_TIMEOUT_TOKEN,
                System.currentTimeMillis() +
                    LOCATION_TIMEOUT_MS,
            )
        }.onFailure {
            runCatching {
                manager.removeUpdates(listener)
            }
            activeLocationListener = null
            mainHandler.removeCallbacksAndMessages(
                LOCATION_TIMEOUT_TOKEN
            )

            if (pendingLocationPurpose == LocationRequestPurpose.MAP) {
                mapGpsActive = false
                mapLocationStatus =
                    "HARU could not start GPS."
            } else {
                onlineStatus =
                    "HARU could not obtain a phone location."
            }

            pendingLocationPurpose =
                LocationRequestPurpose.NONE
        }
    }

    private fun updateMapLocation(
        location: Location,
        prefix: String,
    ) {
        currentDeviceLocation = TrustedLocation(
            id = "haru-current-device",
            name = "My location",
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy.toDouble(),
            expiresAt = Long.MAX_VALUE,
        )

        mapGpsActive = true
        mapLocationStatus =
            prefix +
                " location · ±" +
                location.accuracy.toInt() +
                " m"
    }

    private fun handleCapturedLocation(location: Location) {
        when (pendingLocationPurpose) {
            LocationRequestPurpose.MAP -> {
                updateMapLocation(
                    location,
                    prefix = "GPS",
                )
            }

            LocationRequestPurpose.SHARE -> {
                finishLocationShare(location)
            }

            LocationRequestPurpose.NONE -> Unit
        }

        pendingLocationPurpose =
            LocationRequestPurpose.NONE
    }

    private fun finishLocationShare(location: Location) {
        runCatching {
            trustedLocationManager.createShareBundle(
                name = pendingShareName,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyM = location.accuracy.toDouble(),
                expiresMinutes = pendingShareMinutes,
            )
        }.onSuccess { bundle ->
            locationShareCode = bundle.shareText
            locationShareMapUrl = bundle.googleMapsUrl
            onlineStatus =
                "Location snapshot ready to share."
        }.onFailure {
            locationShareCode = ""
            locationShareMapUrl = ""
            onlineStatus =
                it.message ?: "Could not create location share."
        }
    }

    private fun shareLocationText() {
        val text = locationShareCode.trim()
        if (text.isBlank()) return

        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Share HARU location",
                )
            )
        }
    }

    private fun importLocationShare(code: String) {
        if (code.isBlank()) return
        runCatching {
            trustedLocationManager.importShareCode(code)
        }.onSuccess { item ->
            trustedLocations = trustedLocationManager.load()
            onlineStatus =
                "Shared location added: " + item.name
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

    override fun onResume() {
        super.onResume()

        if (
            awaitingLocationSettings &&
            isLocationServiceEnabled()
        ) {
            awaitingLocationSettings = false
            mapGpsActive = true
            pendingLocationPurpose =
                LocationRequestPurpose.MAP
            requestOrCaptureLocation()
        }

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

    override fun onStop() {
        voiceController.releaseTransientResources()
        currentDeviceLocation = null
        mapLocationStatus = ""
        locationShareCode = ""
        locationShareMapUrl = ""
        mapGpsActive = false

        if (::trustedLocationManager.isInitialized) {
            val manager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
            activeLocationListener?.let {
                runCatching { manager.removeUpdates(it) }
            }
            activeLocationListener = null
            mainHandler.removeCallbacksAndMessages(LOCATION_TIMEOUT_TOKEN)
            pendingLocationPurpose =
                LocationRequestPurpose.NONE
        }

        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        voiceController.shutdown()
        super.onDestroy()
    }

    private fun providerName(provider: OnlineProvider): String =
        when (provider) {
            OnlineProvider.ANTIGRAVITY -> "Antigravity"
            OnlineProvider.GEMINI -> selectedGeminiModel.label
        }

    companion object {
        private const val MAX_EXTERNAL_URL_CHARS = 4096
        private const val LOCATION_TIMEOUT_MS = 20_000L
        private const val LOCATION_MIN_TIME_MS = 750L
        private const val MAP_TARGET_ACCURACY_M = 25f
        private const val SHARE_MAX_ACCURACY_M = 80f
        private const val MAP_CACHE_PREVIEW_MAX_AGE_MS = 120_000L
        private const val SHARE_CACHE_MAX_AGE_MS = 30_000L
        private val LOCATION_TIMEOUT_TOKEN = Any()

        private const val SYSTEM_PROMPT =
            "You are HARU, a concise and practical personal companion. " +
                "Use local device tools for notes, tasks, reminders, voice, hazards, news, and trusted locations. " +
                "Do not claim actions you did not perform."
    }
}
