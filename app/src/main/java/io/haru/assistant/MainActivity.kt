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
import android.os.SystemClock
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
import io.haru.assistant.companion.HaruCheckerStore
import io.haru.assistant.companion.ReminderScheduler
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.CompanionModeStore
import io.haru.assistant.lockscreen.HaruLockScreenService
import io.haru.assistant.lockscreen.LockScreenPreferenceStore
import io.haru.assistant.location.HaruLiveLocationManager
import io.haru.assistant.location.LocationSharePolicy
import io.haru.assistant.location.LiveLocationException
import io.haru.assistant.location.LocationProviderPolicy
import io.haru.assistant.location.LiveLocationSession
import io.haru.assistant.location.LiveMonitorSession
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.location.TrustedLocationManager
import io.haru.assistant.ui.HaruBackgroundTheme
import io.haru.assistant.ui.HaruScreen
import io.haru.assistant.ui.HaruTheme
import io.haru.assistant.ui.HaruThemeColor
import io.haru.assistant.ui.HaruThemePreferenceStore
import io.haru.assistant.update.AndroidAppUpdateManager
import io.haru.assistant.util.cancellableResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var activeViewModel: HaruViewModel? = null

    private lateinit var companionStore: AndroidCompanionStore
    private lateinit var companionModeStore: CompanionModeStore
    private lateinit var haruCheckerStore: HaruCheckerStore
    private lateinit var lockScreenStore: LockScreenPreferenceStore
    private lateinit var themeStore: HaruThemePreferenceStore
    private lateinit var trustedLocationManager: TrustedLocationManager
    private lateinit var liveLocationManager: HaruLiveLocationManager
    private lateinit var appUpdateManager: AndroidAppUpdateManager

    private var pendingShareName = "Loved one"
    private var pendingShareMinutes = 60

    private var companionSnapshot by mutableStateOf(CompanionSnapshot())
    private var companionMode by mutableStateOf(CompanionMode.NORMAL)
    private var haruCheckerEnabled by mutableStateOf(true)
    private var lockScreenEnabled by mutableStateOf(true)
    private var themeColor by mutableStateOf(HaruThemeColor.LAVENDER)
    private var backgroundTheme by mutableStateOf(HaruBackgroundTheme.LIGHT)

    private var updateStatus by mutableStateOf("")
    private var updateUrl by mutableStateOf("")
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
    private var liveUploadJob: Job? = null
    private var liveShareCreateJob: Job? = null
    private var locationShareBusy by mutableStateOf(false)
    private var locationShareEpoch = 0L
    private var activityForeground = false
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
                        locationShareBusy = false
                        mapLocationStatus = "Location permission is needed to create a share."
                    }
                    LocationRequestPurpose.MAP -> {
                        mapGpsActive = false
                        mapLocationStatus =
                            "Location permission is needed to show your position."
                    }
                    LocationRequestPurpose.NONE -> Unit
                }
                pendingLocationPurpose = LocationRequestPurpose.NONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        companionStore = AndroidCompanionStore(applicationContext)
        companionModeStore = CompanionModeStore(applicationContext)
        haruCheckerStore = HaruCheckerStore(applicationContext)
        lockScreenStore = LockScreenPreferenceStore(applicationContext)
        themeStore = HaruThemePreferenceStore(applicationContext)
        trustedLocationManager = TrustedLocationManager(applicationContext)
        liveLocationManager = HaruLiveLocationManager()
        appUpdateManager = AndroidAppUpdateManager()

        companionSnapshot = companionStore.load()
        companionMode = companionModeStore.load()
        haruCheckerEnabled = haruCheckerStore.isEnabled()
        lockScreenEnabled = lockScreenStore.isEnabled()
        themeColor = themeStore.load()
        backgroundTheme = themeStore.loadBackground()
        cleanupLegacyStorageOnce()
        deleteSharedPreferences("haru_secure_credentials")
        deleteSharedPreferences("haru_online_ai")
        if (lockScreenEnabled) {
            requestNotificationPermissionIfNeeded()
            HaruLockScreenService.start(this)
        }
        trustedLocations = trustedLocationManager.load()
        setContent {
            HaruTheme(
                themeColor = themeColor,
                backgroundTheme = backgroundTheme,
            ) {
                val haruViewModel: HaruViewModel = viewModel()
                activeViewModel = haruViewModel
                HaruScreen(
                    journalViewModel = haruViewModel,
                    todayLines = companionSnapshot.todayLines(),
                    companionSnapshot = companionSnapshot,
                    haruCheckerEnabled = haruCheckerEnabled,
                    lockScreenEnabled = lockScreenEnabled,
                    themeColor = themeColor,
                    backgroundTheme = backgroundTheme,
                    companionQuiet = companionMode == CompanionMode.REST,
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
                    locationShareBusy = locationShareBusy,
                    liveMonitorActive = liveMonitorActive,
                    onAddCompanionTask = ::addCompanionTask,
                    onAddReminder = ::addReminder,
                    onUpdateCompanionTask = ::updateCompanionTask,
                    onDeleteCompanionTask = ::deleteCompanionTask,
                    onReorderCompanionTasks = ::reorderCompanionTasks,
                    onToggleHaruChecker = ::toggleHaruChecker,
                    onToggleLockScreen = ::toggleLockScreen,
                    onSelectThemeColor = ::selectThemeColor,
                    onSelectBackgroundTheme = ::selectBackgroundTheme,
                    onOpenAppSettings = ::openAppSettings,
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

    private fun addReminder(text: String, dueAt: Long) {
        if (text.isBlank() || dueAt <= System.currentTimeMillis()) return
        val reminder = companionStore.addReminder(text, dueAt)
        companionSnapshot = companionStore.load()
        ReminderScheduler.schedule(this, reminder)
        requestNotificationPermissionIfNeeded()
        HaruLockScreenService.refresh(this)
    }

    private fun addCompanionTask(text: String) {
        if (text.isBlank()) return
        companionSnapshot = companionStore.addTask(text)
        HaruLockScreenService.refresh(this)
    }

    private fun updateCompanionTask(
        index: Int,
        text: String,
    ) {
        if (index !in companionSnapshot.tasks.indices) return
        companionSnapshot =
            companionStore.updateTask(index, text)
        HaruLockScreenService.refresh(this)
    }

    private fun deleteCompanionTask(index: Int) {
        if (index !in companionSnapshot.tasks.indices) return
        companionSnapshot =
            companionStore.deleteTask(index)
        HaruLockScreenService.refresh(this)
    }

    private fun reorderCompanionTasks(order: List<String>) {
        companionSnapshot =
            companionStore.reorderOpenTasks(order)
        HaruLockScreenService.refresh(this)
    }

    private fun toggleHaruChecker() {
        haruCheckerEnabled = !haruCheckerEnabled
        haruCheckerStore.setEnabled(haruCheckerEnabled)
    }

    private fun toggleLockScreen() {
        lockScreenEnabled = !lockScreenEnabled
        lockScreenStore.setEnabled(lockScreenEnabled)

        if (lockScreenEnabled) {
            requestNotificationPermissionIfNeeded()
            HaruLockScreenService.start(this)
        } else {
            HaruLockScreenService.stop(this)
        }
    }

    private fun selectThemeColor(color: HaruThemeColor) {
        themeColor = color
        themeStore.save(color)
    }

    private fun selectBackgroundTheme(background: HaruBackgroundTheme) {
        backgroundTheme = background
        themeStore.saveBackground(background)
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                )
            )
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

    private fun requestLocationShare(name: String, minutes: Int) {
        if (locationShareBusy || liveShareCreateJob?.isActive == true || liveShareSession != null) return
        locationShareBusy = true
        locationShareEpoch += 1L
        pendingShareName = name.trim().take(40).ifBlank { "Loved one" }
        pendingShareMinutes = minutes.coerceIn(15, 120)
        pendingLocationPurpose = LocationRequestPurpose.SHARE
        mapLocationStatus = "Getting a recent location for sharing…"
        requestOrCaptureLocation()
    }

    private fun toggleMapGps() {
        if (mapGpsActive) {
            disableMapGps()
            return
        }

        if (locationShareBusy) return
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

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        hasFineLocationPermission() ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun enabledLocationProviders(
        manager: LocationManager,
    ): List<String> =
        LocationProviderPolicy.providers(
            gpsEnabled =
                runCatching {
                    manager.isProviderEnabled(
                        LocationManager.GPS_PROVIDER
                    )
                }.getOrDefault(false),
            networkEnabled =
                runCatching {
                    manager.isProviderEnabled(
                        LocationManager.NETWORK_PROVIDER
                    )
                }.getOrDefault(false),
            hasFineLocation = hasFineLocationPermission(),
        )

    @SuppressLint("MissingPermission")
    private fun captureRequestedLocation() {
        if (!hasLocationPermission() || pendingLocationPurpose == LocationRequestPurpose.NONE) return

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = enabledLocationProviders(manager)

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
                locationShareBusy = false
                pendingLocationPurpose = LocationRequestPurpose.NONE
                mapLocationStatus = "Turn on phone location services to create a share."
            }
            return
        }

        activeLocationListener?.let {
            runCatching { manager.removeUpdates(it) }
        }
        activeLocationListener = null
        mainHandler.removeCallbacksAndMessages(LOCATION_TIMEOUT_TOKEN)

        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val cachedLocations =
            providers
                .mapNotNull { provider ->
                    runCatching {
                        manager.getLastKnownLocation(provider)
                    }.getOrNull()
                }
                .filter {
                    LocationProviderPolicy.isValidCoordinate(
                        it.latitude,
                        it.longitude,
                    )
                }

        val mapPreview =
            cachedLocations
                .filter {
                    LocationSharePolicy.isRecentFix(it.elapsedRealtimeNanos, nowElapsedNanos, MAP_CACHE_PREVIEW_MAX_AGE_MS)
                }
                .sortedWith(
                    compareByDescending<Location> { it.elapsedRealtimeNanos }
                        .thenBy { it.accuracy }
                )
                .firstOrNull()

        val shareCached =
            cachedLocations
                .filter {
                    LocationSharePolicy.isRecentFix(it.elapsedRealtimeNanos, nowElapsedNanos, SHARE_CACHE_MAX_AGE_MS) &&
                        it.hasAccuracy() && it.accuracy.isFinite() && it.accuracy in 0f..SHARE_MAX_ACCURACY_M
                }
                .sortedWith(
                    compareByDescending<Location> { it.elapsedRealtimeNanos }
                        .thenBy { it.accuracy }
                )
                .firstOrNull()

        if (
            pendingLocationPurpose == LocationRequestPurpose.MAP &&
            mapPreview != null
        ) {
            updateMapLocation(
                mapPreview,
                prefix = "Recent fix",
            )
        }

        if (
            pendingLocationPurpose == LocationRequestPurpose.SHARE &&
            shareCached != null
        ) {
            handleCapturedLocation(shareCached)
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (
                    !LocationProviderPolicy.isValidCoordinate(
                        location.latitude,
                        location.longitude,
                    ) || !LocationSharePolicy.isRecentFix(
                        location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos(), SHARE_CACHE_MAX_AGE_MS,
                    )
                ) {
                    return
                }

                when (pendingLocationPurpose) {
                    LocationRequestPurpose.MAP -> {
                        updateMapLocation(
                            location,
                            prefix =
                                if (
                                    location.provider ==
                                    LocationManager.GPS_PROVIDER
                                ) {
                                    "GPS"
                                } else {
                                    "Approximate"
                                },
                        )

                        if (
                            location.hasAccuracy() && location.accuracy.isFinite() &&
                                location.accuracy in 0f..MAP_TARGET_ACCURACY_M
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
                        if (
                            location.hasAccuracy() && location.accuracy.isFinite() &&
                                location.accuracy in 0f..SHARE_MAX_ACCURACY_M
                        ) {
                            runCatching {
                                manager.removeUpdates(this)
                            }
                            activeLocationListener = null
                            mainHandler.removeCallbacksAndMessages(
                                LOCATION_TIMEOUT_TOKEN
                            )
                            handleCapturedLocation(location)
                        } else {
                            mapLocationStatus =
                                "Improving location accuracy… ±" +
                                    location.accuracy.toInt() +
                                    " m"
                        }
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
                        if (currentDeviceLocation == null) mapGpsActive = false
                        mapLocationStatus =
                            currentDeviceLocation?.accuracyM?.let {
                                "Using best available fix · ±" +
                                    it.toInt() +
                                    " m"
                            } ?: "GPS timed out. Try again with a clearer sky view."
                    }

                    LocationRequestPurpose.SHARE -> {
                        if (shareCached != null) {
                            handleCapturedLocation(shareCached)
                            return@Runnable
                        }
                        locationShareBusy = false
                        mapLocationStatus =
                            "Could not get a recent accurate location. Move near a window or outdoors and try again."
                    }

                    LocationRequestPurpose.NONE -> Unit
                }

                pendingLocationPurpose =
                    LocationRequestPurpose.NONE
            }
        }

        var providerStarted = false
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    LOCATION_MIN_TIME_MS,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onSuccess {
                providerStarted = true
            }
        }

        if (providerStarted) {
            mainHandler.postAtTime(
                timeout,
                LOCATION_TIMEOUT_TOKEN,
                SystemClock.uptimeMillis() +
                    LOCATION_TIMEOUT_MS,
            )
        } else {
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
                    "HARU could not start phone location."
            } else {
                locationShareBusy = false
                mapLocationStatus = "HARU could not obtain a phone location."
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
            accuracyM = if (location.hasAccuracy()) LocationSharePolicy.validAccuracy(location.accuracy.toDouble()) else null,
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
        val requestEpoch = locationShareEpoch
        val name = pendingShareName
        val minutes = pendingShareMinutes
        liveShareCreateJob = lifecycleScope.launch {
            try {
                liveTrackingStatus = "Creating secure live share…"
                cancellableResult {
                    liveLocationManager.create(
                        name = name,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyM = location.accuracy.toDouble(),
                        expiresMinutes = minutes,
                    )
                }.onSuccess { bundle ->
                    if (!activityForeground || requestEpoch != locationShareEpoch) {
                        cancellableResult { liveLocationManager.stop(bundle.session) }
                        return@onSuccess
                    }
                    liveShareSession = bundle.session
                    liveShareSeq = bundle.session.seq
                    lastLiveUploadAt = SystemClock.elapsedRealtime()
                    lastLiveUploadedLocation = Location(location)
                    locationShareCode = bundle.shareText
                    locationShareMapUrl = bundle.googleMapsUrl
                    liveShareActive = true
                    liveTrackingStatus = "Live sharing active · updates pause in the background."
                    mapLocationStatus = "Live share ready."
                    startLiveLocationPublisher()
                }.onFailure {
                    if (requestEpoch == locationShareEpoch) {
                        liveTrackingStatus = it.message ?: "Could not create live location share."
                    }
                }
            } finally {
                if (requestEpoch == locationShareEpoch) locationShareBusy = false
                liveShareCreateJob = null
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
        runCatching {
            val monitor = liveLocationManager.parseLiveCode(code)
            if (monitor != null) {
                startLiveMonitoring(monitor)
            } else {
                val item = trustedLocationManager.importShareCode(code)
                trustedLocations = trustedLocationManager.load()
                mapLocationStatus = "Shared location added: " + item.name
            }
        }.onFailure {
            mapLocationStatus = it.message ?: "Could not import location share."
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationPublisher() {
        val session = liveShareSession ?: return
        if (!hasLocationPermission() || !isLocationServiceEnabled()) {
            liveTrackingStatus =
                "Live share paused · location permission/service unavailable."
            return
        }

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        liveShareLocationListener?.let {
            runCatching { manager.removeUpdates(it) }
        }

        val providers = enabledLocationProviders(manager)
        if (providers.isEmpty()) {
            liveShareLocationListener = null
            liveTrackingStatus =
                "Live share paused · no usable location provider."
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!liveShareActive || !activityForeground || liveShareSession != session) return
                if (System.currentTimeMillis() >= session.expiresAt) {
                    stopLiveSharing()
                    liveTrackingStatus = "Live share expired."
                    return
                }
                if (
                    location.latitude !in -90.0..90.0 ||
                    location.longitude !in -180.0..180.0 || !location.hasAccuracy() ||
                    !location.accuracy.isFinite() || location.accuracy !in 0f..SHARE_MAX_ACCURACY_M ||
                    !LocationSharePolicy.isRecentFix(location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos(), SHARE_CACHE_MAX_AGE_MS)
                ) {
                    return
                }

                val now = SystemClock.elapsedRealtime()
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
                lastLiveUploadAt = now
                val uploadEpoch = locationShareEpoch
                liveUploadJob = lifecycleScope.launch {
                    try {
                        cancellableResult {
                            liveLocationManager.update(
                                session = session,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracyM = location.accuracy.toDouble(),
                                expectedSeq = liveShareSeq,
                            )
                        }.onSuccess { nextSeq ->
                            if (!activityForeground || liveShareSession != session) return@onSuccess
                            liveShareSeq = nextSeq
                            lastLiveUploadAt = SystemClock.elapsedRealtime()
                            lastLiveUploadedLocation =
                                Location(location)
                            liveTrackingStatus =
                                "Live sharing · ±" +
                                    location.accuracy.toInt() +
                                    " m · updated now"
                        }.onFailure {
                            if (!activityForeground || liveShareSession != session) return@onFailure
                            if (it is LiveLocationException && it.terminal) {
                                stopLiveSharing()
                                liveTrackingStatus = it.message.orEmpty()
                                return@onFailure
                            }
                            liveTrackingStatus =
                                "Live share retrying · " +
                                    (it.message ?: "network unavailable")
                        }
                    } finally {
                        if (uploadEpoch == locationShareEpoch) {
                            liveUploadBusy = false
                            liveUploadJob = null
                        }
                    }
                }
            }

            override fun onProviderDisabled(provider: String) {
                liveTrackingStatus =
                    "Live share paused · phone location is off."
            }

            override fun onProviderEnabled(provider: String) = Unit
        }

        liveShareLocationListener = listener

        var providerStarted = false
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    LIVE_LOCATION_SAMPLE_MS,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onSuccess {
                providerStarted = true
            }
        }

        if (!providerStarted) {
            runCatching {
                manager.removeUpdates(listener)
            }
            liveShareLocationListener = null
            liveTrackingStatus =
                "Could not start live location updates."
        }
    }

    private fun stopLiveSharing() {
        val session = liveShareSession
        locationShareEpoch += 1L
        locationShareBusy = false
        if (pendingLocationPurpose == LocationRequestPurpose.SHARE) disableMapGps()
        liveShareActive = false
        liveUploadJob?.cancel()
        liveUploadJob = null

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
                cancellableResult { liveLocationManager.stop(session) }.onFailure {
                    if (liveShareSession == null && (it !is LiveLocationException || !it.terminal)) {
                        liveTrackingStatus = "Stopped on this phone. Could not revoke the shared code; it expires automatically."
                    }
                }
            }
        }

        liveShareSession = null
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
        liveTrackingStatus = "Connecting to live location…"

        liveMonitorJob = lifecycleScope.launch {
            var retryDelay = LIVE_MONITOR_INTERVAL_MS

            while (
                isActive &&
                liveMonitorActive &&
                liveMonitorSession == monitor
            ) {
                val result = cancellableResult { liveLocationManager.read(monitor) }
                if (!isActive || !activityForeground || liveMonitorSession != monitor) return@launch

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
                    if (it is LiveLocationException && it.terminal) {
                        liveMonitorActive = false
                        liveMonitorSession = null
                        liveTrackedLocation = null
                    }
                    liveTrackingStatus =
                        it.message ?: "Live location temporarily unavailable."
                    retryDelay =
                        (retryDelay * 2L)
                            .coerceAtMost(
                                LIVE_MONITOR_MAX_RETRY_MS
                            )
                }

                if (!liveMonitorActive) break
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

        if (clearStatus) {
            liveTrackingStatus =
                "Live monitoring stopped."
        }
    }

    private fun clearTrustedLocations() {
        trustedLocationManager.clear()
        trustedLocations = emptyList()
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

    override fun onResume() {
        super.onResume()
        activityForeground = true
        if (liveShareSession?.let { it.expiresAt <= System.currentTimeMillis() } == true) {
            stopLiveSharing()
            liveTrackingStatus = "Live share expired."
        }

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
        if (::haruCheckerStore.isInitialized) {
            haruCheckerEnabled =
                haruCheckerStore.isEnabled()
        }
        if (::lockScreenStore.isInitialized) {
            lockScreenEnabled =
                lockScreenStore.isEnabled()
            if (lockScreenEnabled) {
                HaruLockScreenService.start(this)
            }
        }
        if (::trustedLocationManager.isInitialized) {
            trustedLocations = trustedLocationManager.load()
        }
    }

    override fun onStop() {
        activityForeground = false
        locationShareEpoch += 1L
        locationShareBusy = false
        liveUploadJob?.cancel()
        liveUploadJob = null
        liveUploadBusy = false
        activeViewModel?.flush()
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

        if (liveShareCreateJob?.isActive == true) {
            liveTrackingStatus = "Share creation cancelled while HARU is in the background."
        } else if (liveShareActive) {
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
        super.onDestroy()
    }

    companion object {
        private const val MAX_EXTERNAL_URL_CHARS = 4096
        private const val LOCATION_TIMEOUT_MS = 20_000L
        private const val LOCATION_MIN_TIME_MS = 750L
        private const val MAP_TARGET_ACCURACY_M = 25f
        private const val SHARE_MAX_ACCURACY_M = 80f
        private const val MAP_CACHE_PREVIEW_MAX_AGE_MS = 120_000L
        private const val SHARE_CACHE_MAX_AGE_MS = 30_000L

        // Live location is explicitly user-enabled. Keep sampling and network
        // cadence conservative to limit GPS/radio wakeups while retaining
        // useful movement updates.
        private const val LIVE_LOCATION_SAMPLE_MS = 5_000L
        private const val LIVE_UPLOAD_MIN_INTERVAL_MS = 10_000L
        private const val LIVE_HEARTBEAT_INTERVAL_MS = 30_000L
        private const val LIVE_MONITOR_INTERVAL_MS = 10_000L
        private const val LIVE_MONITOR_MAX_RETRY_MS = 60_000L
        private const val LIVE_STALE_AFTER_MS = 45_000L
        private const val LIVE_MIN_MOVE_M = 5f
        private const val LIVE_ACCURACY_IMPROVEMENT_M = 8f

        private val LOCATION_TIMEOUT_TOKEN = Any()

    }
}
