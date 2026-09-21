package io.haru.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.companion.HaruBubbleStatus
import io.haru.assistant.companion.HaruBubbleActionReceiver
import io.haru.assistant.companion.HaruBubbleStore
import io.haru.assistant.companion.HaruBubbleWidgetProvider
import io.haru.assistant.companion.ReminderScheduler
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.CompanionModeStore
import io.haru.assistant.location.HaruLiveLocationManager
import io.haru.assistant.location.LiveLocationSession
import io.haru.assistant.location.LiveMonitorSession
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.location.TrustedLocationManager
import io.haru.assistant.memory.AntigravitySession
import io.haru.assistant.memory.ConversationExchange
import io.haru.assistant.memory.ConversationMemoryPolicy
import io.haru.assistant.memory.EncryptedConversationStore
import io.haru.assistant.onlineai.AndroidOnlineAiManager
import io.haru.assistant.onlineai.GeminiModel
import io.haru.assistant.onlineai.OnlineProvider
import io.haru.assistant.ui.HaruScreen
import io.haru.assistant.ui.HaruTheme
import io.haru.assistant.update.AndroidAppUpdateManager
import io.haru.assistant.voice.HaruVoiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity(), HaruVoiceController.Callbacks {

    private var activeViewModel: HaruViewModel? = null

    private lateinit var voiceController: HaruVoiceController
    private lateinit var onlineAiManager: AndroidOnlineAiManager
    private lateinit var companionStore: AndroidCompanionStore
    private lateinit var companionModeStore: CompanionModeStore
    private lateinit var haruBubbleStore: HaruBubbleStore
    private lateinit var trustedLocationManager: TrustedLocationManager
    private lateinit var liveLocationManager: HaruLiveLocationManager
    private lateinit var appUpdateManager: AndroidAppUpdateManager
    private lateinit var conversationStore: EncryptedConversationStore

    private var pendingVoiceStart = false
    private var pendingShareName = "Loved one"
    private var pendingShareMinutes = 60

    private var companionSnapshot by mutableStateOf(CompanionSnapshot())
    private var companionMode by mutableStateOf(CompanionMode.NORMAL)
    private var haruBubbleEnabled by mutableStateOf(true)
    private var haruBubbleStatus by mutableStateOf(HaruBubbleStatus())
    private var pendingBubblePrompt by mutableStateOf<String?>(null)
    private var openCompanionRequest by mutableStateOf(0)

    private var onlineProvider by mutableStateOf(OnlineProvider.ANTIGRAVITY)
    private var selectedGeminiModel by mutableStateOf(AndroidOnlineAiManager.FALLBACK_GEMINI_MODEL)
    private var geminiModels by mutableStateOf(listOf(AndroidOnlineAiManager.FALLBACK_GEMINI_MODEL))
    private var onlineStatus by mutableStateOf("Antigravity is the online default.")
    private var hasGeminiKey by mutableStateOf(false)
    private var hasGroqKey by mutableStateOf(false)
    private var updateStatus by mutableStateOf("")
    private var updateUrl by mutableStateOf("")
    private var conversationHistory by mutableStateOf(emptyList<ConversationExchange>())
    private var conversationSummary by mutableStateOf("")
    private var antigravitySession: AntigravitySession? = null
    private var conversationEpoch = 0L
    private var activeAiJob: Job? = null

    private var trustedLocations by mutableStateOf(emptyList<TrustedLocation>())
    private var currentDeviceLocation by mutableStateOf<TrustedLocation?>(null)
    private var mapGpsActive by mutableStateOf(false)
    private var locationShareCode by mutableStateOf("")
    private var locationShareMapUrl by mutableStateOf("")
    private var mapLocationStatus by mutableStateOf("")
    private var awaitingLocationSettings = false

    private var liveShareSession: LiveLocationSession? = null
    private var liveMonitorSession: LiveMonitorSession? = null
    private var liveTrackedLocation by mutableStateOf<TrustedLocation?>(null)
    private var liveTrackingStatus by mutableStateOf("")
    private var liveShareActive by mutableStateOf(false)
    private var liveMonitorActive by mutableStateOf(false)
    private var liveShareSeq = 0L
    private var liveShareLocationListener: LocationListener? = null
    private var liveMonitorJob: Job? = null
    private var liveUploadBusy = false
    private var lastLiveUploadAt = 0L
    private var lastLiveUploadedLocation: Location? = null

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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ReminderScheduler.rescheduleAll(this)
        }

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
                        mapGpsActive = false
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
        companionModeStore = CompanionModeStore(applicationContext)
        haruBubbleStore = HaruBubbleStore(applicationContext)
        trustedLocationManager = TrustedLocationManager(applicationContext)
        liveLocationManager = HaruLiveLocationManager()
        appUpdateManager = AndroidAppUpdateManager()
        conversationStore = EncryptedConversationStore(applicationContext)

        companionSnapshot = companionStore.load()
        companionMode = companionModeStore.load()
        haruBubbleEnabled = haruBubbleStore.isEnabled()
        cleanupLegacyStorageOnce()
        refreshCompanionSurface()
        val onlineSettings = onlineAiManager.settings()
        onlineProvider = onlineSettings.provider
        selectedGeminiModel = onlineSettings.geminiModel
        geminiModels = onlineAiManager.geminiModels()
        refreshOnlineKeyState()
        trustedLocations = trustedLocationManager.load()
        val memoryState = conversationStore.loadState()
        conversationHistory = memoryState.exchanges
        conversationSummary = memoryState.summary
        antigravitySession =
            memoryState.antigravitySession
                ?.takeIf { it.isFresh() }

        receiveBubbleIntent(intent)
        refreshBubbleStatus()

        setContent {
            HaruTheme {
                val haruViewModel: HaruViewModel = viewModel()
                activeViewModel = haruViewModel
                LaunchedEffect(pendingBubblePrompt) {
                    pendingBubblePrompt?.let { prompt ->
                        if (!haruViewModel.uiState.isBusy && haruViewModel.uiState.command.isBlank()) {
                            haruViewModel.setCompanionDraft(prompt)
                        }
                        // Never send a network request, interrupt a reply, or replace a user's draft.
                        pendingBubblePrompt = null
                    }
                }

                HaruScreen(
                    viewModel = haruViewModel,
                    todayLines = companionSnapshot.todayLines(),
                    companionSnapshot = companionSnapshot,
                    haruBubbleEnabled = haruBubbleEnabled,
                    haruBubbleStatus = haruBubbleStatus,
                    companionQuiet = companionMode == CompanionMode.REST,
                    openCompanionRequest = openCompanionRequest,
                    onlineProvider = onlineProvider,
                    selectedGeminiModel = selectedGeminiModel,
                    geminiModels = geminiModels,
                    onlineStatus = onlineStatus,
                    hasGeminiKey = hasGeminiKey,
                    hasGroqKey = hasGroqKey,
                    memoryCount = conversationHistory.size,
                    appVersion = currentVersionName(),
                    updateStatus = updateStatus,
                    updateUrl = updateUrl,
                    trustedLocations = trustedLocations,
                    currentDeviceLocation = currentDeviceLocation,
                    mapGpsActive = mapGpsActive,
                    locationShareCode = locationShareCode,
                    locationShareMapUrl = locationShareMapUrl,
                    mapLocationStatus = mapLocationStatus,
                    liveTrackedLocation = liveTrackedLocation,
                    liveTrackingStatus = liveTrackingStatus,
                    liveShareActive = liveShareActive,
                    liveMonitorActive = liveMonitorActive,
                    onSubmitClick = {
                        submitWithAi(haruViewModel, speakResult = false)
                    },
                    onMicClick = { requestVoiceRecognition() },
                    onAddCompanionTask = ::addCompanionTask,
                    onUpdateCompanionTask = ::updateCompanionTask,
                    onDeleteCompanionTask = ::deleteCompanionTask,
                    onRequestHaruBubble = ::requestHaruBubble,
                    onRefreshBubbleStatus = ::refreshBubbleStatus,
                    onToggleHaruBubble = ::toggleHaruBubble,
                    onSelectOnlineProvider = ::selectOnlineProvider,
                    onSelectGeminiModel = ::selectGeminiModel,
                    onRefreshGeminiModels = ::refreshGeminiModels,
                    onSaveGeminiKey = ::saveGeminiKey,
                    onSaveGroqKey = ::saveGroqKey,
                    onTestOnlineAi = ::testOnlineAi,
                    onResetMemory = { resetConversationMemory(haruViewModel) },
                    onCheckUpdate = ::checkForUpdate,
                    onOpenUpdate = ::openUrl,
                    onOpenUrl = ::openUrl,
                    onLocateMe = ::toggleMapGps,
                    onCreateLocationShare = ::requestLocationShare,
                    onShareLocation = ::shareLocationText,
                    onImportLocationShare = ::importLocationShare,
                    onStopLiveShare = ::stopLiveSharing,
                    onStopLiveMonitor = ::stopLiveMonitoring,
                    onClearTrustedLocations = ::clearTrustedLocations,
                )
            }
        }
    }

    private fun submitWithAi(
        viewModel: HaruViewModel,
        speakResult: Boolean,
    ) {
        if (!viewModel.uiState.canSubmit) return
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

        val requestEpoch = conversationEpoch
        val requestHistory = conversationHistory
        val requestSummary = conversationSummary
        val requestCompanionMode = companionMode
        val requestAntigravitySession =
            antigravitySession?.takeIf { it.isFresh() }

        activeAiJob?.cancel()
        activeAiJob = lifecycleScope.launch {
            try {
                val reply = onlineAiManager.ask(
                    provider = onlineProvider,
                    prompt = prompt,
                    systemPrompt = systemPromptFor(requestCompanionMode),
                    history = requestHistory,
                    summary = requestSummary,
                    antigravitySession =
                        if (onlineProvider == OnlineProvider.ANTIGRAVITY) {
                            requestAntigravitySession
                        } else {
                            null
                        },
                )

                if (requestEpoch != conversationEpoch) {
                    return@launch
                }

                if (onlineProvider == OnlineProvider.ANTIGRAVITY) {
                    antigravitySession =
                        reply.antigravitySession
                }

                val memoryState =
                    conversationStore.appendCompleted(
                        user = prompt,
                        assistant = reply.text,
                        antigravitySession =
                            if (onlineProvider == OnlineProvider.ANTIGRAVITY) {
                                antigravitySession
                            } else {
                                null
                            },
                    )

                conversationHistory = memoryState.exchanges
                conversationSummary = memoryState.summary

                onlineStatus =
                    providerName(onlineProvider) +
                        " connected · memory " +
                        conversationHistory.size +
                        "/" +
                        ConversationMemoryPolicy.MAX_EXCHANGES +
                        if (conversationSummary.isNotBlank()) {
                            " + recap"
                        } else {
                            ""
                        }
                viewModel.completeAi(reply.text, success = true)
                if (speakResult) voiceController.speak(reply.text)
            } catch (exc: Exception) {
                if (requestEpoch != conversationEpoch) {
                    return@launch
                }

                val message =
                    exc.message ?: "HARU could not complete that request."
                viewModel.completeAi(message, success = false)
                if (speakResult) voiceController.speak(message)
            } finally {
                if (requestEpoch == conversationEpoch) {
                    activeAiJob = null
                }
            }
        }
    }

    private fun refreshCompanionSurface() {
        HaruBubbleWidgetProvider.refreshAll(
            applicationContext
        )
    }

    private fun addCompanionTask(text: String) {
        if (text.isBlank()) return
        companionSnapshot = companionStore.addTask(text)
        refreshCompanionSurface()
    }

    private fun updateCompanionTask(
        index: Int,
        text: String,
    ) {
        if (index !in companionSnapshot.tasks.indices) return
        companionSnapshot =
            companionStore.updateTask(index, text)
        refreshCompanionSurface()
    }

    private fun deleteCompanionTask(index: Int) {
        if (index !in companionSnapshot.tasks.indices) return
        companionSnapshot =
            companionStore.deleteTask(index)
        refreshCompanionSurface()
    }

    private fun selectCompanionMode(mode: CompanionMode) {
        companionMode = mode
        companionModeStore.save(mode)
        refreshCompanionSurface()
        activeViewModel?.completeAi(
            mode.activationMessage,
            success = true,
        )
    }

    private fun toggleHaruBubble() {
        haruBubbleEnabled = !haruBubbleEnabled
        if (!haruBubbleEnabled) activeViewModel?.clearCompanionDraft()
        haruBubbleStore.setEnabled(haruBubbleEnabled)
        refreshCompanionSurface()
        refreshBubbleStatus()
    }

    private fun refreshBubbleStatus() {
        haruBubbleStatus = HaruBubbleStatus(
            installedCount = HaruBubbleWidgetProvider.installedCount(this),
            enabled = haruBubbleStore.isEnabled(),
            pinSupported = AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported,
        )
    }

    private fun requestHaruBubble() {
        refreshBubbleStatus()
        val manager = AppWidgetManager.getInstance(this)
        if (!haruBubbleStatus.pinSupported) return // Setup always displays manual launcher steps.
        val callback = PendingIntent.getBroadcast(
            this, 7003,
            Intent(this, HaruBubbleActionReceiver::class.java).setAction(HaruBubbleWidgetProvider.ACTION_PINNED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // true means a request was accepted, not that a widget was installed.
        val requested = runCatching {
            manager.requestPinAppWidget(
                ComponentName(this, HaruBubbleWidgetProvider::class.java), null, callback,
            )
        }.getOrDefault(false)
        haruBubbleStatus = haruBubbleStatus.copy(requestPending = requested)
    }

    private fun receiveBubbleIntent(incoming: Intent?) {
        if (incoming?.action != HaruBubbleWidgetProvider.ACTION_CHAT) return
        openCompanionRequest += 1
        pendingBubblePrompt = incoming.getStringExtra(HaruBubbleWidgetProvider.EXTRA_PROMPT)
            ?.take(500)?.takeIf { it.isNotBlank() }
        incoming.removeExtra(HaruBubbleWidgetProvider.EXTRA_PROMPT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveBubbleIntent(intent)
    }

    private fun resetConversationMemory(
        viewModel: HaruViewModel,
    ) {
        conversationEpoch += 1L
        activeAiJob?.cancel()
        activeAiJob = null
        conversationStore.clear()
        conversationHistory = emptyList()
        conversationSummary = ""
        antigravitySession = null
        onlineStatus = "Conversation memory cleared · 0/" +
            ConversationMemoryPolicy.MAX_EXCHANGES
        viewModel.resetConversation()
    }

    private fun selectOnlineProvider(provider: OnlineProvider) {
        onlineProvider = provider
        onlineAiManager.saveProvider(provider)
        if (provider != OnlineProvider.ANTIGRAVITY) {
            antigravitySession = null
            conversationStore.saveAntigravitySession(null)
        }
        onlineStatus = providerName(provider) + " selected."
    }

    private fun saveGeminiKey(value: String) {
        if (value.isBlank()) return
        onlineAiManager.saveGeminiKey(value)
        refreshOnlineKeyState()
        onlineStatus = "Gemini key saved securely on this phone."
    }

    private fun saveGroqKey(value: String) {
        if (value.isBlank()) return
        onlineAiManager.saveGroqKey(value)
        refreshOnlineKeyState()
        onlineStatus = "Groq key saved securely on this phone."
    }

    private fun selectGeminiModel(model: GeminiModel) {
        selectedGeminiModel = model
        onlineAiManager.saveGeminiModel(model)
        onlineStatus = model.label + " selected."
    }

    private fun refreshOnlineKeyState() {
        hasGeminiKey = onlineAiManager.hasGeminiKey()
        hasGroqKey = onlineAiManager.hasGroqKey()
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
        lifecycleScope.launch {
            liveTrackingStatus = "Creating secure live share…"

            runCatching {
                liveLocationManager.create(
                    name = pendingShareName,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = location.accuracy.toDouble(),
                    expiresMinutes = pendingShareMinutes,
                )
            }.onSuccess { bundle ->
                liveShareSession = bundle.session
                liveShareSeq = bundle.session.seq
                locationShareCode = bundle.shareText
                locationShareMapUrl = bundle.googleMapsUrl
                liveShareActive = true
                refreshCompanionSurface()
                liveTrackingStatus =
                    "Live sharing active · updates about every 4–12 seconds."
                startLiveLocationPublisher()
            }.onFailure {
                liveShareSession = null
                liveShareActive = false
                locationShareCode = ""
                locationShareMapUrl = ""
                liveTrackingStatus =
                    it.message ?: "Could not create live location share."
            }
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

        liveLocationManager.parseLiveCode(code)?.let { monitor ->
            startLiveMonitoring(monitor)
            return
        }

        runCatching {
            trustedLocationManager.importShareCode(code)
        }.onSuccess { item ->
            trustedLocations = trustedLocationManager.load()
            onlineStatus =
                "Shared location added: " + item.name
        }.onFailure {
            onlineStatus =
                it.message ?: "Could not import location share."
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationPublisher() {
        val session = liveShareSession ?: return
        if (!hasLocationPermission() || !isLocationServiceEnabled()) {
            liveShareActive = false
            liveTrackingStatus =
                "Live share paused · location permission/service unavailable."
            return
        }

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        liveShareLocationListener?.let {
            runCatching { manager.removeUpdates(it) }
        }

        val provider =
            if (runCatching {
                    manager.isProviderEnabled(
                        LocationManager.GPS_PROVIDER
                    )
                }.getOrDefault(false)
            ) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!liveShareActive) return
                if (
                    location.latitude !in -90.0..90.0 ||
                    location.longitude !in -180.0..180.0
                ) {
                    return
                }

                val now = System.currentTimeMillis()
                val previous = lastLiveUploadedLocation
                val movedM =
                    previous?.distanceTo(location) ?: Float.MAX_VALUE
                val accuracyImproved =
                    previous != null &&
                        previous.accuracy - location.accuracy >=
                            LIVE_ACCURACY_IMPROVEMENT_M
                val heartbeatDue =
                    now - lastLiveUploadAt >=
                        LIVE_HEARTBEAT_INTERVAL_MS
                val normalDue =
                    now - lastLiveUploadAt >=
                        LIVE_UPLOAD_MIN_INTERVAL_MS

                if (
                    liveUploadBusy ||
                    (!heartbeatDue &&
                        !(normalDue &&
                            (
                                movedM >= LIVE_MIN_MOVE_M ||
                                    accuracyImproved
                                )
                            )
                        )
                ) {
                    return
                }

                liveUploadBusy = true
                lifecycleScope.launch {
                    runCatching {
                        liveLocationManager.update(
                            session = session,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyM = location.accuracy.toDouble(),
                            expectedSeq = liveShareSeq,
                        )
                    }.onSuccess { nextSeq ->
                        liveShareSeq = nextSeq
                        lastLiveUploadAt =
                            System.currentTimeMillis()
                        lastLiveUploadedLocation =
                            Location(location)
                        liveTrackingStatus =
                            "Live sharing · ±" +
                                location.accuracy.toInt() +
                                " m · updated now"
                    }.onFailure {
                        liveTrackingStatus =
                            "Live share retrying · " +
                                (it.message ?: "network unavailable")
                    }
                    liveUploadBusy = false
                }
            }

            override fun onProviderDisabled(provider: String) {
                liveTrackingStatus =
                    "Live share paused · phone location is off."
            }

            override fun onProviderEnabled(provider: String) = Unit
        }

        liveShareLocationListener = listener

        runCatching {
            manager.requestLocationUpdates(
                provider,
                LIVE_LOCATION_SAMPLE_MS,
                LIVE_MIN_MOVE_M,
                listener,
                Looper.getMainLooper(),
            )
        }.onFailure {
            liveShareLocationListener = null
            liveShareActive = false
            liveTrackingStatus =
                "Could not start live location updates."
        }
    }

    private fun stopLiveSharing() {
        val session = liveShareSession
        liveShareActive = false

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        liveShareLocationListener?.let {
            runCatching { manager.removeUpdates(it) }
        }
        liveShareLocationListener = null
        lastLiveUploadedLocation = null
        lastLiveUploadAt = 0L
        liveUploadBusy = false

        if (session != null) {
            lifecycleScope.launch {
                liveLocationManager.stop(session)
            }
        }

        liveShareSession = null
        refreshCompanionSurface()
        locationShareCode = ""
        locationShareMapUrl = ""
        liveTrackingStatus = "Live sharing stopped."
    }

    private fun startLiveMonitoring(
        monitor: LiveMonitorSession,
    ) {
        stopLiveMonitoring(clearStatus = false)
        liveMonitorSession = monitor
        liveMonitorActive = true
        refreshCompanionSurface()
        liveTrackingStatus = "Connecting to live location…"

        liveMonitorJob = lifecycleScope.launch {
            var retryDelay = LIVE_MONITOR_INTERVAL_MS

            while (
                isActive &&
                liveMonitorActive &&
                liveMonitorSession == monitor
            ) {
                val result = runCatching {
                    liveLocationManager.read(monitor)
                }

                result.onSuccess { snapshot ->
                    val ageMs =
                        System.currentTimeMillis() -
                            snapshot.capturedAt

                    liveTrackedLocation = TrustedLocation(
                        id = "haru-live-" +
                            snapshot.sessionId,
                        name = snapshot.name,
                        latitude = snapshot.latitude,
                        longitude = snapshot.longitude,
                        accuracyM = snapshot.accuracyM,
                        expiresAt = snapshot.expiresAt,
                    )

                    liveTrackingStatus =
                        when {
                            ageMs <= LIVE_STALE_AFTER_MS ->
                                "Live · updated " +
                                    (ageMs / 1000L).coerceAtLeast(0L) +
                                    "s ago"
                            else ->
                                "Live share is stale · last update " +
                                    (ageMs / 1000L) +
                                    "s ago"
                        }

                    retryDelay = LIVE_MONITOR_INTERVAL_MS
                }.onFailure {
                    liveTrackingStatus =
                        it.message ?: "Live location temporarily unavailable."
                    retryDelay =
                        (retryDelay * 2L)
                            .coerceAtMost(
                                LIVE_MONITOR_MAX_RETRY_MS
                            )
                }

                delay(retryDelay)
            }
        }
    }

    private fun stopLiveMonitoring(
        clearStatus: Boolean = true,
    ) {
        liveMonitorActive = false
        liveMonitorJob?.cancel()
        liveMonitorJob = null
        liveMonitorSession = null
        liveTrackedLocation = null
        refreshCompanionSurface()

        if (clearStatus) {
            liveTrackingStatus =
                "Live monitoring stopped."
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

        if (
            low == "what mode" ||
            low == "current mode" ||
            low == "haru mode" ||
            low == "what mode are you in"
        ) {
            return companionMode.label +
                " mode · " +
                companionMode.role +
                " · " +
                companionMode.priority
        }

        CompanionMode.fromCommand(clean)?.let { mode ->
            selectCompanionMode(mode)
            return mode.activationMessage
        }

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
                refreshCompanionSurface()
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
                refreshCompanionSurface()
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
                refreshCompanionSurface()
                return "All reminders cleared."
            }
        }

        Regex("(?i)^(?:remember that|remember|note|save note)\\s+(.+)$")
            .matchEntire(clean)
            ?.let { match ->
                companionSnapshot = companionStore.addNote(match.groupValues[1])
                refreshCompanionSurface()
                return "Noted: " + match.groupValues[1].trim()
            }

        Regex("(?i)^(?:task|add task|todo|to-do|add to tasks)\\s+(.+)$")
            .matchEntire(clean)
            ?.let { match ->
                companionSnapshot = companionStore.addTask(match.groupValues[1])
                refreshCompanionSurface()
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
                refreshCompanionSurface()
                return "Completed: " + taskText
            }

        companionStore.parseRelativeReminder(clean)?.let { (text, dueAt) ->
            val reminder = companionStore.addReminder(text, dueAt)
            companionSnapshot = companionStore.load()
            ReminderScheduler.schedule(this, reminder)
            requestNotificationPermissionIfNeeded()
            refreshCompanionSurface()

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
        val viewModel = activeViewModel ?: return
        if (viewModel.uiState.isBusy) return
        viewModel.setListening()

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

        if (
            liveShareActive &&
            liveShareSession != null &&
            liveShareLocationListener == null
        ) {
            startLiveLocationPublisher()
        }

        if (
            liveMonitorActive &&
            liveMonitorSession != null &&
            liveMonitorJob == null
        ) {
            startLiveMonitoring(
                liveMonitorSession!!
            )
        }

        if (::companionStore.isInitialized) {
            companionSnapshot = companionStore.load()
            ReminderScheduler.rescheduleAll(this)
        }
        if (::haruBubbleStore.isInitialized) {
            haruBubbleEnabled =
                haruBubbleStore.isEnabled()
            refreshBubbleStatus()
            refreshCompanionSurface()
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
        viewModel.cancelListening()
        viewModel.updateCommand(text)
        submitWithAi(viewModel, speakResult = true)
    }

    override fun onVoiceError(message: String) {
        activeViewModel?.cancelListening(message)
    }

    override fun onRuntimeChanged(
        status: HaruVoiceController.VoiceRuntimeStatus,
    ) = Unit

    override fun onStop() {
        voiceController.releaseTransientResources()
        currentDeviceLocation = null
        mapLocationStatus = ""
        mapGpsActive = false

        if (::trustedLocationManager.isInitialized) {
            val manager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager

            activeLocationListener?.let {
                runCatching { manager.removeUpdates(it) }
            }
            activeLocationListener = null

            liveShareLocationListener?.let {
                runCatching { manager.removeUpdates(it) }
            }
            liveShareLocationListener = null

            liveMonitorJob?.cancel()
            liveMonitorJob = null

            mainHandler.removeCallbacksAndMessages(
                LOCATION_TIMEOUT_TOKEN
            )
            pendingLocationPurpose =
                LocationRequestPurpose.NONE
        }

        if (liveShareActive) {
            liveTrackingStatus =
                "Live share paused while HARU is in the background."
        } else if (liveMonitorActive) {
            liveTrackingStatus =
                "Live monitoring paused while HARU is in the background."
        }

        super.onStop()
    }

    override fun onDestroy() {
        liveMonitorJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        voiceController.shutdown()
        super.onDestroy()
    }

    private fun systemPromptFor(mode: CompanionMode): String =
        SYSTEM_PROMPT +
            " Current companion mode: " +
            mode.label +
            ". " +
            mode.aiGuidance

    private fun providerName(provider: OnlineProvider): String =
        when (provider) {
            OnlineProvider.ANTIGRAVITY -> "Antigravity"
            OnlineProvider.GEMINI -> selectedGeminiModel.label
            OnlineProvider.GROQ -> "Groq · Qwen 3.8 27B"
        }

    companion object {
        private const val MAX_EXTERNAL_URL_CHARS = 4096
        private const val LOCATION_TIMEOUT_MS = 20_000L
        private const val LOCATION_MIN_TIME_MS = 750L
        private const val MAP_TARGET_ACCURACY_M = 25f
        private const val SHARE_MAX_ACCURACY_M = 80f
        private const val MAP_CACHE_PREVIEW_MAX_AGE_MS = 120_000L
        private const val SHARE_CACHE_MAX_AGE_MS = 30_000L

        private const val LIVE_LOCATION_SAMPLE_MS = 2_000L
        private const val LIVE_UPLOAD_MIN_INTERVAL_MS = 4_000L
        private const val LIVE_HEARTBEAT_INTERVAL_MS = 12_000L
        private const val LIVE_MONITOR_INTERVAL_MS = 4_000L
        private const val LIVE_MONITOR_MAX_RETRY_MS = 20_000L
        private const val LIVE_STALE_AFTER_MS = 15_000L
        private const val LIVE_MIN_MOVE_M = 2.5f
        private const val LIVE_ACCURACY_IMPROVEMENT_M = 5f

        private val LOCATION_TIMEOUT_TOKEN = Any()

        private const val SYSTEM_PROMPT =
            "You are HARU, a concise and practical personal companion. " +
                "Use local device tools for notes, tasks, reminders, voice, hazards, news, and trusted locations. " +
                "Do not claim actions you did not perform."
    }
}
