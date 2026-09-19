package io.haru.assistant.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.haru.assistant.localai.LocalAiStatus
import io.haru.assistant.localai.LocalModelOption
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.onlineai.OnlineProvider
import io.haru.assistant.voice.HaruVoiceController
import java.text.DateFormat
import java.util.Date

@Composable
fun HaruScreen(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    localAiStatus: LocalAiStatus,
    localAiBusy: Boolean,
    localModelOptions: List<LocalModelOption>,
    localAiDownloadProgress: Float?,
    localAiDownloadLabel: String,
    localAiDownloadState: String,
    localAiDownloadModelId: String,
    todayLines: List<String>,
    onlineProvider: OnlineProvider,
    onlineStatus: String,
    hasGeminiKey: Boolean,
    hasOpenRouterKey: Boolean,
    newsBundle: AndroidNewsBundle,
    hazardBundle: AndroidHazardBundle,
    trustedLocations: List<TrustedLocation>,
    locationShareCode: String,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onDownloadLocalModel: (LocalModelOption) -> Unit,
    onPauseLocalModelDownload: () -> Unit,
    onResumeLocalModelDownload: () -> Unit,
    onCancelLocalModelDownload: () -> Unit,
    onValidateLocalModel: (String) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
    onSelectOnlineProvider: (OnlineProvider) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveOpenRouterKey: (String) -> Unit,
    onTestOnlineAi: () -> Unit,
    onRefreshNews: () -> Unit,
    onRefreshHazards: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCreateLocationShare: (String, Int) -> Unit,
    onImportLocationShare: (String) -> Unit,
    onClearTrustedLocations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLocalAi by remember { mutableStateOf(false) }
    var showOnlineAi by remember { mutableStateOf(false) }
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
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> AssistantPane(
                    viewModel = viewModel,
                    voiceStatus = voiceStatus,
                    localAiStatus = localAiStatus,
                    todayLines = todayLines,
                    onlineProvider = onlineProvider,
                    onlineStatus = onlineStatus,
                    onSubmitClick = onSubmitClick,
                    onMicClick = onMicClick,
                    onSpeakClick = onSpeakClick,
                    onOpenLocalAi = { showLocalAi = true },
                    onOpenOnlineAi = { showOnlineAi = true },
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
                    shareCode = locationShareCode,
                    onCreateShare = onCreateLocationShare,
                    onImportShare = onImportLocationShare,
                    onClear = onClearTrustedLocations,
                    onOpenUrl = onOpenUrl,
                )
            }
        }
    }

    if (showLocalAi) {
        LocalAiSetupDialog(
            status = localAiStatus,
            busy = localAiBusy,
            options = localModelOptions,
            downloadProgress = localAiDownloadProgress,
            downloadLabel = localAiDownloadLabel,
            downloadState = localAiDownloadState,
            downloadModelId = localAiDownloadModelId,
            onDismiss = { if (!localAiBusy) showLocalAi = false },
            onDownload = onDownloadLocalModel,
            onPauseDownload = onPauseLocalModelDownload,
            onResumeDownload = onResumeLocalModelDownload,
            onCancelDownload = onCancelLocalModelDownload,
            onValidate = onValidateLocalModel,
            onDelete = onDeleteLocalModel,
        )
    }

    if (showOnlineAi) {
        OnlineAiDialog(
            provider = onlineProvider,
            status = onlineStatus,
            hasGeminiKey = hasGeminiKey,
            hasOpenRouterKey = hasOpenRouterKey,
            onDismiss = { showOnlineAi = false },
            onSelectProvider = onSelectOnlineProvider,
            onSaveGeminiKey = onSaveGeminiKey,
            onSaveOpenRouterKey = onSaveOpenRouterKey,
            onTest = onTestOnlineAi,
        )
    }
}

