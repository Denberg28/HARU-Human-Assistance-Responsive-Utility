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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.content.AndroidHazardBundle
import io.haru.assistant.content.AndroidHazardItem
import io.haru.assistant.content.AndroidNewsBundle
import io.haru.assistant.content.AndroidNewsItem
import io.haru.assistant.core.CompanionMode
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
    companionSnapshot: CompanionSnapshot,
    companionMode: CompanionMode,
    lockScreenCompanionEnabled: Boolean,
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
    newsBundle: AndroidNewsBundle,
    hazardBundle: AndroidHazardBundle,
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
    onSpeakClick: () -> Unit,
    onSelectCompanionMode: (CompanionMode) -> Unit,
    onAddCompanionTask: (String) -> Unit,
    onCompleteCompanionTask: (Int) -> Unit,
    onUpdateCompanionTask: (Int, String) -> Unit,
    onDeleteCompanionTask: (Int) -> Unit,
    onAddQuickReminder: (String, Int) -> Unit,
    onSetLockScreenCompanion: (Boolean) -> Unit,
    onOpenLockScreenNotificationSettings: () -> Unit,
    onTestLockScreenCompanion: () -> Unit,
    onSelectOnlineProvider: (OnlineProvider) -> Unit,
    onSelectGeminiModel: (GeminiModel) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveGroqKey: (String) -> Unit,
    onTestOnlineAi: () -> Unit,
    onResetMemory: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: (String) -> Unit,
    onRefreshNews: () -> Unit,
    onRefreshHazards: () -> Unit,
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
    val tabs = listOf("HARU", "Map")

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "HARU",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    top = 14.dp,
                    bottom = 4.dp,
                ),
            )

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
                        voiceStatus = voiceStatus,
                        onSubmitClick = onSubmitClick,
                        onMicClick = onMicClick,
                        onAddTask = onAddCompanionTask,
                        onCompleteTask = onCompleteCompanionTask,
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

    if (showSettings) {
        SimpleSettingsDialog(
            lockScreenCompanionEnabled = lockScreenCompanionEnabled,
            onlineProvider = onlineProvider,
            memoryCount = memoryCount,
            appVersion = appVersion,
            onDismiss = { showSettings = false },
            onToggleLockScreen = {
                onSetLockScreenCompanion(
                    !lockScreenCompanionEnabled
                )
            },
            onTestLockScreen = onTestLockScreenCompanion,
            onOpenLockScreenSettings =
                onOpenLockScreenNotificationSettings,
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
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onAddTask: (String) -> Unit,
    onCompleteTask: (Int) -> Unit,
    onUpdateTask: (Int, String) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state = viewModel.uiState
    var showToday by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HaruFace(
                mood = state.mood,
                modifier = Modifier.sizeCompat(118.dp),
            )

            Spacer(Modifier.height(6.dp))

            if (state.isBusy && state.mood == HaruMood.THINKING) {
                ThinkingDots()
            } else {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
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

            if (state.latestUserMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "You: " +
                        state.latestUserMessage
                            .replace(Regex("\\s+"), " ")
                            .take(160),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
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
                    KeyboardActions(onSend = { onSubmitClick() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "task Buy milk  •  remind me in 30 min to call",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
            )

            Spacer(Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSubmitClick,
                    enabled =
                        !state.isBusy &&
                            state.command.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Send")
                }
                OutlinedButton(
                    onClick = onMicClick,
                    enabled =
                        !state.isBusy ||
                            state.mood == HaruMood.LISTENING,
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

            Text(
                "Voice: " + voiceStatus.speechInput +
                    " • " + voiceStatus.speechOutput,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }

    if (showToday) {
        SimpleTodayDialog(
            snapshot = companionSnapshot,
            onDismiss = { showToday = false },
            onAddTask = onAddTask,
            onCompleteTask = onCompleteTask,
            onUpdateTask = onUpdateTask,
            onDeleteTask = onDeleteTask,
        )
    }
}

@Composable
private fun SimpleTodayDialog(
    snapshot: CompanionSnapshot,
    onDismiss: () -> Unit,
    onAddTask: (String) -> Unit,
    onCompleteTask: (Int) -> Unit,
    onUpdateTask: (Int, String) -> Unit,
    onDeleteTask: (Int) -> Unit,
) {
    var taskText by remember { mutableStateOf("") }
    var selectedTaskIndex by remember {
        mutableStateOf<Int?>(null)
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
                        "Tap a task to edit, finish, or delete.",
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

    selectedTaskIndex?.let { index ->
        val task =
            snapshot.tasks.getOrNull(index)
        if (task != null) {
            TaskEditDialog(
                initialText = task.text,
                onDismiss = {
                    selectedTaskIndex = null
                },
                onSave = { updated ->
                    onUpdateTask(index, updated)
                    selectedTaskIndex = null
                },
                onDone = {
                    onCompleteTask(index)
                    selectedTaskIndex = null
                },
                onDelete = {
                    onDeleteTask(index)
                    selectedTaskIndex = null
                },
            )
        } else {
            selectedTaskIndex = null
        }
    }
}

@Composable
private fun TaskEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDone: () -> Unit,
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
            Row {
                TextButton(onClick = onDone) {
                    Text("Done")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
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
    lockScreenCompanionEnabled: Boolean,
    onlineProvider: OnlineProvider,
    memoryCount: Int,
    appVersion: String,
    onDismiss: () -> Unit,
    onToggleLockScreen: () -> Unit,
    onTestLockScreen: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
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
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onToggleLockScreen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Lock screen · " +
                            if (lockScreenCompanionEnabled) {
                                "On"
                            } else {
                                "Off"
                            }
                    )
                }

                if (lockScreenCompanionEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = onTestLockScreen,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Test")
                        }
                        TextButton(
                            onClick =
                                onOpenLockScreenSettings,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Android settings")
                        }
                    }
                }

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
private fun HomePane(
    viewModel: HaruViewModel,
    todayLines: List<String>,
    companionSnapshot: CompanionSnapshot,
    companionMode: CompanionMode,
    lockScreenCompanionEnabled: Boolean,
    onlineProvider: OnlineProvider,
    memoryCount: Int,
    mapGpsActive: Boolean,
    currentDeviceLocation: TrustedLocation?,
    liveShareActive: Boolean,
    liveMonitorActive: Boolean,
    newsCount: Int,
    hazardCount: Int,
    appVersion: String,
    onSelectMode: (CompanionMode) -> Unit,
    onAddTask: (String) -> Unit,
    onCompleteTask: (Int) -> Unit,
    onAddQuickReminder: (String, Int) -> Unit,
    onSetLockScreenCompanion: (Boolean) -> Unit,
    onOpenLockScreenNotificationSettings: () -> Unit,
    onTestLockScreenCompanion: () -> Unit,
    onTalk: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenHazards: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenOnlineAi: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val state = viewModel.uiState
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var showPriority by remember { mutableStateOf(false) }
    var showToday by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HaruFace(
            mood = state.mood,
            modifier = Modifier.sizeCompat(140.dp),
        )
        Spacer(Modifier.height(6.dp))

        Text(
            text = "COMPANION NODE · " + companionMode.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = companionMode.role,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Observe · Remember · Assist · Act · Alert",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { modeMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mode · " + companionMode.label + " ▾")
            }
            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false },
            ) {
                CompanionMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (mode == companionMode) "✓ " else "") +
                                    mode.label +
                                    " · " +
                                    mode.role
                            )
                        },
                        onClick = {
                            onSelectMode(mode)
                            modeMenuExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(
            onClick = { showPriority = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Current priority  ›",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    companionMode.priority,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Local core active · cloud AI is optional.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(
            onClick = { showToday = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Today  ›", fontWeight = FontWeight.SemiBold)
                if (todayLines.isEmpty()) {
                    Text(
                        "No open tasks or upcoming reminders.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    todayLines.take(4).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Lock-screen companion",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (lockScreenCompanionEnabled) {
                        "HARU status is visible while the phone is locked."
                    } else {
                        "Off by default. Enable a quiet HARU status card on the lock screen."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = {
                        onSetLockScreenCompanion(
                            !lockScreenCompanionEnabled
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (lockScreenCompanionEnabled) {
                            "Turn off lock-screen HARU"
                        } else {
                            "Show HARU on lock screen"
                        }
                    )
                }

                if (lockScreenCompanionEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onTestLockScreenCompanion,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Test now")
                        }
                        OutlinedButton(
                            onClick =
                                onOpenLockScreenNotificationSettings,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Lock-screen settings")
                        }
                    }
                    Text(
                        "After Test now, press the power button. If HARU is still hidden, open Lock-screen settings and allow this notification category on the lock screen.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Text(
                    "Low-power standby: no polling, wake lock, GPS, or network refresh is started by this status.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Companion state", fontWeight = FontWeight.SemiBold)
                Text(
                    "GPS · " +
                        if (mapGpsActive || currentDeviceLocation != null) {
                            "ready"
                        } else {
                            "off"
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Live share · " +
                        (if (liveShareActive) "active" else "off") +
                        "  |  Tracking · " +
                        (if (liveMonitorActive) "active" else "off"),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Memory · $memoryCount/10 encrypted",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Reasoning · " +
                        providerLabel(onlineProvider) +
                        " · optional",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Cached feeds · $newsCount news · $hazardCount hazard items",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Quick actions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onTalk,
                modifier = Modifier.weight(1f),
            ) {
                Text("Talk")
            }
            OutlinedButton(
                onClick = onOpenAssistant,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reason")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onOpenMap,
                modifier = Modifier.weight(1f),
            ) {
                Text("Map")
            }
            OutlinedButton(
                onClick = onOpenHazards,
                modifier = Modifier.weight(1f),
            ) {
                Text("Hazards")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onOpenNews,
                modifier = Modifier.weight(1f),
            ) {
                Text("News")
            }
            OutlinedButton(
                onClick = onOpenOnlineAi,
                modifier = Modifier.weight(1f),
            ) {
                Text("AI setup")
            }
        }

        TextButton(onClick = onOpenUpdate) {
            Text("HARU v$appVersion · Check update")
        }
    }

    if (showPriority) {
        PriorityDialog(
            mode = companionMode,
            onDismiss = { showPriority = false },
            onToday = {
                showPriority = false
                showToday = true
            },
            onTalk = {
                showPriority = false
                onTalk()
            },
            onReason = {
                showPriority = false
                onOpenAssistant()
            },
            onMap = {
                showPriority = false
                onOpenMap()
            },
            onHazards = {
                showPriority = false
                onOpenHazards()
            },
        )
    }

    if (showToday) {
        TodayDialog(
            snapshot = companionSnapshot,
            onDismiss = { showToday = false },
            onAddTask = onAddTask,
            onCompleteTask = onCompleteTask,
            onAddQuickReminder = onAddQuickReminder,
        )
    }
}

@Composable
private fun PriorityDialog(
    mode: CompanionMode,
    onDismiss: () -> Unit,
    onToday: () -> Unit,
    onTalk: () -> Unit,
    onReason: () -> Unit,
    onMap: () -> Unit,
    onHazards: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text(mode.label + " priority") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(mode.priority)
                Text(
                    "These are local shortcuts. They do not use cloud AI unless you choose Reason.",
                    style = MaterialTheme.typography.labelSmall,
                )
                when (mode) {
                    CompanionMode.FLIGHT -> {
                        OutlinedButton(onClick = onToday, modifier = Modifier.fillMaxWidth()) {
                            Text("Checklist / Today")
                        }
                        OutlinedButton(onClick = onMap, modifier = Modifier.fillMaxWidth()) {
                            Text("GPS / Map")
                        }
                        OutlinedButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) {
                            Text("Talk to HARU")
                        }
                    }
                    CompanionMode.TRAVEL,
                    CompanionMode.SAFETY -> {
                        OutlinedButton(onClick = onMap, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Map")
                        }
                        OutlinedButton(onClick = onHazards, modifier = Modifier.fillMaxWidth()) {
                            Text("Check Hazards")
                        }
                        OutlinedButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) {
                            Text("Talk to HARU")
                        }
                    }
                    CompanionMode.WORK,
                    CompanionMode.NORMAL -> {
                        OutlinedButton(onClick = onToday, modifier = Modifier.fillMaxWidth()) {
                            Text("Manage Today")
                        }
                        OutlinedButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) {
                            Text("Talk to HARU")
                        }
                        OutlinedButton(onClick = onReason, modifier = Modifier.fillMaxWidth()) {
                            Text("Use Reasoning")
                        }
                    }
                    CompanionMode.REST -> {
                        OutlinedButton(onClick = onToday, modifier = Modifier.fillMaxWidth()) {
                            Text("Essential reminders")
                        }
                        OutlinedButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) {
                            Text("Talk to HARU")
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun TodayDialog(
    snapshot: CompanionSnapshot,
    onDismiss: () -> Unit,
    onAddTask: (String) -> Unit,
    onCompleteTask: (Int) -> Unit,
    onAddQuickReminder: (String, Int) -> Unit,
) {
    var taskText by remember { mutableStateOf("") }
    var reminderText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Today") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Tasks", fontWeight = FontWeight.SemiBold)
                val openTasks = snapshot.tasks.withIndex().filter { !it.value.done }
                if (openTasks.isEmpty()) {
                    Text("No open tasks.", style = MaterialTheme.typography.bodySmall)
                } else {
                    openTasks.take(12).forEach { indexed ->
                        OutlinedButton(
                            onClick = { onCompleteTask(indexed.index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("✓  " + indexed.value.text)
                        }
                    }
                }

                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it.take(200) },
                    label = { Text("New task") },
                    singleLine = true,
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

                HorizontalDivider()
                Text("Quick reminder", fontWeight = FontWeight.SemiBold)
                snapshot.reminders
                    .filter { it.dueAt > System.currentTimeMillis() }
                    .sortedBy { it.dueAt }
                    .take(6)
                    .forEach { reminder ->
                        Text(
                            "⏰ " + reminder.text + " · " +
                                DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT,
                                ).format(Date(reminder.dueAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                OutlinedTextField(
                    value = reminderText,
                    onValueChange = { reminderText = it.take(200) },
                    label = { Text("Reminder text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onAddQuickReminder(reminderText, 15)
                            reminderText = ""
                        },
                        enabled = reminderText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("15 min")
                    }
                    OutlinedButton(
                        onClick = {
                            onAddQuickReminder(reminderText, 60)
                            reminderText = ""
                        },
                        enabled = reminderText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("1 hour")
                    }
                }
            }
        },
    )
}

@Composable
private fun AssistantPane(
    viewModel: HaruViewModel,
    voiceStatus: HaruVoiceController.VoiceRuntimeStatus,
    todayLines: List<String>,
    onlineProvider: OnlineProvider,
    onlineStatus: String,
    memoryCount: Int,
    onSubmitClick: () -> Unit,
    onMicClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onResetMemory: () -> Unit,
    appVersion: String,
    onOpenOnlineAi: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val state = viewModel.uiState

    val assistantScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(assistantScrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Reasoning console",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Local commands run first. Cloud AI is only used when needed.",
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        HaruFace(mood = state.mood, modifier = Modifier.sizeCompat(120.dp))
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Memory $memoryCount/10 · encrypted · 5-turn AI window",
                style = MaterialTheme.typography.labelSmall,
            )
            TextButton(
                onClick = onResetMemory,
                enabled = memoryCount > 0 || state.isBusy,
            ) {
                Text("Reset memory")
            }
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
        TextButton(
            onClick = { onOpenUrl(item.link) },
            enabled = item.link.startsWith("https://"),
        ) {
            Text("Read article ↗")
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
        OnlineProvider.GROQ -> "Groq · Qwen 3.8"
    }

private fun Modifier.sizeCompat(size: androidx.compose.ui.unit.Dp): Modifier =
    this.width(size).height(size)
