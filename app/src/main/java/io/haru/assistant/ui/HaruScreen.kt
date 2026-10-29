package io.haru.assistant.ui

import android.view.Gravity
import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.haru.assistant.HaruViewModel
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.companion.HaruDailyPulseFactory
import io.haru.assistant.core.HaruMood
import io.haru.assistant.location.TrustedLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HaruScreen(
    journalViewModel: HaruViewModel,
    todayLines: List<String>,
    companionSnapshot: CompanionSnapshot,
    haruCheckerEnabled: Boolean,
    lockScreenEnabled: Boolean,
    themeColor: HaruThemeColor,
    backgroundTheme: HaruBackgroundTheme,
    companionQuiet: Boolean,
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
    locationShareBusy: Boolean,
    liveMonitorActive: Boolean,
    onAddCompanionTask: (String) -> Unit,
    onAddReminder: (String, Long) -> Unit,
    onUpdateCompanionTask: (Int, String) -> Unit,
    onDeleteCompanionTask: (Int) -> Unit,
    onReorderCompanionTasks: (List<String>) -> Unit,
    onToggleHaruChecker: () -> Unit,
    onToggleLockScreen: () -> Unit,
    onSelectThemeColor: (HaruThemeColor) -> Unit,
    onSelectBackgroundTheme: (HaruBackgroundTheme) -> Unit,
    onOpenAppSettings: () -> Unit,
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
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val journalStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var showUpdate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLockScreenSetup by remember { mutableStateOf(false) }
    val tabs = listOf("Journal", "Map")
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

            // MapLibre is a heavyweight native view. Avoid crossfading the two
            // tab bodies because that briefly renders HARU and the map together
            // and can cause frame drops on mid-range phones. TabRow already
            // provides the selection motion, while the content swaps once.
            if (selectedTab == 0) {
                journalStateHolder.SaveableStateProvider("journal") {
                var showToday by remember { mutableStateOf(false) }
                JournalPane(
                    viewModel = journalViewModel,
                    companionSnapshot = companionSnapshot,
                    checkerEnabled = haruCheckerEnabled,
                    quiet = companionQuiet,
                    visible = !showSettings && !showAbout && !showUpdate && !showLockScreenSetup && !showToday,
                    onOpenToday = { showToday = true },
                    onOpenSettings = { showSettings = true },
                )
                if (showToday) SimpleTodayDialog(
                    snapshot = companionSnapshot,
                    onDismiss = { showToday = false },
                    onAddTask = onAddCompanionTask,
                    onUpdateTask = onUpdateCompanionTask,
                    onDeleteTask = onDeleteCompanionTask,
                    onReorderTasks = onReorderCompanionTasks,
                    onAddReminder = onAddReminder,
                )
                }
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
                    locationShareBusy = locationShareBusy,
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

    if (showLockScreenSetup) {
        HaruLockScreenDialog(
            enabled = lockScreenEnabled,
            onDismiss = { showLockScreenSetup = false },
            onToggle = onToggleLockScreen,
            onOpenAppSettings = onOpenAppSettings,
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
                        "A private daily journal with memorable days, quiet caring check-ins, tasks, reminders, and trusted location tools.",
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
            lockScreenEnabled = lockScreenEnabled,
            themeColor = themeColor,
            backgroundTheme = backgroundTheme,
            appVersion = appVersion,
            onDismiss = { showSettings = false },
            onOpenLockScreen = {
                showSettings = false
                showLockScreenSetup = true
            },
            onToggleHaruChecker = onToggleHaruChecker,
            onSelectThemeColor = onSelectThemeColor,
            onSelectBackgroundTheme = onSelectBackgroundTheme,
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

}

@Composable
private fun SimpleTodayDialog(
    snapshot: CompanionSnapshot,
    onDismiss: () -> Unit,
    onAddTask: (String) -> Unit,
    onUpdateTask: (Int, String) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onReorderTasks: (List<String>) -> Unit,
    onAddReminder: (String, Long) -> Unit,
) {
    var taskText by remember { mutableStateOf("") }
    var showReminder by remember { mutableStateOf(false) }
    var selectedTaskIndex by remember {
        mutableStateOf<Int?>(null)
    }
    val taskOrder =
        remember(snapshot.tasks) {
            mutableStateListOf<String>().apply {
                addAll(
                    snapshot.tasks
                        .filterNot { it.done }
                        .map { it.id }
                )
            }
        }
    var draggingTaskId by remember {
        mutableStateOf<String?>(null)
    }
    var dragOffsetY by remember {
        mutableStateOf(0f)
    }
    var dragOrderChanged by remember {
        mutableStateOf(false)
    }
    val taskRowHeights =
        remember(snapshot.tasks) {
            mutableStateMapOf<String, Int>()
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

                if (taskOrder.isEmpty()) {
                    Text(
                        "No open tasks.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Hold and drag ≡ to reorder. Tap a task to edit or delete.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    taskOrder.take(12).forEach { taskId ->
                        val task =
                            snapshot.tasks.firstOrNull { it.id == taskId }
                                ?: return@forEach
                        val taskIndex =
                            snapshot.tasks.indexOfFirst { it.id == taskId }
                        if (taskIndex < 0) return@forEach

                        key(taskId) {
                            val isDragging =
                                draggingTaskId == taskId

                            Card(
                                onClick = {
                                    if (!isDragging) {
                                        selectedTaskIndex = taskIndex
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .zIndex(
                                            if (isDragging) 1f else 0f
                                        )
                                        .graphicsLayer {
                                            translationY =
                                                if (isDragging) {
                                                    dragOffsetY
                                                } else {
                                                    0f
                                                }
                                            shadowElevation =
                                                if (isDragging) {
                                                    10.dp.toPx()
                                                } else {
                                                    0f
                                                }
                                        }
                                        .onGloballyPositioned { coordinates ->
                                            taskRowHeights[taskId] =
                                                coordinates.size.height
                                        },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        "≡",
                                        modifier =
                                            Modifier
                                                .width(42.dp)
                                                .pointerInput(taskId) {
                                                    val fallbackHeight =
                                                        56.dp.toPx()
                                                    val rowSpacing =
                                                        8.dp.toPx()

                                                    fun rowHeight(id: String): Float =
                                                        taskRowHeights[id]
                                                            ?.toFloat()
                                                            ?: fallbackHeight

                                                    fun centerDistance(
                                                        first: String,
                                                        second: String,
                                                    ): Float =
                                                        rowHeight(first) / 2f +
                                                            rowSpacing +
                                                            rowHeight(second) / 2f

                                                    fun persistOrderIfChanged() {
                                                        if (dragOrderChanged) {
                                                            onReorderTasks(
                                                                taskOrder.toList()
                                                            )
                                                            dragOrderChanged = false
                                                        }
                                                    }

                                                    fun finishDrag() {
                                                        persistOrderIfChanged()
                                                        draggingTaskId = null
                                                        dragOffsetY = 0f
                                                    }

                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            draggingTaskId =
                                                                taskId
                                                            dragOffsetY = 0f
                                                            dragOrderChanged = false
                                                        },
                                                        onDragCancel = {
                                                            finishDrag()
                                                        },
                                                        onDragEnd = {
                                                            finishDrag()
                                                        },
                                                        onDrag = {
                                                                change,
                                                                dragAmount,
                                                            ->
                                                            change.consume()
                                                            dragOffsetY +=
                                                                dragAmount.y

                                                            val current =
                                                                taskOrder.indexOf(taskId)

                                                            if (
                                                                current in
                                                                    0 until
                                                                        taskOrder.lastIndex
                                                            ) {
                                                                val next =
                                                                    taskOrder[
                                                                        current + 1
                                                                    ]
                                                                val step =
                                                                    centerDistance(
                                                                        taskId,
                                                                        next,
                                                                    )
                                                                if (
                                                                    dragOffsetY >
                                                                        step * 0.55f
                                                                ) {
                                                                    val moved =
                                                                        taskOrder.removeAt(
                                                                            current
                                                                        )
                                                                    taskOrder.add(
                                                                        current + 1,
                                                                        moved,
                                                                    )
                                                                    dragOffsetY -= step
                                                                    dragOrderChanged = true
                                                                    persistOrderIfChanged()
                                                                }
                                                            }

                                                            val updatedCurrent =
                                                                taskOrder.indexOf(taskId)
                                                            if (updatedCurrent > 0) {
                                                                val previous =
                                                                    taskOrder[
                                                                        updatedCurrent - 1
                                                                    ]
                                                                val step =
                                                                    centerDistance(
                                                                        taskId,
                                                                        previous,
                                                                    )
                                                                if (
                                                                    dragOffsetY <
                                                                        -step * 0.55f
                                                                ) {
                                                                    val moved =
                                                                        taskOrder.removeAt(
                                                                            updatedCurrent
                                                                        )
                                                                    taskOrder.add(
                                                                        updatedCurrent - 1,
                                                                        moved,
                                                                    )
                                                                    dragOffsetY += step
                                                                    dragOrderChanged = true
                                                                    persistOrderIfChanged()
                                                                }
                                                            }
                                                        },
                                                    )
                                                },
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        task.text,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
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

                TextButton(onClick = { showReminder = true }) { Text("Add reminder") }
            }
        },
    )

    if (showReminder) ReminderEditDialog(
        onDismiss = { showReminder = false },
        onSave = { text, dueAt -> onAddReminder(text, dueAt); showReminder = false },
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
    lockScreenEnabled: Boolean,
    themeColor: HaruThemeColor,
    backgroundTheme: HaruBackgroundTheme,
    appVersion: String,
    onDismiss: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onToggleHaruChecker: () -> Unit,
    onSelectThemeColor: (HaruThemeColor) -> Unit,
    onSelectBackgroundTheme: (HaruBackgroundTheme) -> Unit,
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
                    Text(if (lockScreenEnabled) "Lock-screen HARU · ON" else "Lock-screen HARU · OFF")
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
                    if (lockScreenEnabled) {
                        "Lock-screen HARU is armed. On Xiaomi/POCO/Redmi phones, allow HARU to show on the lock screen in the phone's app permissions."
                    } else {
                        "Lock-screen HARU is off."
                    },
                    style = MaterialTheme.typography.labelSmall,
                )

                HaruOptionDropdown(
                    label = "Accent color",
                    selectedText = themeColor.label,
                    options = HaruThemeColor.entries,
                    optionLabel = { it.label },
                    isSelected = { it == themeColor },
                    onSelect = onSelectThemeColor,
                )

                HaruOptionDropdown(
                    label = "Background",
                    selectedText = backgroundTheme.label,
                    options = HaruBackgroundTheme.entries,
                    optionLabel = { it.label },
                    isSelected = { it == backgroundTheme },
                    onSelect = onSelectBackgroundTheme,
                )

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
    locationShareBusy: Boolean,
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
    var shareMinutes by remember { mutableIntStateOf(60) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(locations, liveTrackedLocation) {
        now = System.currentTimeMillis()
        while (isActive) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val visibleLocations = locations.filter { it.expiresAt > now }
    val visibleLiveLocation = liveTrackedLocation?.takeIf { it.expiresAt > now }

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
            "Share only with people you trust. Anyone with a live code can view that location until sharing stops or expires.",
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
            locations = visibleLocations,
            currentDeviceLocation = currentDeviceLocation,
            liveTrackedLocation = visibleLiveLocation,
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
        Spacer(Modifier.height(8.dp))
        HaruOptionDropdown(
            label = "Share duration",
            selectedText = "$shareMinutes minutes",
            options = listOf(15, 30, 60, 120),
            optionLabel = { "$it minutes" },
            isSelected = { it == shareMinutes },
            onSelect = { shareMinutes = it },
        )
        Button(
            onClick = {
                onCreateShare(
                    shareName.ifBlank { "Loved one" },
                    shareMinutes,
                )
            },
            enabled = !liveShareActive && !locationShareBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(when {
                locationShareBusy -> "Preparing share…"
                liveShareActive -> "Live sharing active"
                else -> "Start live share"
            })
        }

        if (shareCode.isNotBlank()) {
            OutlinedTextField(
                value = shareCode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Share package") },
                minLines = 2,
                maxLines = 4,
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

        if (liveShareActive || locationShareBusy || liveMonitorActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (liveShareActive || locationShareBusy) {
                    OutlinedButton(
                        onClick = onStopLiveShare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (locationShareBusy) "Cancel share" else "Stop sharing")
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

        if (visibleLiveLocation != null) {
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
                            "● " + visibleLiveLocation.name,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Shared marker · " +
                                (visibleLiveLocation.accuracyM?.let {
                                    "±" + it.toInt() + " m"
                                } ?: "accuracy unavailable"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(
                        onClick = {
                            onOpenUrl(
                                "https://www.google.com/maps/search/?api=1&query=" +
                                    visibleLiveLocation.latitude + "," +
                                    visibleLiveLocation.longitude
                            )
                        }
                    ) {
                        Text("Google Maps ↗")
                    }
                }
            }
        }

        if (visibleLocations.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text("Clear locations")
            }
        }

        Spacer(Modifier.height(8.dp))
        visibleLocations.forEach { item ->
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
                        enabled = !locationShareBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (currentDeviceLocation == null) {
                                "📍 Locating…"
                            } else {
                                "📍 Location ON"
                            }
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onLocateMe,
                        enabled = !locationShareBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("📍 My location")
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
        it.id + ":" + it.latitude + ":" + it.longitude + ":" + it.name + ":" + it.accuracyM
    }

    var lastFocusId by remember { mutableStateOf<String?>(null) }
    var mapDisposed by remember { mutableStateOf(false) }
    var mapLoadFailed by remember { mutableStateOf(false) }
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

            addOnDidFailLoadingMapListener { if (!mapDisposed) mapLoadFailed = true }
            getMapAsync { map ->
                if (mapDisposed) return@getMapAsync
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
                    if (mapDisposed) return@setStyle
                    mapLoadFailed = false
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
            mapDisposed = true
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
                        allLocations.take(22).forEach { item ->
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

                        if (focus != null && focus.id != lastFocusId) {
                            lastFocusId = focus.id
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
                        } else if (focus == null && map.cameraPosition.zoom < 3.5) {
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

    if (mapLoadFailed) {
        Text("Map tiles unavailable. Check your connection; coordinates and sharing controls remain available.",
            style = MaterialTheme.typography.bodySmall)
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

private fun Modifier.sizeCompat(size: androidx.compose.ui.unit.Dp): Modifier =
    this.width(size).height(size)