@Composable
private fun AssistantPane(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    localAiStatus: LocalAiStatus,
    todayLines: List<String>,
    onlineProvider: OnlineProvider,
    onlineStatus: String,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onOpenLocalAi: () -> Unit,
    onOpenOnlineAi: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onOpenOnlineAi,
                modifier = Modifier.weight(1f),
            ) {
                Text(providerLabel(onlineProvider))
            }
            OutlinedButton(
                onClick = onOpenLocalAi,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (localAiStatus.activeModel.isBlank()) "Local AI"
                    else "Local ✓"
                )
            }
        }
        if (onlineStatus.isNotBlank()) {
            Text(
                onlineStatus,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(8.dp))
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
            Text("Open ↗")
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
            Text("Hazard Advisories", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
        Text(
            "Official situational information. Follow agency and local-government instructions.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("🛰️ Live PAGASA PANaHON", fontWeight = FontWeight.SemiBold)
                Text(
                    "Interactive PAGASA radar, satellite, warning, rainfall, and weather layers.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                PagasaLiveMap()
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onOpenUrl("https://www.panahon.gov.ph/") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open in browser ↗")
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🌧️ PAGASA", fontWeight = FontWeight.SemiBold)
                bundle.pagasa.take(1).forEach { HazardEntry(it, onOpenUrl) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("🌋 PHIVOLCS", fontWeight = FontWeight.SemiBold)
                bundle.phivolcs.take(3).forEach { HazardEntry(it, onOpenUrl) }
                bundle.noah.firstOrNull()?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("🗺️ UP NOAH", fontWeight = FontWeight.SemiBold)
                    HazardEntry(it, onOpenUrl)
                }
            }
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
    shareCode: String,
    onCreateShare: (String, Int) -> Unit,
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
        Text("Trusted Locations", style = MaterialTheme.typography.titleMedium)
        Text(
            "Consent-based temporary location snapshots. HARU does not background-track people.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(8.dp))
        TrustedLocationsMap(locations)

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Share my location", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = shareName,
                    onValueChange = { shareName = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onCreateShare(shareName.ifBlank { "Loved one" }, 60) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create 1-hour share")
                }
                if (shareCode.isNotBlank()) {
                    OutlinedTextField(
                        value = shareCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Share code") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Find a loved one", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = incomingCode,
                    onValueChange = { incomingCode = it.take(4000) },
                    label = { Text("Paste share code") },
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
            }
        }

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
                                (item.accuracyM?.let { " · ±" + it.toInt() + " m" } ?: ""),
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
                        Text("Google Maps ↗")
                    }
                }
            }
        }
    }
}

@Composable
private fun PagasaLiveMap() {
    // PANaHON is a full interactive web application. Load it as a top-level
    // browser document instead of placing it inside another iframe.
    BrowserWebView(
        url = "https://panahon.gov.ph/",
        allowedHostSuffixes = setOf(
            "panahon.gov.ph",
            "pagasa.dost.gov.ph",
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
    )
}

@Composable
private fun TrustedLocationsMap(locations: List<TrustedLocation>) {
    val focus = locations.lastOrNull()
    val mapUrl = if (focus != null) {
        "https://maps.google.com/maps?q=" +
            focus.latitude + "," + focus.longitude +
            "&z=13&output=embed"
    } else {
        "https://maps.google.com/maps?q=Philippines&z=5&output=embed"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        GoogleMapsIframe(
            mapUrl = mapUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
    }
}

@Composable
private fun GoogleMapsIframe(
    mapUrl: String,
    modifier: Modifier = Modifier,
) {
    val escapedUrl = mapUrl
        .replace("&", "&amp;")
        .replace(""", "&quot;")

    val html = """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <style>
              html, body { margin:0; padding:0; width:100%; height:100%; overflow:hidden; background:#f7f7f7; }
              iframe { width:100%; height:100%; border:0; }
            </style>
          </head>
          <body>
            <iframe
              src="$escapedUrl"
              title="Google Maps"
              loading="eager"
              allowfullscreen
              referrerpolicy="no-referrer-when-downgrade">
            </iframe>
          </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureMapWebView()
                tag = mapUrl
                loadDataWithBaseURL(
                    "https://maps.google.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            if (webView.tag != mapUrl) {
                webView.tag = mapUrl
                webView.loadDataWithBaseURL(
                    "https://maps.google.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

@Composable
private fun BrowserWebView(
    url: String,
    allowedHostSuffixes: Set<String>,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureMapWebView()

                // Some interactive map sites degrade or reject the Android WebView
                // marker. Use the normal mobile Chromium UA while retaining the
                // app's HTTPS-only navigation policy.
                settings.userAgentString =
                    WebSettings.getDefaultUserAgent(context).replace("; wv", "")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): Boolean {
                        val uri = request?.url ?: return true
                        if (!uri.scheme.equals("https", ignoreCase = true)) return true

                        val host = uri.host?.lowercase().orEmpty()
                        return allowedHostSuffixes.none { suffix ->
                            host == suffix || host.endsWith("." + suffix)
                        }
                    }
                }

                tag = url
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.tag != url) {
                webView.tag = url
                webView.loadUrl(url)
            }
        },
    )
}

private fun WebView.configureMapWebView() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        setGeolocationEnabled(false)
    }
    webChromeClient = android.webkit.WebChromeClient()
}

