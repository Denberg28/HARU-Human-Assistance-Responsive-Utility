package io.haru.assistant.ui

import android.view.Gravity
import android.view.MotionEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.haru.assistant.HaruViewModel
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.core.HaruMood
import io.haru.assistant.location.TrustedLocation
import io.haru.assistant.onlineai.GeminiModel
import io.haru.assistant.onlineai.OnlineProvider
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
    todayLines: List<String>,
    companionSnapshot: CompanionSnapshot,
    haruCheckerEnabled: Boolean,
    companionQuiet: Boolean,
    onlineProvider: OnlineProvider,
    selectedGeminiModel: GeminiModel,
    geminiModels: List<GeminiModel>,
    onlineStatus: String,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    memoryCount: Int,
    appVersion: String,
    updateStatus: String,
    updateUrl: String,
    trustedLocations: List<TrustedLocation>,
    currentDeviceLocation: TrustedLocation?,
    mapGpsActive: Boolean,
    locationShareCode: String,
    locationShareMapUrl: String,
    mapLocationStatus: String,
    liveTrackedLocation: TrustedLocation?,
    liveTrackingStatus: String,
    liveShareActive: Boolean,
    liveMonitorActive: Boolean,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onAddCompanionTask: (String) -> Unit,
    onUpdateCompanionTask: (Int, String) -> Unit,
    onDeleteCompanionTask: (Int) -> Unit,
    onToggleHaruChecker: () -> Unit,
    onSelectOnlineProvider: (OnlineProvider) -> Unit,
    onSelectGeminiModel: (GeminiModel) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveGroqKey: (String) -> Unit,
    onTestOnlineAi: () -> Unit,
    onResetMemory: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onLocateMe: () -> Unit,
    onCreateLocationShare: (String, Int) -> Unit,
    onShareLocation: () -> Unit,
    onImportLocationShare: (String) -> Unit,
    onStopLiveShare: () -> Unit,
    onStopLiveMonitor: () -> Unit,
    onClearTrustedLocations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOnlineAi by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLockScreenSetup by remember { mutableStateOf(false) }
    val tabs = listOf("HARU", "Map")
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAbout = true }
                    .padding(top = 6.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "HARU",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Human Assistance & Responsive Utility",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }

            TabRow(
                selectedTabIndex = selectedTab,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 140),
                label = "haru-simple-tab-transition",
            ) { tab ->
                if (tab == 0) {
                    SimpleHaruPane(
                        viewModel = viewModel,
                        todayLines = todayLines,
                        companionSnapshot = companionSnapshot,
                        haruCheckerEnabled = haruCheckerEnabled,
                        companionQuiet = companionQuiet,
                        companionVisible = selectedTab == 0 && !showSettings && !showAbout && !showOnlineAi && !showUpdate && !showLockScreenSetup,
                        onOpenLockScreenSetup = { showLockScreenSetup = true },
                        onSubmitClick = onSubmitClick,
                        onMicClick = onMicClick,
                        onAddTask = onAddCompanionTask,
                        onUpdateTask = onUpdateCompanionTask,
                        onDeleteTask = onDeleteCompanionTask,
                        onOpenSettings = { showSettings = true },
                    )
                } else {
                    MapPane(
                        locations = trustedLocations,
                        currentDeviceLocation = currentDeviceLocation,
                        mapGpsActive = mapGpsActive,
                        shareCode = locationShareCode,
                        shareMapUrl = locationShareMapUrl,
                        mapLocationStatus = mapLocationStatus,
                        liveTrackedLocation = liveTrackedLocation,
                        liveTrackingStatus = liveTrackingStatus,
                        liveShareActive = liveShareActive,
                        liveMonitorActive = liveMonitorActive,
                        onLocateMe = onLocateMe,
                        onCreateShare = onCreateLocationShare,
                        onShareLocation = onShareLocation,
                        onImportShare = onImportLocationShare,
                        onStopLiveShare = onStopLiveShare,
                        onStopLiveMonitor = onStopLiveMonitor,
                        onClear = onClearTrustedLocations,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        }
    }

    if (showLockScreenSetup) {
        HaruLockScreenDialog(
            enabled = haruCheckerEnabled,
            onDismiss = { showLockScreenSetup = false },
            onToggle = onToggleHaruChecker,
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("HARU") },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("Close")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Human Assistance & Responsive Utility",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "A compact personal assistant with quiet caring check-ins, tasks, reminders, voice, and trusted location tools.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                    Text(
                        "Creator: MD",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Version $appVersion",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
        )
    }

    if (showSettings) {
        SimpleSettingsDialog(
            haruCheckerEnabled = haruCheckerEnabled,
            onlineProvider = onlineProvider,
            memoryCount = memoryCount,
            appVersion = appVersion,
            onDismiss = { showSettings = false },
            onOpenLockScreen = {
                showSettings = false
                showLockScreenSetup = true
            },
            onToggleHaruChecker = onToggleHaruChecker,
            onOpenAi = {
                showSettings = false
                showOnlineAi = true
            },
            onResetMemory = onResetMemory,
            onOpenUpdate = {
                showSettings = false
                showUpdate = true
            },
        )
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
            hasGroqKey = hasGroqKey,
            onDismiss = { showOnlineAi = false },
            onSelectProvider = onSelectOnlineProvider,
            onSelectGeminiModel = onSelectGeminiModel,
            onRefreshGeminiModels = onRefreshGeminiModels,
            onSaveGeminiKey = onSaveGeminiKey,
            onSaveGroqKey = onSaveGroqKey,
            onTest = onTestOnlineAi,
        )
    }

}

