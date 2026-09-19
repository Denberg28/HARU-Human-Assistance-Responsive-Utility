package io.haru.assistant.ui

import android.view.MotionEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.haru.assistant.HaruViewModel
import io.haru.assistant.content.AndroidHazardBundle
import io.haru.assistant.content.AndroidHazardItem
import io.haru.assistant.content.AndroidNewsBundle
import io.haru.assistant.content.AndroidNewsItem
import io.haru.assistant.core.HaruMood
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.onlineai.GeminiModel
import io.haru.assistant.onlineai.OnlineProvider
import io.haru.assistant.voice.HaruVoiceController
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.text.DateFormat
import java.util.Date

@Composable
fun HaruScreen(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    todayLines: List<String>,
    onlineProvider: OnlineProvider,
    selectedGeminiModel: GeminiModel,
    geminiModels: List<GeminiModel>,
    onlineStatus: String,
    hasGeminiKey: Boolean,
    appVersion: String,
    updateStatus: String,
    updateUrl: String,
    newsBundle: AndroidNewsBundle,
    hazardBundle: AndroidHazardBundle,
    trustedLocations: List<TrustedLocation>,
    currentDeviceLocation: TrustedLocation?,
    mapGpsActive: Boolean,
    locationShareCode: String,
    locationShareMapUrl: String,
    mapLocationStatus: String,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onSelectOnlineProvider: (OnlineProvider) -> Unit,
    onSelectGeminiModel: (GeminiModel) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onTestOnlineAi: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: (String) -> Unit,
    onRefreshNews: () -> Unit,
    onRefreshHazards: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onLocateMe: () -> Unit,
    onCreateLocationShare: (String, Int) -> Unit,
    onShareLocation: () -> Unit,
    onImportLocationShare: (String) -> Unit,
    onClearTrustedLocations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOnlineAi by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    val tabs = listOf("Assistant", "News", "Hazard Advisories", "Map")

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 18.dp, end = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "HARU",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Human Assistance & Responsive Utility",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(8.dp))
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            when (index) {
                                1 -> if (
                                    newsBundle.local.isEmpty() &&
                                    newsBundle.international.isEmpty() &&
                                    newsBundle.error.isBlank()
                                ) {
                                    onRefreshNews()
                                }
                                2 -> if (
                                    hazardBundle.pagasa.isEmpty() &&
                                    hazardBundle.phivolcs.isEmpty() &&
                                    hazardBundle.error.isBlank()
                                ) {
                                    onRefreshHazards()
                                }
                            }
                        },
                        text = { Text(title) },
                    )
                }
            }

            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 160),
                label = "haru-tab-transition",
            ) { tab ->
                    when (tab) {
                    0 -> AssistantPane(
                        viewModel = viewModel,
                        voiceStatus = voiceStatus,
                        todayLines = todayLines,
                        onlineProvider = onlineProvider,
                        onlineStatus = onlineStatus,
                        onSubmitClick = onSubmitClick,
                        onMicClick = onMicClick,
                        onSpeakClick = onSpeakClick,
                        appVersion = appVersion,
                        onOpenOnlineAi = { showOnlineAi = true },
                        onOpenUpdate = { showUpdate = true },
                    )
                    1 -> NewsPane(
                        bundle = newsBundle,
                        onRefresh = onRefreshNews,
                        onOpenUrl = onOpenUrl,
                    )
                    2 -> HazardPane(
                        bundle = hazardBundle,
                        onRefresh = onRefreshHazards,
                        onOpenUrl = onOpenUrl,
                    )
                    else -> MapPane(
                        locations = trustedLocations,
                        currentDeviceLocation = currentDeviceLocation,
                        mapGpsActive = mapGpsActive,
                        shareCode = locationShareCode,
                        shareMapUrl = locationShareMapUrl,
                        mapLocationStatus = mapLocationStatus,
                        onLocateMe = onLocateMe,
                        onCreateShare = onCreateLocationShare,
                        onShareLocation = onShareLocation,
                        onImportShare = onImportLocationShare,
                        onClear = onClearTrustedLocations,
                        onOpenUrl = onOpenUrl,
                    )
            }
            }
        }
    }

    if (showUpdate) {
        AppUpdateDialog(
            appVersion = appVersion,
            status = updateStatus,
            updateUrl = updateUrl,
            onDismiss = { showUpdate = false },
            onCheck = onCheckUpdate,
            onOpenUpdate = onOpenUpdate,
        )
    }

    if (showOnlineAi) {
        OnlineAiDialog(
            provider = onlineProvider,
            selectedGeminiModel = selectedGeminiModel,
            geminiModels = geminiModels,
            status = onlineStatus,
            hasGeminiKey = hasGeminiKey,
            onDismiss = { showOnlineAi = false },
            onSelectProvider = onSelectOnlineProvider,
            onSelectGeminiModel = onSelectGeminiModel,
            onRefreshGeminiModels = onRefreshGeminiModels,
            onSaveGeminiKey = onSaveGeminiKey,
            onTest = onTestOnlineAi,
        )
    }

}

