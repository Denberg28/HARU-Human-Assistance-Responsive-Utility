package io.haru.assistant.companion

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

sealed interface HaruChatActionResult {
    data object None : HaruChatActionResult

    data class AddTask(
        val text: String,
    ) : HaruChatActionResult

    data class SetReminder(
        val text: String,
        val dueAt: Long,
    ) : HaruChatActionResult

    data class Clarify(
        val message: String,
    ) : HaruChatActionResult
}

/**
 * Small deterministic action interpreter for chat and voice.
 *
 * Only explicit user requests are executed. The online model never receives
 * direct write access to HARU's local task/reminder store.
 */
object HaruChatActionParser {
    fun parse(
        input: String,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): HaruChatActionResult {
        val clean =
            input
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(MAX_INPUT_CHARS)

        if (clean.isBlank()) return HaruChatActionResult.None

        val normalized = stripPolitePrefix(clean)

        parseTask(normalized)?.let {
            return HaruChatActionResult.AddTask(it)
        }

        parseRelativeReminder(
            normalized,
            nowMillis,
        )?.let {
            return it
        }

        parseAbsoluteReminder(
            normalized,
            nowMillis,
            zoneId,
        )?.let {
            return it
        }

        if (looksLikeIncompleteReminder(normalized)) {
            return HaruChatActionResult.Clarify(
                "Tell me the reminder time too — for example, “remind me tomorrow at 8 AM to call Mom” or “remind me in 30 minutes to check the rover.”"
            )
        }

        return HaruChatActionResult.None
    }

    private fun parseTask(value: String): String? {
        val patterns =
            listOf(
                Regex(
                    """(?i)^(?:task|add task|todo|to-do|add to tasks)\s+(.+)$"""
                ),
                Regex(
                    """(?i)^(?:add|put)\s+(.+?)\s+(?:to|on)\s+(?:my\s+)?(?:task list|tasks|to-do list|todo list)\??[.!]?$"""
                ),
                Regex(
                    """(?i)^make\s+(.+?)\s+(?:a\s+)?(?:task|to-do)\??[.!]?$"""
                ),
            )

        patterns.forEach { pattern ->
            val match = pattern.matchEntire(value) ?: return@forEach
            return cleanActionText(match.groupValues[1])
        }

        return null
    }

    private fun parseRelativeReminder(
        value: String,
        nowMillis: Long,
    ): HaruChatActionResult.SetReminder? {
        val match =
            Regex(
                """(?i)^(?:remind me|set (?:a )?reminder)(?:\s+for)?\s+in\s+(\d{1,4})\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days)\s+(?:to|about)\s+(.+)$"""
            ).matchEntire(value)
                ?: return null

        val amount = match.groupValues[1].toLongOrNull() ?: return null
        if (amount <= 0) return null

        val unit = match.groupValues[2].lowercase()
        val delay =
            when (unit) {
                "minute", "minutes", "min", "mins" -> amount * 60_000L
                "hour", "hours", "hr", "hrs" -> amount * 60L * 60_000L
                else -> amount * 24L * 60L * 60_000L
            }

        if (delay > MAX_REMINDER_DELAY_MS) return null

        val text = cleanActionText(match.groupValues[3]) ?: return null
        return HaruChatActionResult.SetReminder(
            text = text,
            dueAt = nowMillis + delay,
        )
    }

    private fun parseAbsoluteReminder(
        value: String,
        nowMillis: Long,
        zoneId: ZoneId,
    ): HaruChatActionResult? {
        val leadingTime =
            Regex(
                """(?i)^(?:remind me|set (?:a )?reminder)(?:\s+for)?\s+(?:(today|tomorrow)\s+)?(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s+(?:to|about|[-:])\s+(.+)$"""
            ).matchEntire(value)

        if (leadingTime != null) {
            val day = leadingTime.groupValues[1]
            val text = cleanActionText(leadingTime.groupValues[5])
                ?: return HaruChatActionResult.None
            return buildAbsoluteReminder(
                dayToken = day,
                hourToken = leadingTime.groupValues[2],
                minuteToken = leadingTime.groupValues[3],
                meridiemToken = leadingTime.groupValues[4],
                text = text,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )
        }

        val trailingTime =
            Regex(
                """(?i)^remind me\s+(?:to|about)\s+(.+?)\s+(?:(today|tomorrow)\s+)?at\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?[.!]?$"""
            ).matchEntire(value)

        if (trailingTime != null) {
            val text = cleanActionText(trailingTime.groupValues[1])
                ?: return HaruChatActionResult.None
            return buildAbsoluteReminder(
                dayToken = trailingTime.groupValues[2],
                hourToken = trailingTime.groupValues[3],
                minuteToken = trailingTime.groupValues[4],
                meridiemToken = trailingTime.groupValues[5],
                text = text,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )
        }

        return null
    }