@Composable
private fun OnlineAiDialog(
    provider: OnlineProvider,
    status: String,
    hasGeminiKey: Boolean,
    hasOpenRouterKey: Boolean,
    onDismiss: () -> Unit,
    onSelectProvider: (OnlineProvider) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveOpenRouterKey: (String) -> Unit,
    onTest: () -> Unit,
) {
    var geminiKey by remember { mutableStateOf("") }
    var openRouterKey by remember { mutableStateOf("") }

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
                Text("Online default", fontWeight = FontWeight.SemiBold)
                listOf(
                    OnlineProvider.ANTIGRAVITY,
                    OnlineProvider.GEMINI_FLASH_LITE,
                    OnlineProvider.OPENROUTER_FREE,
                    OnlineProvider.LOCAL_ONLY,
                ).forEach { option ->
                    OutlinedButton(
                        onClick = { onSelectProvider(option) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            (if (provider == option) "✓ " else "") + providerLabel(option)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Gemini key" + if (hasGeminiKey) " · saved securely" else "",
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

                Spacer(Modifier.height(8.dp))
                Text(
                    "OpenRouter key" + if (hasOpenRouterKey) " · saved securely" else "",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = openRouterKey,
                    onValueChange = { openRouterKey = it.take(400) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onSaveOpenRouterKey(openRouterKey)
                        openRouterKey = ""
                    },
                    enabled = openRouterKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save OpenRouter key")
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
                    "OpenRouter is locked to its free router. Paid models are not exposed.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    )
}

@Composable
private fun LocalAiSetupDialog(
    status: LocalAiStatus,
    busy: Boolean,
    options: List<LocalModelOption>,
    downloadProgress: Float?,
    downloadLabel: String,
    downloadState: String,
    downloadModelId: String,
    onDismiss: () -> Unit,
    onDownload: (LocalModelOption) -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onValidate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val downloadActive = downloadState in listOf("DOWNLOADING", "QUEUED")
    val downloadPaused = downloadState in listOf("PAUSED", "FAILED")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
            ) {
                Text("Done")
            }
        },
        title = { Text("Local AI") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    status.ramGb.toString() + " GB RAM • " +
                        status.freeStorageGb.toString() + " GB free",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "HARU recommends " + status.recommendedTier + " for this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )

                if (busy || downloadLabel.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))

                    when {
                        downloadProgress != null -> LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        downloadActive -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (downloadLabel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            downloadLabel,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    if (downloadActive || downloadPaused) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (downloadActive) {
                                OutlinedButton(
                                    onClick = onPauseDownload,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Pause")
                                }
                            } else {
                                Button(
                                    onClick = onResumeDownload,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Resume")
                                }
                            }

                            TextButton(onClick = onCancelDownload) {
                                Text("Cancel")
                            }
                        }

                        Text(
                            "Background download continues while you use HARU or another app.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Available models",
                    fontWeight = FontWeight.SemiBold,
                )

                options.forEach { option ->
                    val installed = status.installedModels.contains(option.fileName)
                    val active = status.activeModel == option.fileName
                    val recommended = status.recommendedModelId == option.id
                    val fits =
                        status.ramGb >= option.minRamGb &&
                            status.freeStorageGb >= option.minFreeStorageGb
                    val thisDownload =
                        downloadModelId == option.id &&
                            downloadState in listOf(
                                "DOWNLOADING",
                                "QUEUED",
                                "PAUSED",
                                "FAILED",
                            )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    option.name,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    when {
                                        active -> "ACTIVE"
                                        recommended -> "BEST FIT"
                                        installed -> "DOWNLOADED"
                                        thisDownload -> downloadState
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Needs about " + option.minRamGb + " GB RAM · " +
                                    option.minFreeStorageGb + " GB free",
                                style = MaterialTheme.typography.labelSmall,
                            )

                            Spacer(Modifier.height(6.dp))

                            when {
                                active -> Text(
                                    "Loaded and ready for local use.",
                                    style = MaterialTheme.typography.labelSmall,
                                )

                                installed -> Text(
                                    "Downloaded. Load it from the section below.",
                                    style = MaterialTheme.typography.labelSmall,
                                )

                                thisDownload -> Text(
                                    when (downloadState) {
                                        "PAUSED" -> "Paused. Use Resume above."
                                        "FAILED" -> "Interrupted. Use Resume above to retry."
                                        else -> "Downloading in the background."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )

                                else -> {
                                    Button(
                                        onClick = { onDownload(option) },
                                        enabled = !busy && fits && !downloadActive,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            if (recommended) "Download best model"
                                            else "Download"
                                        )
                                    }

                                    if (!fits) {
                                        Text(
                                            "Not recommended for current RAM/storage.",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (status.installedModels.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Downloaded models",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Load a downloaded model locally or remove it from this phone.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(6.dp))

                    status.installedModels.forEach { fileName ->
                        val active = status.activeModel == fileName
                        val displayName = options.firstOrNull {
                            it.fileName == fileName
                        }?.name ?: fileName

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (active) {
                                        Text(
                                            "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { onValidate(fileName) },
                                        enabled = !busy && !active,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(if (active) "Loaded" else "Load model")
                                    }

                                    TextButton(
                                        onClick = { onDelete(fileName) },
                                        enabled = !busy,
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Model files stay on this phone. HARU keeps online AI available separately.",
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
        OnlineProvider.GEMINI_FLASH_LITE -> "Gemini Flash-Lite"
        OnlineProvider.OPENROUTER_FREE -> "OpenRouter Free"
        OnlineProvider.LOCAL_ONLY -> "Local only"
    }

private fun Modifier.sizeCompat(size: androidx.compose.ui.unit.Dp): Modifier =
    this.width(size).height(size)
