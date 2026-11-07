package io.haru.assistant.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.haru.assistant.finance.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun FinancePane(model: FinanceViewModel = viewModel()) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    FinanceContent(model.entries, model.ready, model.busy, model.error, model::reload,
        onAdd = { editingId = null; editing = true },
        onEdit = { editingId = it.id; editing = true })
    if (editing) FinanceEditor(
        entry = model.entries.firstOrNull { it.id == editingId },
        categories = model.entries.map { it.category },
        busy = model.busy, error = model.error,
        onDismiss = { editing = false },
        onSave = { model.save(it) { editing = false } },
        onDelete = { model.delete(it) { editing = false } },
    )
}

@Composable
internal fun FinanceContent(
    entries: List<FinanceEntry>, ready: Boolean, busy: Boolean, error: String?,
    onRetry: () -> Unit, onAdd: () -> Unit, onEdit: (FinanceEntry) -> Unit,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val now = YearMonth.now()
                if (monthText == currentMonth.toString()) monthText = now.toString()
                currentMonth = now
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val month = YearMonth.parse(monthText)
    val filtered = remember(entries, month, allTime) { Finance.filter(entries, month.takeUnless { allTime }) }
    val summary = remember(filtered) { Finance.summary(filtered) }
    val groups = remember(filtered) { Finance.groups(filtered) }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = !allTime,
                    modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("‹") }
                Text(if (allTime) "All time" else month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = !allTime && month < currentMonth,
                    modifier = Modifier.semantics { contentDescription = "Next month" }) { Text("›") }
                FilterChip(selected = allTime, onClick = { allTime = !allTime }, label = { Text("All") })
            }
        }
        if (!ready) {
            item {
                if (error != null) { Text(error); TextButton(onClick = onRetry, enabled = !busy) { Text("Retry") } }
                else Text("Opening finances…", Modifier.padding(vertical = 24.dp))
            }
        } else {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Net · ${if (allTime) "all time" else "selected month"}", style = MaterialTheme.typography.labelMedium)
                        Text(Finance.money(summary.net), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) { Text("Income", style = MaterialTheme.typography.labelMedium); Text(Finance.money(summary.income)) }
                            Column(Modifier.weight(1f)) { Text("Expenses", style = MaterialTheme.typography.labelMedium); Text(Finance.money(summary.expenses)) }
                        }
                        Text("Based on your recorded entries", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { FinanceSpendingChart(groups.filter { it.type == FinanceType.EXPENSE }) }
            item { Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Add entry") } }
            if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Categories", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (expanded.isNotEmpty()) TextButton(onClick = { expanded = arrayListOf() }) { Text("Collapse all") }
                }
            }
            if (groups.isEmpty()) item { Text("No entries for this period. Add income or an expense to start.",
                Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            groups.forEach { group ->
                val open = group.id in expanded
                item(key = "category:${group.id}") {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button,
                            onClickLabel = if (open) "Collapse ${group.category}" else "Expand ${group.category}") {
                            expanded = ArrayList(expanded).apply { if (open) remove(group.id) else add(group.id) }
                        }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (open) "▾" else "▸")
                            Column(Modifier.weight(1f)) {
                                Text(group.category, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${group.type.label} · ${group.entries.size} ${if (group.entries.size == 1) "entry" else "entries"}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${if (group.type == FinanceType.INCOME) "+" else "−"}${Finance.money(group.cents)}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (open) items(group.entries, key = { "entry:${it.id}" }) { entry ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Edit entry") { onEdit(entry) }
                        .padding(start = 24.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.note.ifBlank { entry.category }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Finance.money(entry.cents), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text("On this phone · PHP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** A compact, labelled composition chart; all expenses contribute, including the tail. */
@Composable
internal fun FinanceSpendingChart(groups: List<FinanceGroup>) {
    val total = groups.sumOf { it.cents }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Spending breakdown", style = MaterialTheme.typography.labelLarge)
        if (total == 0L) {
            Text("No expenses in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val parts = groups.take(4).map { it.category to it.cents }.toMutableList()
        if (groups.size > 4) parts.add("Remaining categories" to groups.drop(4).sumOf { it.cents })
        val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurfaceVariant)
        val description = parts.joinToString { (name, cents) -> "$name: ${Finance.money(cents)}" }
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).semantics { contentDescription = "Spending breakdown. $description" }) {
            parts.forEachIndexed { index, part ->
                Box(Modifier.weight((part.second.toDouble() / total).toFloat()).fillMaxHeight().background(colors[index]))
            }
        }
        parts.forEachIndexed { index, (name, cents) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(colors[index], CircleShape))
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val percent = cents.toDouble() * 100 / total
                Text(if (percent < 1) "<1%" else "${kotlin.math.round(percent).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun FinanceEditor(
    entry: FinanceEntry?, categories: List<String>, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (FinanceEntry) -> Unit, onDelete: (String) -> Unit,
) {
    var id by rememberSaveable { mutableStateOf(entry?.id ?: java.util.UUID.randomUUID().toString()) }
    val createdAt by rememberSaveable { mutableStateOf(entry?.createdAt ?: System.currentTimeMillis()) }
    var amount by rememberSaveable { mutableStateOf(entry?.let { Finance.amountText(it.cents) }.orEmpty()) }
    var typeName by rememberSaveable { mutableStateOf((entry?.type ?: FinanceType.EXPENSE).name) }
    val type = FinanceType.valueOf(typeName)
    var category by rememberSaveable { mutableStateOf(entry?.category ?: Finance.categories(type).first()) }
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf((entry?.date ?: LocalDate.now()).toString()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val date = LocalDate.parse(dateText)
    val cents = Finance.parseAmount(amount)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (entry == null) "Add entry" else "Edit entry") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinanceType.entries.forEach { choice ->
                        FilterChip(selected = type == choice, enabled = !busy, onClick = {
                            typeName = choice.name
                            category = Finance.categories(choice).first()
                        }, label = { Text(choice.label) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 12) amount = it },
                    label = { Text("Amount (PHP)") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotEmpty() && cents == null,
                    supportingText = if (amount.isNotEmpty() && cents == null) { { Text("Enter a positive amount, up to 2 decimal places.") } } else null,
                    modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedTextField(value = category, onValueChange = { if (it.length <= 32) category = it },
                        label = { Text("Category") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { TextButton(onClick = { menu = true }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Choose category" }) { Text("▾") } })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                        (Finance.categories(type) + categories).distinctBy { it.lowercase(Locale.ROOT) }.forEach { suggestion ->
                            DropdownMenuItem(text = { Text(suggestion) }, onClick = { category = suggestion; menu = false })
                        }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { if (it.length <= 200) note = it },
                    label = { Text("Note (optional)") }, maxLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !busy, onClick = {
                    DatePickerDialog(context, { _, y, m, d -> dateText = LocalDate.of(y, m + 1, d).toString() },
                        date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }) { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (entry != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete entry") }
            }
        },
        confirmButton = { TextButton(enabled = !busy && cents != null && category.isNotBlank() && date <= LocalDate.now(),
            onClick = { onSave(FinanceEntry(id, date, type, requireNotNull(cents), category.trim(), note.trim(), createdAt)) }) {
            Text(if (busy) "Saving…" else "Save")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
    if (confirmDelete && entry != null) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("Delete this entry?") }, text = { Text("Your totals will update after it is deleted.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(entry.id) }) { Text("Delete") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Keep") } })
}