@Composable
private fun SimpleHaruPane(
    viewModel: HaruViewModel,
    todayLines: List<String>,
    companionSnapshot: CompanionSnapshot,
    haruCheckerEnabled: Boolean,
    companionQuiet: Boolean,
    companionVisible: Boolean,
    onOpenLockScreenSetup: () -> Unit,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onAddTask: (String) -> Unit,
    onUpdateTask: (Int, String) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state = viewModel.uiState
    var showToday by remember { mutableStateOf(false) }
    val responseScrollState = rememberScrollState()
    LaunchedEffect(state.message) {
        responseScrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(responseScrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (haruCheckerEnabled && !state.isBusy) {
                HaruCheckerCard(
                    snapshot = companionSnapshot,
                    quiet = companionQuiet,
                    busy = state.isBusy,
                    draft = state.command,
                    visible = companionVisible && !showToday,
                )
            } else {
                HaruFace(mood = state.mood, modifier = Modifier.sizeCompat(96.dp))
            }

            Spacer(Modifier.height(4.dp))

            if (state.isBusy && state.mood == HaruMood.THINKING) {
                ThinkingDots()
            } else {
                AssistantResponseText(
                    message = state.message,
                )
            }

            Spacer(Modifier.height(12.dp))

            Card(
                onClick = { showToday = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Today  ›",
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (todayLines.isEmpty()) {
                        Text(
                            "Nothing pending.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        todayLines.take(3).forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenLockScreenSetup) {
                    Text(if (haruCheckerEnabled) "Lock screen · ON" else "Lock screen · OFF")
                }
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
        ) {
            OutlinedTextField(
                value = state.command,
                onValueChange = viewModel::updateCommand,
                label = { Text("Tell HARU") },
                placeholder = {
                    Text("Ask, add a task, or set a reminder")
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(onSend = { if (state.canSubmit) onSubmitClick() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (shouldShowCommandExamples(
                    command = state.command,
                    latestUserMessage = state.latestUserMessage,
                )
            ) {
                Text(
                    COMMAND_EXAMPLE_HINT,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                )
            }

            Spacer(Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSubmitClick,
                    enabled = state.canSubmit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Send")
                }
                OutlinedButton(
                    onClick = onMicClick,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.mood == HaruMood.LISTENING) {
                            "Listening…"
                        } else {
                            "Mic"
                        }
                    )
                }
            }

            // Voice remains available through the Mic button.
            // Keep diagnostics out of the primary interface.
        }
    }

    if (showToday) {
        SimpleTodayDialog(
            snapshot = companionSnapshot,
            onDismiss = { showToday = false },
            onAddTask = onAddTask,
            onUpdateTask = onUpdateTask,
            onDeleteTask = onDeleteTask,
        )
    }
}

internal fun shouldShowCommandExamples(
    command: String,
    latestUserMessage: String,
): Boolean =
    command.isBlank() &&
        latestUserMessage.isBlank()

private const val COMMAND_EXAMPLE_HINT =
    "task Buy milk  •  remind me in 30 min to call"

@Composable
private fun SimpleTodayDialog(
    snapshot: CompanionSnapshot,
    onDismiss: () -> Unit,
    onAddTask: (String) -> Unit,
    onUpdateTask: (Int, String) -> Unit,
    onDeleteTask: (Int) -> Unit,
) {
    var taskText by remember { mutableStateOf("") }
    var selectedTaskIndex by remember {
        mutableStateOf<Int?>(null)
    }

    val selectedIndex = selectedTaskIndex
    if (selectedIndex != null) {
        val selectedTask =
            snapshot.tasks.getOrNull(selectedIndex)

        if (selectedTask != null) {
            TaskEditDialog(
                initialText = selectedTask.text,
                onDismiss = {
                    selectedTaskIndex = null
                },
                onSave = { updated ->
                    onUpdateTask(selectedIndex, updated)
                    selectedTaskIndex = null
                },
                onDelete = {
                    onDeleteTask(selectedIndex)
                    selectedTaskIndex = null
                },
            )
        } else {
            selectedTaskIndex = null
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        title = { Text("Today") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = {
                        taskText = it.take(200)
                    },
                    label = { Text("New task") },
                    placeholder = { Text("What do you need to do?") },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (taskText.isNotBlank()) {
                                    onAddTask(taskText)
                                    taskText = ""
                                }
                            }
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        onAddTask(taskText)
                        taskText = ""
                    },
                    enabled = taskText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add task")
                }

                val openTasks =
                    snapshot.tasks
                        .withIndex()
                        .filter { !it.value.done }

                if (openTasks.isEmpty()) {
                    Text(
                        "No open tasks.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Tap a task to edit or delete.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    openTasks.take(12).forEach { indexed ->
                        Card(
                            onClick = {
                                selectedTaskIndex = indexed.index
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "○  " + indexed.value.text,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }

                val upcoming =
                    snapshot.reminders
                        .filter {
                            it.dueAt >
                                System.currentTimeMillis()
                        }
                        .sortedBy { it.dueAt }
                        .take(4)

                if (upcoming.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        "Reminders",
                        fontWeight = FontWeight.SemiBold,
                    )
                    upcoming.forEach { reminder ->
                        Text(
                            "⏰ " + reminder.text + " · " +
                                DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT,
                                ).format(Date(reminder.dueAt)),
                            style =
                                MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Text(
                    "Reminder: type “remind me in 30 min to call” in Tell HARU.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    )

}

@Composable
private fun TaskEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var editedText by remember(initialText) {
        mutableStateOf(initialText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(editedText) },
                enabled = editedText.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        },
        title = { Text("Task") },
        text = {
            OutlinedTextField(
                value = editedText,
                onValueChange = {
                    editedText = it.take(200)
                },
                label = { Text("Edit task") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun SimpleSettingsDialog(
    haruCheckerEnabled: Boolean,
    onlineProvider: OnlineProvider,
    memoryCount: Int,
    appVersion: String,
    onDismiss: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onToggleHaruChecker: () -> Unit,
    onOpenAi: () -> Unit,
    onResetMemory: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        title = { Text("HARU settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenLockScreen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (haruCheckerEnabled) "Lock-screen HARU · ON" else "Lock-screen HARU · OFF")
                }

                OutlinedButton(
                    onClick = onToggleHaruChecker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Check-ins · " +
                            if (haruCheckerEnabled) {
                                "On"
                            } else {
                                "Paused"
                            }
                    )
                }

                Text(
                    if (haruCheckerEnabled) {
                        "HARU is armed for the lock screen only. It appears after the phone locks and closes when you unlock."
                    } else {
                        "Lock-screen HARU is off."
                    },
                    style = MaterialTheme.typography.labelSmall,
                )

                OutlinedButton(
                    onClick = onOpenAi,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "AI · " +
                            providerLabel(onlineProvider)
                    )
                }

                TextButton(
                    onClick = onResetMemory,
                    enabled = memoryCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear AI memory · $memoryCount/10")
                }

                TextButton(
                    onClick = onOpenUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("HARU v$appVersion · Check update")
                }
            }
        },
    )
}

@Composable
private fun MapPane(
    locations: List<TrustedLocation>,
    currentDeviceLocation: TrustedLocation?,
    mapGpsActive: Boolean,
    shareCode: String,
    shareMapUrl: String,
    mapLocationStatus: String,
    liveTrackedLocation: TrustedLocation?,
    liveTrackingStatus: String,
    liveShareActive: Boolean,
    liveMonitorActive: Boolean,
    onLocateMe: () -> Unit,
    onCreateShare: (String, Int) -> Unit,
    onShareLocation: () -> Unit,
    onImportShare: (String) -> Unit,
    onStopLiveShare: () -> Unit,
    onStopLiveMonitor: () -> Unit,
    onClear: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var shareName by remember { mutableStateOf("") }
    var incomingCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Shared Locations", style = MaterialTheme.typography.titleMedium)
        Text(
            "Native MapLibre map using OpenStreetMap data. Share codes protect integrity, but sender identity is not independently verified.",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(6.dp))
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
            liveTrackedLocation = liveTrackedLocation,
        )
        Text(
            "Map data © OpenStreetMap contributors · tiles/style by OpenFreeMap",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(12.dp))
        Text("Live location sharing", fontWeight = FontWeight.SemiBold)
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
            Text(if (liveShareActive) "Live sharing active" else "Start 1-hour live share")
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
                "The share contains a HARU-LIVE code plus a Google Maps snapshot. Another HARU user can paste the live code to monitor updates until it expires.",
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
            Text(if (liveMonitorActive) "Monitoring live share" else "Open shared location")
        }

        if (liveTrackingStatus.isNotBlank()) {
            Text(
                liveTrackingStatus,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (liveShareActive || liveMonitorActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (liveShareActive) {
                    OutlinedButton(
                        onClick = onStopLiveShare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Stop sharing")
                    }
                }

                if (liveMonitorActive) {
                    OutlinedButton(
                        onClick = onStopLiveMonitor,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Stop monitoring")
                    }
                }
            }
        }

        if (liveTrackedLocation != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "● " + liveTrackedLocation.name,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Live marker · " +
                                (liveTrackedLocation.accuracyM?.let {
                                    "±" + it.toInt() + " m"
                                } ?: "accuracy unavailable"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(
                        onClick = {
                            onOpenUrl(
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    liveTrackedLocation.latitude + "," +
                                    liveTrackedLocation.longitude
                            )
                        }
                    ) {
                        Text("Google Maps ↗")
                    }
                }
            }
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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
        }
    }
}

@Composable
private fun TrustedLocationsMap(
    locations: List<TrustedLocation>,
    currentDeviceLocation: TrustedLocation?,
    liveTrackedLocation: TrustedLocation?,
) {
    val context = LocalContext.current
    val allLocations = buildList {
        currentDeviceLocation?.let(::add)
        liveTrackedLocation?.let(::add)
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
                    setCompassFadeFacingNorth(true)
                    setCompassGravity(
                        Gravity.TOP or Gravity.END
                    )
                    val compassMargin =
                        (16f * resources.displayMetrics.density)
                            .toInt()
                    setCompassMargins(
                        0,
                        compassMargin,
                        compassMargin,
                        0,
                    )
                }

                map.setMinZoomPreference(3.0)
                map.setMaxZoomPreference(20.0)

                map.setStyle(OPENFREE_MAP_STYLE) {
                    mapController = map
                    tag = null
                }
            }

        }
    }

    val mapLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(mapView, mapLifecycle) {
        val binding = MapLifecycleBinding(
            mapLifecycle, mapView::onStart, mapView::onResume,
            mapView::onPause, mapView::onStop, mapView::onDestroy,
        )
        onDispose {
            mapController = null
            mapView.setOnTouchListener(null)
            binding.close()
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
                                liveTrackedLocation ?:
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


        }
    }

    Text(
        "Pinch/quick zoom enabled · rotate with two fingers · tap the native compass to return north.",
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
    hasGroqKey: Boolean,
    onDismiss: () -> Unit,
    onSelectProvider: (OnlineProvider) -> Unit,
    onSelectGeminiModel: (GeminiModel) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveGroqKey: (String) -> Unit,
    onTest: () -> Unit,
) {
    var geminiKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }
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
                    OnlineProvider.GROQ,
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
                Text(
                    "Groq API key" + if (hasGroqKey) " · saved securely" else "",
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = groqKey,
                    onValueChange = { groqKey = it.take(300) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onSaveGroqKey(groqKey)
                        groqKey = ""
                    },
                    enabled = groqKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Groq key")
                }
                Text(
                    "Groq uses Qwen 3.8 27B. HARU sends the same encrypted rolling conversation context used by Gemini.",
                    style = MaterialTheme.typography.labelSmall,
                )

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
                    "Antigravity remains the default. Gemini models refresh from Google's live catalog. Groq uses Qwen 3.8 27B with HARU's encrypted local memory.",
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

internal sealed interface HaruResponseBlock {
    data class Heading(
        val text: String,
        val level: Int,
    ) : HaruResponseBlock

    data class Bullet(val text: String) : HaruResponseBlock

    data class Numbered(
        val number: String,
        val text: String,
    ) : HaruResponseBlock

    data class Quote(val text: String) : HaruResponseBlock

    data class Code(val text: String) : HaruResponseBlock

    data class Paragraph(val text: String) : HaruResponseBlock

    data object Gap : HaruResponseBlock
}

internal fun parseAssistantResponse(
    message: String,
): List<HaruResponseBlock> {
    val clean =
        message
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u0000", "")
            .trim()

    if (clean.isBlank()) return emptyList()

    val blocks = mutableListOf<HaruResponseBlock>()
    val codeLines = mutableListOf<String>()
    val paragraphLines = mutableListOf<String>()
    var inCode = false

    fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        blocks +=
            HaruResponseBlock.Paragraph(
                paragraphLines
                    .joinToString(" ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            )
        paragraphLines.clear()
    }

    fun flushCode() {
        if (codeLines.isEmpty()) return
        blocks +=
            HaruResponseBlock.Code(
                codeLines.joinToString("\n").trimEnd()
            )
        codeLines.clear()
    }

    clean.lines().forEach { raw ->
        val trimmed = raw.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            if (inCode) flushCode()
            inCode = !inCode
            return@forEach
        }

        if (inCode) {
            codeLines += raw
            return@forEach
        }

        when {
            trimmed.isBlank() -> {
                flushParagraph()
                if (blocks.lastOrNull() !is HaruResponseBlock.Gap) {
                    blocks += HaruResponseBlock.Gap
                }
            }

            HEADING_MARKDOWN.matches(trimmed) -> {
                flushParagraph()
                val match =
                    HEADING_MARKDOWN.matchEntire(trimmed)
                        ?: return@forEach
                blocks +=
                    HaruResponseBlock.Heading(
                        text = match.groupValues[2],
                        level = match.groupValues[1].length,
                    )
            }

            trimmed.startsWith("- ") ||
                trimmed.startsWith("* ") ||
                trimmed.startsWith("• ") -> {
                flushParagraph()
                blocks +=
                    HaruResponseBlock.Bullet(
                        trimmed.drop(2).trim()
                    )
            }

            ORDERED_MARKDOWN.matches(trimmed) -> {
                flushParagraph()
                val match =
                    ORDERED_MARKDOWN.matchEntire(trimmed)
                        ?: return@forEach
                blocks +=
                    HaruResponseBlock.Numbered(
                        number = match.groupValues[1],
                        text = match.groupValues[2],
                    )
            }

            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks +=
                    HaruResponseBlock.Quote(
                        trimmed.drop(2).trim()
                    )
            }

            else -> paragraphLines += trimmed
        }
    }

    flushParagraph()
    if (inCode) flushCode()

    while (blocks.lastOrNull() is HaruResponseBlock.Gap) {
        blocks.removeAt(blocks.lastIndex)
    }

    return blocks
}

@Composable
private fun AssistantResponseText(message: String) {
    val blocks =
        remember(message) {
            parseAssistantResponse(message)
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is HaruResponseBlock.Heading -> {
                    Text(
                        text = formattedInlineText(block.text),
                        style =
                            if (block.level <= 2) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is HaruResponseBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            modifier = Modifier.width(18.dp),
                        )
                        Text(
                            text = formattedInlineText(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is HaruResponseBlock.Numbered -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = block.number + ".",
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            text = formattedInlineText(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is HaruResponseBlock.Quote -> {
                    Text(
                        text = formattedInlineText(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                    )
                }

                is HaruResponseBlock.Code -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color =
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = block.text,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                is HaruResponseBlock.Paragraph -> {
                    Text(
                        text = formattedInlineText(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HaruResponseBlock.Gap ->
                    Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private val HEADING_MARKDOWN =
    Regex("""^(#{1,6})\s+(.+)$""")

private val ORDERED_MARKDOWN =
    Regex("""^(\d+)[.)]\s+(.+)$""")

private fun formattedInlineText(
    value: String,
): AnnotatedString {
    val source =
        value
            .replace(
                Regex("""\[([^\]]+)]\((?:https?://)?[^)]+\)"""),
                "$1",
            )
            .replace(Regex("""__(.+?)__"""), "**$1**")
            .replace(Regex("""(?<!_)_([^_]+)_(?!_)"""), "*$1*")
            .replace(
                Regex(
                    """[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""
                ),
                "",
            )

    return buildAnnotatedString {
        var index = 0
        while (index < source.length) {
            when {
                source.startsWith("**", index) -> {
                    val end = source.indexOf("**", index + 2)
                    if (end > index + 2) {
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(
                            source.substring(index + 2, end)
                        )
                        pop()
                        index = end + 2
                    } else {
                        append(source[index])
                        index += 1
                    }
                }

                source[index] == '`' -> {
                    val end = source.indexOf('`', index + 1)
                    if (end > index + 1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        append(
                            source.substring(index + 1, end)
                        )
                        pop()
                        index = end + 1
                    } else {
                        index += 1
                    }
                }

                source[index] == '*' -> {
                    val end = source.indexOf('*', index + 1)
                    if (end > index + 1) {
                        append(
                            source.substring(index + 1, end)
                        )
                        index = end + 1
                    } else {
                        index += 1
                    }
                }

                else -> {
                    append(source[index])
                    index += 1
                }
            }
        }
    }
}

private fun providerLabel(provider: OnlineProvider): String =
    when (provider) {
        OnlineProvider.ANTIGRAVITY -> "Antigravity"
        OnlineProvider.GEMINI -> "Gemini"
        OnlineProvider.GROQ -> "Groq · Qwen 3.8"
    }

private fun Modifier.sizeCompat(size: androidx.compose.ui.unit.Dp): Modifier =
    this.width(size).height(size)
