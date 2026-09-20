package io.haru.assistant.companion

import io.haru.assistant.core.HaruFaces

data class HaruBubbleContent(
    val face: String,
    val greeting: String,
    val line: String,
)

object HaruBubbleContentFactory {
    private val idleLines =
        listOf(
            "Ready when you are.",
            "Anything for today?",
            "I'm here.",
            "Need a quick reminder?",
            "One thing at a time.",
            "What are we working on?",
        )

    fun create(
        hourOfDay: Int,
        step: Long,
        snapshot: CompanionSnapshot,
        now: Long,
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
        val reminders =
            snapshot.reminders.count { it.dueAt > now }

        val line =
            when {
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
                else ->
                    idleLines[
                        Math.floorMod(
                            step,
                            idleLines.size.toLong(),
                        ).toInt()
                    ]
            }

        return HaruBubbleContent(
            face =
                HaruFaces.idleRotation(
                    hourOfDay = hourOfDay,
                    step = step,
                ),
            greeting = greeting,
            line = line,
        )
    }
}
