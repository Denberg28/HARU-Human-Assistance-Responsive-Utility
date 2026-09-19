package io.haru.assistant.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CompanionTask(
    val text: String,
    val done: Boolean = false,
)

data class CompanionReminder(
    val id: String,
    val text: String,
    val dueAt: Long,
)

data class CompanionSnapshot(
    val notes: List<String> = emptyList(),
    val tasks: List<CompanionTask> = emptyList(),
    val reminders: List<CompanionReminder> = emptyList(),
) {
    fun todayLines(now: Long = System.currentTimeMillis()): List<String> {
        val lines = mutableListOf<String>()
        val openTasks = tasks.filterNot { it.done }.take(2)
        if (openTasks.isNotEmpty()) {
            lines += "Tasks: " + openTasks.joinToString(" · ") { it.text }
        }

        reminders
            .filter { it.dueAt > now }
            .sortedBy { it.dueAt }
            .take(2)
            .forEach { reminder ->
                val minutes = ((reminder.dueAt - now) / 60_000L).coerceAtLeast(0)
                val whenText = when {
                    minutes < 60 -> "in " + minutes + " min"
                    minutes < 1440 -> "in " + (minutes / 60) + " hr"
                    else -> "in " + (minutes / 1440) + " day"
                }
                lines += "⏰ " + reminder.text + " · " + whenText
            }
        return lines
    }
}

class AndroidCompanionStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences("haru_companion", Context.MODE_PRIVATE)

    fun load(): CompanionSnapshot {
        val notes = readStringArray("notes")
        val tasks = readTasks()
        val reminders = readReminders()
        return CompanionSnapshot(notes, tasks, reminders)
    }

    fun addNote(text: String): CompanionSnapshot {
        val snapshot = load()
        val notes = (snapshot.notes + clean(text)).filter { it.isNotBlank() }.takeLast(200)
        saveNotes(notes)
        return load()
    }

    fun clearNotes(): CompanionSnapshot {
        saveNotes(emptyList())
        return load()
    }

    fun addTask(text: String): CompanionSnapshot {
        val snapshot = load()
        val tasks = (snapshot.tasks + CompanionTask(clean(text))).filter { it.text.isNotBlank() }.takeLast(200)
        saveTasks(tasks)
        return load()
    }

    fun completeTask(index: Int): CompanionSnapshot {
        val snapshot = load()
        if (index !in snapshot.tasks.indices) return snapshot
        val tasks = snapshot.tasks.mapIndexed { i, item ->
            if (i == index) item.copy(done = true) else item
        }
        saveTasks(tasks)
        return load()
    }

    fun clearTasks(): CompanionSnapshot {
        saveTasks(emptyList())
        return load()
    }

    fun addReminder(text: String, dueAt: Long): CompanionReminder {
        val snapshot = load()
        val reminder = CompanionReminder(
            id = UUID.randomUUID().toString(),
            text = clean(text),
            dueAt = dueAt,
        )
        saveReminders((snapshot.reminders + reminder).takeLast(200))
        return reminder
    }

    fun removeReminder(id: String): CompanionSnapshot {
        val snapshot = load()
        saveReminders(snapshot.reminders.filterNot { it.id == id })
        return load()
    }

    fun clearReminders(): CompanionSnapshot {
        saveReminders(emptyList())
        return load()
    }

    fun parseRelativeReminder(command: String): Pair<String, Long>? {
        val regex = Regex(
            """(?i)^remind me in\s+(\d{1,4})\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days)\s+(?:to\s+)?(.+)$"""
        )
        val match = regex.matchEntire(command.trim()) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        if (amount <= 0) return null

        val unit = match.groupValues[2].lowercase()
        val delay = when (unit) {
            "minute", "minutes", "min", "mins" -> TimeUnit.MINUTES.toMillis(amount)
            "hour", "hours", "hr", "hrs" -> TimeUnit.HOURS.toMillis(amount)
            else -> TimeUnit.DAYS.toMillis(amount)
        }
        if (delay > TimeUnit.DAYS.toMillis(30)) return null

        val text = clean(match.groupValues[3])
        if (text.isBlank()) return null
        return text to (System.currentTimeMillis() + delay)
    }

    private fun clean(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").take(500)

    private fun readStringArray(key: String): List<String> {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = clean(array.optString(i))
                    if (value.isNotBlank()) add(value)
                }
            }.takeLast(200)
        }.getOrDefault(emptyList())
    }

    private fun saveNotes(notes: List<String>) {
        val array = JSONArray()
        notes.takeLast(200).forEach(array::put)
        preferences.edit().putString("notes", array.toString()).apply()
    }

    private fun readTasks(): List<CompanionTask> {
        val raw = preferences.getString("tasks", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val text = clean(item.optString("text"))
                    if (text.isNotBlank()) {
                        add(CompanionTask(text, item.optBoolean("done", false)))
                    }
                }
            }.takeLast(200)
        }.getOrDefault(emptyList())
    }

    private fun saveTasks(tasks: List<CompanionTask>) {
        val array = JSONArray()
        tasks.takeLast(200).forEach { task ->
            array.put(
                JSONObject()
                    .put("text", task.text)
                    .put("done", task.done)
            )
        }
        preferences.edit().putString("tasks", array.toString()).apply()
    }

    private fun readReminders(): List<CompanionReminder> {
        val raw = preferences.getString("reminders", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val text = clean(item.optString("text"))
                    val dueAt = item.optLong("dueAt", 0L)
                    if (id.isNotBlank() && text.isNotBlank() && dueAt > 0) {
                        add(CompanionReminder(id, text, dueAt))
                    }
                }
            }.takeLast(200)
        }.getOrDefault(emptyList())
    }

    private fun saveReminders(reminders: List<CompanionReminder>) {
        val array = JSONArray()
        reminders.takeLast(200).forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("text", reminder.text)
                    .put("dueAt", reminder.dueAt)
            )
        }
        preferences.edit().putString("reminders", array.toString()).apply()
    }
}
