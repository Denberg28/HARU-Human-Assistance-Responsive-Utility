package io.haru.assistant.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.haru.assistant.HaruViewModel
import io.haru.assistant.companion.CompanionSnapshot
import io.haru.assistant.journal.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun JournalPane(
    viewModel: HaruViewModel,
    companionSnapshot: CompanionSnapshot,
    checkerEnabled: Boolean,
    quiet: Boolean,
    visible: Boolean,
    onOpenToday: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var memorableOnly by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val today = rememberLocalCalendarDate()
    fun edit(date: LocalDate) { selectedDate = date.toString(); editing = true }

    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenToday) { Text("Today · ${companionSnapshot.tasks.count { !it.done }} tasks") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        if (checkerEnabled) HaruCheckerCard(companionSnapshot, quiet, false, "", visible && !editing)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { edit(today) }, enabled = viewModel.ready, modifier = Modifier.weight(1f)) {
                Text("Write today")
            }
            OutlinedButton(onClick = {
                val date = LocalDate.parse(selectedDate)
                DatePickerDialog(context, { _, y, m, d -> edit(LocalDate.of(y, m + 1, d)) },
                    date.year, date.monthValue - 1, date.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
            }, enabled = viewModel.ready, modifier = Modifier.weight(1f)) { Text("Choose date") }
        }
        JournalArchive(viewModel.entries, today, memorableOnly, { memorableOnly = it },
            viewModel.ready, viewModel.loadFailed, viewModel.status, viewModel::reload, ::edit,
            Modifier.weight(1f))
    }
    if (editing && viewModel.ready) JournalEditor(viewModel, LocalDate.parse(selectedDate), onDismiss = { viewModel.flush(); editing = false })
}


@Composable
internal fun JournalArchive(
    entries: List<JournalEntry>, today: LocalDate, memorableOnly: Boolean,
    onMemorableOnlyChange: (Boolean) -> Unit, ready: Boolean, loadFailed: Boolean,
    status: String, onRetry: () -> Unit, onEdit: (LocalDate) -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val archivePreferences = remember { context.getSharedPreferences("haru_journal_view", android.content.Context.MODE_PRIVATE) }
    // Stable bucket IDs retain expansion when a week moves beneath a month/year.
    // Keep hidden filter results independently so returning from Memorable restores them.
    var expanded by rememberSaveable { mutableStateOf(ArrayList(archivePreferences.getStringSet("expanded", emptySet()).orEmpty())) }
    LaunchedEffect(expanded) { archivePreferences.edit().putStringSet("expanded", expanded.toSet()).apply() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val filtered = remember(entries, memorableOnly) { entries.filter { !memorableOnly || it.memorable } }
    val nodes = remember(filtered, today) { JournalTimeline.build(filtered, today) }
    val rows = remember(nodes, expanded) { JournalTimeline.rows(nodes, expanded.toSet()) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = memorableOnly, onClick = { onMemorableOnlyChange(!memorableOnly) }, label = { Text("★ Memorable") })
            Spacer(Modifier.weight(1f))
            if (rows.any { it.node.children.isNotEmpty() && it.node.id in expanded }) TextButton(onClick = {
                // Find the surviving top-level parent of the visible row before
                // removing its descendants, then keep that header at the viewport.
                val first = rows.getOrNull(listState.firstVisibleItemIndex)
                val parent = first?.let { row -> nodes.firstOrNull { node -> node.entries.any { it.date == row.node.entries.first().date } } }
                val index = nodes.indexOf(parent).coerceAtLeast(0)
                expanded = arrayListOf()
                scope.launch { listState.scrollToItem(index) }
            }) { Text("Collapse all") }
        }
        if (loadFailed) TextButton(onClick = onRetry) { Text("Retry") }
        Text(status, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (ready && rows.isEmpty()) {
            Text(if (memorableOnly) "Star a date to find it here." else "A little space for your day. Start with today or choose a past date.",
                modifier = Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows, key = { it.node.id }, contentType = { it.node.level }) { row ->
                val node = row.node
                val open = node.id in expanded
                Surface(shape = MaterialTheme.shapes.medium,
                    color = if (node.level == JournalLevel.DAY) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 12).dp)
                        .animateItem(fadeInSpec = tween(140), fadeOutSpec = null, placementSpec = tween(180))
                        .clickable(role = Role.Button, onClickLabel = if (node.level == JournalLevel.DAY) "Open journal entry" else if (open) "Collapse ${node.label}" else "Expand ${node.label}") {
                            if (node.level == JournalLevel.DAY) onEdit(node.entries.single().date)
                            else {
                                val anchorId = rows.getOrNull(listState.firstVisibleItemIndex)?.node?.id
                                val offset = listState.firstVisibleItemScrollOffset
                                val next = ArrayList(expanded)
                                if (open) next.remove(node.id) else next.add(node.id)
                                val nextRows = JournalTimeline.rows(nodes, next.toSet())
                                val anchorIndex = nextRows.indexOfFirst { it.node.id == anchorId }
                                expanded = next
                                if (anchorIndex >= 0) scope.launch { listState.scrollToItem(anchorIndex, offset) }
                            }
                        }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (node.level != JournalLevel.DAY) Text(if (open) "▾" else "▸")
                            Text(node.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            if (node.stars > 0) Text("★" + if (node.level == JournalLevel.DAY) "" else " ${node.stars}",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { contentDescription = "${node.stars} memorable days" })
                        }
                        if (node.level == JournalLevel.DAY) {
                            Text(node.entries.single().text.ifBlank { "A memorable day" }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        } else Text(node.summary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEditor(viewModel: HaruViewModel, date: LocalDate, onDismiss: () -> Unit) {
    val entry = viewModel.entries.firstOrNull { it.date == date } ?: JournalEntry(date)
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
                Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = entry.memorable, onClick = { viewModel.update(date, entry.text, !entry.memorable) },
                        label = { Text(if (entry.memorable) "★ Memorable day" else "☆ Mark memorable") })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                OutlinedTextField(value = entry.text,
                    onValueChange = { viewModel.update(date, it, entry.memorable) },
                    placeholder = { Text("What would you like to remember?") },
                    modifier = Modifier.fillMaxWidth().weight(1f), minLines = 5,
                    supportingText = { Text("${entry.text.length} / ${HaruViewModel.MAX_TEXT_CHARS}") })
                Text(viewModel.status, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { confirmDelete = true }, enabled = entry.text.isNotEmpty() || entry.memorable) { Text("Delete entry") }
                    TextButton(onClick = viewModel::flush) { Text("Save") }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this entry?") },
        text = { Text("This removes the journal text and memorable marker for this date.") },
        confirmButton = { TextButton(onClick = { viewModel.delete(date); viewModel.flush(); onDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } })
}

@Composable
internal fun ReminderEditDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var dueText by rememberSaveable { mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    val due = LocalDateTime.parse(dueText)
    val context = LocalContext.current
    val dueAt = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reminder") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it.take(200) }, label = { Text("Remind me to…") })
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> dueText = due.withYear(y).withMonth(1).withDayOfMonth(1).withMonth(m + 1).withDayOfMonth(d).toString() }, due.year, due.monthValue - 1, due.dayOfMonth).show()
            }) { Text(due.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> dueText = due.withHour(h).withMinute(m).toString() }, due.hour, due.minute, false).show()
            }) { Text(due.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
            if (dueAt <= System.currentTimeMillis()) Text("Choose a future time.")
        } },
        confirmButton = { TextButton(enabled = text.isNotBlank() && dueAt > System.currentTimeMillis(), onClick = { onSave(text.trim(), dueAt) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