    private fun buildAbsoluteReminder(
        dayToken: String,
        hourToken: String,
        minuteToken: String,
        meridiemToken: String,
        text: String,
        nowMillis: Long,
        zoneId: ZoneId,
    ): HaruChatActionResult {
        val rawHour = hourToken.toIntOrNull()
            ?: return HaruChatActionResult.None
        val minute = minuteToken.ifBlank { "0" }.toIntOrNull()
            ?: return HaruChatActionResult.None

        if (minute !in 0..59) {
            return HaruChatActionResult.Clarify(
                "That reminder time is not valid. Try a time like 8:30 AM."
            )
        }

        val meridiem = meridiemToken.lowercase()
        val hour =
            if (meridiem.isNotBlank()) {
                if (rawHour !in 1..12) {
                    return HaruChatActionResult.Clarify(
                        "That reminder time is not valid. Try a time like 8:00 AM."
                    )
                }
                when {
                    meridiem == "am" && rawHour == 12 -> 0
                    meridiem == "pm" && rawHour < 12 -> rawHour + 12
                    else -> rawHour
                }
            } else {
                if (rawHour !in 0..23) {
                    return HaruChatActionResult.Clarify(
                        "That reminder time is not valid. Use 24-hour time or include AM/PM."
                    )
                }
                rawHour
            }

        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val explicitDay = dayToken.lowercase()
        val baseDate =
            when (explicitDay) {
                "tomorrow" -> now.toLocalDate().plusDays(1)
                else -> now.toLocalDate()
            }

        var due =
            LocalDateTime.of(
                baseDate,
                LocalTime.of(hour, minute),
            ).atZone(zoneId)

        if (explicitDay == "today" && !due.isAfter(now)) {
            return HaruChatActionResult.Clarify(
                "That time has already passed today. Give me a future time."
            )
        }

        if (explicitDay.isBlank() && !due.isAfter(now)) {
            due = due.plusDays(1)
        }

        if (!due.isAfter(now)) {
            return HaruChatActionResult.Clarify(
                "Give me a future reminder time."
            )
        }

        return HaruChatActionResult.SetReminder(
            text = text,
            dueAt = due.toInstant().toEpochMilli(),
        )
    }

    private fun looksLikeIncompleteReminder(value: String): Boolean =
        Regex("""(?i)^remind me\s+(?:to|about)\s+.+$""")
            .matches(value) ||
            Regex("""(?i)^set (?:a )?reminder(?:\s+for)?(?:\s+.+)?$""")
                .matches(value)

    private fun stripPolitePrefix(value: String): String {
        var result = value.trim()
        result = result.replaceFirst(
            Regex("""(?i)^please\s+"""),
            "",
        )
        result = result.replaceFirst(
            Regex("""(?i)^(?:can|could|would)\s+you\s+"""),
            "",
        )
        result = result.replaceFirst(
            Regex("""(?i)^please\s+"""),
            "",
        )
        return result.trim()
    }

    private fun cleanActionText(value: String): String? {
        val clean =
            value
                .trim()
                .trimEnd('.', '?', '!')
                .replace(Regex("\\s+"), " ")
                .take(MAX_ACTION_CHARS)

        return clean.takeIf { it.isNotBlank() }
    }

    private const val MAX_INPUT_CHARS = 2000
    private const val MAX_ACTION_CHARS = 500
    private const val MAX_REMINDER_DELAY_MS =
        30L * 24L * 60L * 60L * 1000L
}