@Composable
private fun AssistantPane(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    todayLines: List<String>,
    onlineProvider: OnlineProvider,
    onlineStatus: String,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    appVersion: String,
    onOpenOnlineAi: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HaruFace(mood = state.mood, modifier = Modifier.sizeCompat(170.dp))
        Spacer(Modifier.height(8.dp))

        if (!state.isBusy || state.mood != HaruMood.THINKING) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = state.mood.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    AssistantResponseText(state.message)
                }
            }
        }

        if (todayLines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Today", fontWeight = FontWeight.SemiBold)
                    todayLines.take(3).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenOnlineAi,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("AI · " + providerLabel(onlineProvider))
        }
        if (onlineStatus.isNotBlank()) {
            Text(
                onlineStatus,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = "Voice: " + voiceStatus.speechInput + " STT • " + voiceStatus.speechOutput,
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(12.dp))

        if (state.latestUserMessage.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.latestUserMessage
                            .replace(Regex("\\s+"), " ")
                            .take(180),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            if (state.isBusy && state.mood == HaruMood.THINKING) {
                Spacer(Modifier.height(5.dp))
                ThinkingDots()
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = state.command,
            onValueChange = viewModel::updateCommand,
            label = { Text("Ask HARU") },
            placeholder = { Text("Type or tap Mic") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmitClick() }),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = onSubmitClick,
                enabled = !state.isBusy && state.command.isNotBlank(),
            ) {
                Text("Send")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onMicClick,
                enabled = !state.isBusy || state.mood == HaruMood.LISTENING,
            ) {
                Text(if (state.mood == HaruMood.LISTENING) "Listening…" else "Mic")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onSpeakClick,
                enabled = !state.isBusy && state.message.isNotBlank(),
            ) {
                Text("Speak")
            }
        }

        TextButton(onClick = onOpenUpdate) {
            Text("HARU v$appVersion · Check update")
        }
    }
}

