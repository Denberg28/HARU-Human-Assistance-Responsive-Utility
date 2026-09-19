package io.haru.assistant.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
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
    onImportLocalModel: () -> Unit,
    onDownloadLocalModel: (String) -> Unit,
    onValidateLocalModel: (String) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
    onOpenModelLibrary: () -> Unit,
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
                    text = "😺 HARU",
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
            onDismiss = { if (!localAiBusy) showLocalAi = false },
            onImport = onImportLocalModel,
            onDownload = onDownloadLocalModel,
            onValidate = onValidateLocalModel,
            onDelete = onDeleteLocalModel,
            onOpenLibrary = onOpenModelLibrary,
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
                    "Open PAGASA’s live operational map for radar, satellite, warnings, rainfall, and weather layers.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onOpenUrl("https://www.panahon.gov.ph/") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open live PANaHON map")
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
private fun TrustedLocationsMap(locations: List<TrustedLocation>) {
    val focus = locations.lastOrNull()
    val mapUrl = if (focus != null) {
        "https://www.google.com/maps?q=" +
            focus.latitude + "," + focus.longitude +
            "&z=13&output=embed"
    } else {
        "https://www.google.com/maps?q=Philippines&z=5&output=embed"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): Boolean {
                            val host = request?.url?.host.orEmpty()
                            return !(host.endsWith("google.com") ||
                                host.endsWith("googleusercontent.com") ||
                                host.endsWith("gstatic.com"))
                        }
                    }
                    loadUrl(mapUrl)
                }
            },
            update = { webView ->
                if (webView.url != mapUrl) {
                    webView.loadUrl(mapUrl)
                }
            },
        )
    }
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
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onDownload: (String) -> Unit,
    onValidate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var downloadUrl by remember { mutableStateOf("") }
    var selectedModel by remember(status.installedModels) {
        mutableStateOf(
            status.activeModel.takeIf { it.isNotBlank() }
                ?: status.installedModels.firstOrNull().orEmpty()
        )
    }

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
        title = { Text("Local AI on this phone") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(status.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    status.ramGb.toString() + " GB RAM • " +
                        status.freeStorageGb.toString() + " GB free",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Recommended: " + status.recommendedTier,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import .litertlm from phone")
                }
                OutlinedButton(
                    onClick = onOpenLibrary,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open LiteRT model library")
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it.take(2000) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text("HTTPS .litertlm URL") },
                )
                Button(
                    onClick = { onDownload(downloadUrl.trim()) },
                    enabled = !busy &&
                        downloadUrl.trim().startsWith("https://") &&
                        downloadUrl.trim().substringBefore('?').endsWith(".litertlm"),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Working…" else "Download model")
                }

                status.installedModels.forEach { name ->
                    Spacer(Modifier.height(6.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (name == selectedModel) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        selectedModel = name
                                        onValidate(name)
                                    },
                                    enabled = !busy,
                                ) {
                                    Text("Validate")
                                }
                                TextButton(
                                    onClick = { onDelete(name) },
                                    enabled = !busy,
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AssistantResponseText(message: String) {
    val clean = message
        .replace("```", "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .trim()

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
