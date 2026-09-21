package io.haru.assistant.companion

import io.haru.assistant.core.HaruFaces

data class HaruCheckerContent(
    val face: String,
    val greeting: String,
    val line: String,
)

data class HaruAcknowledgement(
    val face: String,
    val line: String,
)

object HaruCheckerContentFactory {
    private val checkInLines =
        listOf(
            "Had some water lately?",
            "Need a tiny breather?",
            "Eyes need a short screen break?",
            "Shoulders okay? Tiny stretch?",
            "Anything you don't want to forget?",
            "One small win today?",
            "All good over there?",
            "Have you eaten something?",
            "How's your energy?",
            "Just checking in on you.",
        )

    private val acknowledgements =
        listOf(
            HaruAcknowledgement("(˶ᵔ ᵕ ᵔ˶) ♡", "Purr~ ♡"),
            HaruAcknowledgement("ฅ(˶ᵔ ᵕ ᵔ˶)ฅ", "Hehe~ noticed ♡"),
            HaruAcknowledgement("( ˶ˆᗜˆ˵ ) ♡", "Happy HARU noises~"),
            HaruAcknowledgement("(˵ •̀ ᴗ •́ ˵ ) ✧", "Mhm~ I'm here ♡"),
        )

    fun create(
        hourOfDay: Int,
        step: Long,
        snapshot: CompanionSnapshot,
        now: Long,
        quiet: Boolean = false,
    ): HaruCheckerContent {
        val greeting =
            when (hourOfDay) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Still here"
            }

        val openTasks = snapshot.tasks.count { !it.done }
        val reminders = snapshot.reminders.size
        val overdue = snapshot.reminders.count { it.dueAt <= now }
        val index =
            Math.floorMod(
                step * 31L + hourOfDay,
                checkInLines.size.toLong(),
            ).toInt()
        val checkIn = checkInLines[index]

        val line =
            when {
                quiet -> "Just checking in quietly."
                Math.floorMod(step, 4L) != 0L -> checkIn
                overdue > 0 ->
                    "$overdue reminder" +
                        (if (overdue == 1) "" else "s") +
                        " due. Just a gentle nudge."
                reminders > 0 && openTasks > 0 ->
                    "$openTasks task" +
                        (if (openTasks == 1) "" else "s") +
                        " · $reminders reminder" +
                        (if (reminders == 1) "" else "s") +
                        " waiting."
                reminders > 0 ->
                    "$reminders reminder" +
                        (if (reminders == 1) "" else "s") +
                        " waiting."
                openTasks > 0 ->
                    "$openTasks task" +
                        (if (openTasks == 1) "" else "s") +
                        " for today."
                else -> checkIn
            }

        return HaruCheckerContent(
            face =
                HaruFaces.idleRotation(
                    hourOfDay = hourOfDay,
                    step = if (quiet) 0L else step,
                ),
            greeting = greeting,
            line = line,
        )
    }

    fun acknowledgement(step: Long): HaruAcknowledgement =
        acknowledgements[
            Math.floorMod(
                step,
                acknowledgements.size.toLong(),
            ).toInt()
        ]
}