@Composable
private fun NewsPane(
    bundle: AndroidNewsBundle,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HARU News", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (bundle.error.isNotBlank()) {
            Text(bundle.error, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🇵🇭 Local", fontWeight = FontWeight.SemiBold)
                bundle.local.forEach { NewsEntry(it, onOpenUrl) }
                if (bundle.local.isEmpty()) Text("No local stories.", style = MaterialTheme.typography.bodySmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("🌍 International", fontWeight = FontWeight.SemiBold)
                bundle.international.forEach { NewsEntry(it, onOpenUrl) }
                if (bundle.international.isEmpty()) Text("No world stories.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NewsEntry(
    item: AndroidNewsItem,
    onOpenUrl: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            item.source + if (item.published.isBlank()) "" else " · " + item.published,
            style = MaterialTheme.typography.labelSmall,
        )
        TextButton(onClick = { onOpenUrl(item.link) }) {
            Text("Google Maps ↗")
        }
        HorizontalDivider()
    }
}

@Composable
private fun HazardPane(
    bundle: AndroidHazardBundle,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Weather & Hazards", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }

        Text(
            "Lightweight native summaries. No live weather page runs in the background.",
            style = MaterialTheme.typography.labelSmall,
        )

        if (bundle.error.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(bundle.error, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(10.dp))
        Text("PAGASA summary", fontWeight = FontWeight.SemiBold)
        Text(
            "Official weekly outlook plus the current public weather outlook.",
            style = MaterialTheme.typography.labelSmall,
        )
        bundle.pagasa.take(2).forEach { HazardEntry(it, onOpenUrl) }

        OutlinedButton(
            onClick = {
                onOpenUrl(
                    "https://bagong.pagasa.dost.gov.ph/" +
                        "weather/weather-outlook-selected-philippine-cities"
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("PAGASA 5-day city outlook ↗")
        }

        Spacer(Modifier.height(12.dp))
        Text("PHIVOLCS", fontWeight = FontWeight.SemiBold)
        bundle.phivolcs.take(3).forEach { HazardEntry(it, onOpenUrl) }

        bundle.noah.firstOrNull()?.let {
            Spacer(Modifier.height(8.dp))
            Text("UP NOAH", fontWeight = FontWeight.SemiBold)
            HazardEntry(it, onOpenUrl)
        }
    }
}

@Composable
private fun HazardEntry(
    item: AndroidHazardItem,
    onOpenUrl: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (item.issued.isNotBlank()) {
                Text(item.issued, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.summary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onOpenUrl(item.url) }) {
                Text("Official source ↗")
            }
        }
    }
}

@Composable
private fun MapPane(
    locations: List<TrustedLocation>,
    currentDeviceLocation: TrustedLocation?,
    mapGpsActive: Boolean,
    shareCode: String,
    shareMapUrl: String,
    mapLocationStatus: String,
    onLocateMe: () -> Unit,
    onCreateShare: (String, Int) -> Unit,
    onShareLocation: () -> Unit,
    onImportShare: (String) -> Unit,
    onClear: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var shareName by remember { mutableStateOf("") }
    var incomingCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Shared Locations", style = MaterialTheme.typography.titleMedium)
        Text(
            "Native MapLibre map using OpenStreetMap data. Share codes protect integrity, but sender identity is not independently verified.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (mapGpsActive) {
                Button(
                    onClick = onLocateMe,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📍 GPS ON")
                }
            } else {
                OutlinedButton(
                    onClick = onLocateMe,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📍 My GPS")
                }
            }

            OutlinedButton(
                onClick = {
                    currentDeviceLocation?.let { item ->
                        onOpenUrl(
                            "https://www.google.com/maps/search/?api=1&query=" +
                                item.latitude + "," + item.longitude
                        )
                    }
                },
                enabled = currentDeviceLocation != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Google Maps ↗")
            }
        }

        if (mapLocationStatus.isNotBlank()) {
            Text(
                mapLocationStatus,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(6.dp))
        TrustedLocationsMap(
            locations = locations,
            currentDeviceLocation = currentDeviceLocation,
        )
        Text(
            "Map data © OpenStreetMap contributors · tiles/style by OpenFreeMap",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(12.dp))
        Text("Share my location", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = shareName,
            onValueChange = { shareName = it.take(40) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onCreateShare(
                    shareName.ifBlank { "Loved one" },
                    60,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create 1-hour share")
        }

        if (shareCode.isNotBlank()) {
            OutlinedTextField(
                value = shareCode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Share package") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onShareLocation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }

                OutlinedButton(
                    onClick = {
                        if (shareMapUrl.isNotBlank()) {
                            onOpenUrl(shareMapUrl)
                        }
                    },
                    enabled = shareMapUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Open map ↗")
                }
            }

            Text(
                "The shared text contains a normal Google Maps link plus a HARU code for optional in-app import.",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Find a shared location", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = incomingCode,
            onValueChange = { incomingCode = it.take(8000) },
            label = { Text("Paste HARU share or Google Maps link") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onImportShare(incomingCode) },
            enabled = incomingCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add location")
        }

        if (locations.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text("Clear locations")
            }
        }

        Spacer(Modifier.height(8.dp))
        locations.forEach { item ->
            val expires = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(Date(item.expiresAt))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📍 " + item.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "expires " + expires +
                                (item.accuracyM?.let {
                                    " · ±" + it.toInt() + " m"
                                } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    TextButton(
                        onClick = {
                            onOpenUrl(
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    item.latitude + "," + item.longitude
                            )
                        }
                    ) {
                        Text("Open ↗")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustedLocationsMap(
    locations: List<TrustedLocation>,
    currentDeviceLocation: TrustedLocation?,
) {
    val context = LocalContext.current
    val allLocations = buildList {
        currentDeviceLocation?.let(::add)
        addAll(locations)
    }
    val renderKey = allLocations.joinToString("|") {
        it.id + ":" + it.latitude + ":" + it.longitude
    }

    var mapController by remember {
        mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null)
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)

        MapView(context).apply {
            onCreate(null)

            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    else ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }

            getMapAsync { map ->
                map.uiSettings.apply {
                    setZoomGesturesEnabled(true)
                    setDoubleTapGesturesEnabled(true)
                    setQuickZoomGesturesEnabled(true)
                    setScrollGesturesEnabled(true)
                    setScaleVelocityAnimationEnabled(true)
                    setRotateGesturesEnabled(true)
                    setTiltGesturesEnabled(false)
                    setCompassEnabled(true)
                    setCompassFadeFacingNorth(false)
                }

                map.setMinZoomPreference(3.0)
                map.setMaxZoomPreference(20.0)

                map.setStyle(OPENFREE_MAP_STYLE) {
                    mapController = map
                    tag = null
                }
            }

            onStart()
            onResume()
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapController = null
            mapView.setOnTouchListener(null)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            AndroidView(
                factory = { mapView },
                update = { view ->
                    val map = mapController
                    if (
                        map != null &&
                        map.style != null &&
                        view.tag != renderKey
                    ) {
                        view.tag = renderKey
                        map.clear()

                        @Suppress("DEPRECATION")
                        allLocations.takeLast(21).forEach { item ->
                            map.addMarker(
                                MarkerOptions()
                                    .position(
                                        LatLng(
                                            item.latitude,
                                            item.longitude,
                                        )
                                    )
                                    .title(item.name)
                                    .snippet(
                                        if (
                                            item.id ==
                                            "haru-current-device"
                                        ) {
                                            item.accuracyM?.let {
                                                "Current GPS · ±" +
                                                    it.toInt() +
                                                    " m"
                                            } ?: "Current GPS location"
                                        } else {
                                            item.accuracyM?.let {
                                                "Accuracy ±" +
                                                    it.toInt() +
                                                    " m"
                                            } ?: "Shared location"
                                        }
                                    )
                            )
                        }

                        val focus =
                            currentDeviceLocation ?:
                                locations.lastOrNull()

                        if (focus != null) {
                            map.easeCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(
                                        focus.latitude,
                                        focus.longitude,
                                    ),
                                    if (
                                        currentDeviceLocation != null
                                    ) 16.0 else 14.0,
                                ),
                                220,
                            )
                        } else if (map.cameraPosition.zoom < 3.5) {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    PHILIPPINES_CENTER,
                                    4.7,
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = {
                        mapController?.easeCamera(
                            CameraUpdateFactory.zoomBy(1.0),
                            140,
                        )
                    },
                    enabled = mapController != null,
                ) {
                    Text("+")
                }

                Button(
                    onClick = {
                        mapController?.easeCamera(
                            CameraUpdateFactory.zoomBy(-1.0),
                            140,
                        )
                    },
                    enabled = mapController != null,
                ) {
                    Text("−")
                }

                OutlinedButton(
                    onClick = {
                        mapController?.resetNorth()
                    },
                    enabled = mapController != null,
                ) {
                    Text("N")
                }
            }
        }
    }

    Text(
        "Pinch/quick zoom enabled · rotate with two fingers · N resets north · MapLibre compass is enabled.",
        style = MaterialTheme.typography.labelSmall,
    )
}

private val PHILIPPINES_CENTER =
    LatLng(12.8797, 121.7740)

private const val OPENFREE_MAP_STYLE =
    "https://tiles.openfreemap.org/styles/liberty"

@Composable
private fun AppUpdateDialog(
    appVersion: String,
    status: String,
    updateUrl: String,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onOpenUpdate: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU update") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Installed: v$appVersion", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    status.ifBlank { "Check GitHub for the latest standalone HARU APK." },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check update")
                }
                if (updateUrl.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onOpenUpdate(updateUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open GitHub APK")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "HARU only checks for a newer release and opens the GitHub APK link. Android handles the installer.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    )
}

@Composable
private fun OnlineAiDialog(
    provider: OnlineProvider,
    selectedGeminiModel: GeminiModel,
    geminiModels: List<GeminiModel>,
    status: String,
    hasGeminiKey: Boolean,
    onDismiss: () -> Unit,
    onSelectProvider: (OnlineProvider) -> Unit,
    onSelectGeminiModel: (GeminiModel) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onTest: () -> Unit,
) {
    var geminiKey by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HARU AI") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Online AI", fontWeight = FontWeight.SemiBold)
                listOf(
                    OnlineProvider.ANTIGRAVITY,
                    OnlineProvider.GEMINI,
                ).forEach { option ->
                    OutlinedButton(
                        onClick = { onSelectProvider(option) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text((if (provider == option) "✓ " else "") + providerLabel(option))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Gemini model", fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { modelMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(selectedGeminiModel.label + " ▾")
                    }
                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                    ) {
                        geminiModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (model.id == selectedGeminiModel.id) "✓ " else "") +
                                            model.label
                                    )
                                },
                                onClick = {
                                    onSelectGeminiModel(model)
                                    modelMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRefreshGeminiModels,
                    enabled = hasGeminiKey,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Refresh Gemini models")
                }
                Text(
                    "Refresh discovers newly released models and removes models no longer returned by Google.",
                    style = MaterialTheme.typography.labelSmall,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "Gemini API key" + if (hasGeminiKey) " · saved securely" else "",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it.take(300) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onSaveGeminiKey(geminiKey)
                        geminiKey = ""
                    },
                    enabled = geminiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Gemini key")
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onTest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Test selected AI")
                }

                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Antigravity remains the default. Gemini models refresh from Google's live catalog.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    )
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking-dots")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-dots-alpha",
    )
    Text(
        text = "•••",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.alpha(alpha),
    )
}

