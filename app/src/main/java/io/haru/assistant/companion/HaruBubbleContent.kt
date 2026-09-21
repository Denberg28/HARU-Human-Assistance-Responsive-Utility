package io.haru.assistant.companion

import io.haru.assistant.core.HaruFaces

data class HaruBubbleContent(
    val face: String,
    val greeting: String,
    val line: String,
    val conversationPrompt: String = "Let's have a short chat.",
)

object HaruBubbleContentFactory {
    private val idleLines =
        listOf(
            "What's one small win today?" to "Help me reflect on one small win today.",
            "What shall we work on?" to "Help me choose one thing to work on today.",
            "How is your day going?" to "Ask me how my day is going. Keep it conversational.",
            "Time for a little breather?" to "Suggest a simple one-minute break.",
            "One thing at a time." to "Help me break my next task into one small step.",
            "Something on your mind?" to "Let's have a short, friendly conversation.",
        )

    fun create(
        hourOfDay: Int,
        step: Long,
        snapshot: CompanionSnapshot,
        now: Long,
        quiet: Boolean = false,
    ): HaruBubbleContent {
        val greeting =
            when (hourOfDay) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Still here"
            }

        val openTasks =
            snapshot.tasks.count { !it.done }
        // Keep overdue, undelivered reminders visible instead of silently hiding them.
        val reminders = snapshot.reminders.size
        val overdue = snapshot.reminders.count { it.dueAt <= now }
        val idle = idleLines[Math.floorMod(step, idleLines.size.toLong()).toInt()]

        val line =
            when {
                quiet -> "A quiet moment. I'm here when you need me."
                Math.floorMod(step, 3L) != 0L -> idle.first
                overdue > 0 -> "$overdue reminder" + (if (overdue == 1) "" else "s") + " due. Open Today."
                reminders > 0 && openTasks > 0 ->
                    "$openTasks task" +
                        (if (openTasks == 1) "" else "s") +
                        " · $reminders reminder" +
                        (if (reminders == 1) "" else "s")
                reminders > 0 ->
                    "$reminders reminder" +
                        (if (reminders == 1) "" else "s") +
                        " waiting."
                openTasks > 0 ->
                    "$openTasks task" +
                        (if (openTasks == 1) "" else "s") +
                        " for today."
                else -> idle.first
            }

        return HaruBubbleContent(
            face =
                HaruFaces.idleRotation(
                    hourOfDay = hourOfDay,
                    step = if (quiet) 0L else step,
                ),
            greeting = greeting,
            line = line,
            conversationPrompt = if (quiet) "Let's have a short, quiet chat."
                else if (Math.floorMod(step, 3L) == 0L && (openTasks > 0 || reminders > 0)) "today"
                else idle.second,
        )
    }
}