@Composable
private fun AssistantResponseText(message: String) {
    val clean = message
        .replace("```", "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .trim()
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            clean.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Spacer(Modifier.height(3.dp))
                    line.startsWith("#") -> Text(
                        text = line.trimStart('#', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    line.startsWith("- ") || line.startsWith("* ") -> Text(
                        text = "• " + line.drop(2).trim(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Text(
                        text = line.replace("`", ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (scrollState.maxValue > 0 && viewportHeightPx > 0) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .fillMaxHeight(),
            ) {
                val viewport = viewportHeightPx.toFloat()
                val content = viewport + scrollState.maxValue.toFloat()
                val thumbHeight = (viewport * viewport / content)
                    .coerceAtLeast(24.dp.toPx())
                    .coerceAtMost(viewport)
                val maxOffset = (viewport - thumbHeight).coerceAtLeast(0f)
                val fraction =
                    (scrollState.value.toFloat() / scrollState.maxValue.toFloat())
                        .coerceIn(0f, 1f)
                val radius = size.width / 2f

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width, viewport),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(0f, maxOffset * fraction),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
    }
}

private fun providerLabel(provider: OnlineProvider): String =
    when (provider) {
        OnlineProvider.ANTIGRAVITY -> "Antigravity"
        OnlineProvider.GEMINI -> "Gemini"
    }

private fun Modifier.sizeCompat(size: androidx.compose.ui.unit.Dp): Modifier =
    this.width(size).height(size)
