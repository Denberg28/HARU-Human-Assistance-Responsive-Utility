package io.haru.assistant.companion

enum class HaruDailyState {
    SLEEPING,
    MORNING,
    FOCUSED,
    CELEBRATING,
    EVENING,
    IDLE,
}

data class HaruDailyPulse(
    val state: HaruDailyState,
    val greeting: String,
    val line: String,
)

object HaruDailyPulseFactory {
    fun create(
        hourOfDay: Int,
        snapshot: CompanionSnapshot,
    ): HaruDailyPulse {
        val openTasks = snapshot.tasks.count { !it.done }
        val completedTasks = snapshot.tasks.count { it.done }

        if (openTasks == 0) {
            if (completedTasks > 0) {
                return HaruDailyPulse(
                    state = HaruDailyState.CELEBRATING,
                    greeting = greetingFor(hourOfDay),
                    line = "Tasks clear. Nice work today. ✨",
                )
            }

            val state =
                when (hourOfDay) {
                    in 22..23, in 0..4 -> HaruDailyState.SLEEPING
                    in 5..11 -> HaruDailyState.MORNING
                    in 17..21 -> HaruDailyState.EVENING
                    else -> HaruDailyState.IDLE
                }

            val line =
                when (state) {
                    HaruDailyState.SLEEPING -> "Quiet night. Nothing pending."
                    HaruDailyState.MORNING -> "Quiet start. Nothing pending."
                    HaruDailyState.EVENING -> "Quiet evening. Nothing pending."
                    else -> "Quiet day. Nothing pending."
                }

            return HaruDailyPulse(
                state = state,
                greeting = greetingFor(hourOfDay),
                line = line,
            )
        }

        val state =
            when (hourOfDay) {
                in 22..23, in 0..4 -> HaruDailyState.SLEEPING
                in 5..11 -> HaruDailyState.MORNING
                in 17..21 -> HaruDailyState.EVENING
                else -> HaruDailyState.FOCUSED
            }

        val taskWord = if (openTasks == 1) "task" else "tasks"
        val line =
            when (state) {
                HaruDailyState.SLEEPING ->
                    "${openTasks} ${taskWord} waiting. HARU can hold the place for tomorrow."
                HaruDailyState.MORNING ->
                    "${openTasks} ${taskWord} today. Start with one small win."
                HaruDailyState.EVENING ->
                    "${openTasks} ${taskWord} left. One at a time."
                else ->
                    "${openTasks} ${taskWord} left. Stay with the first one."
            }

        return HaruDailyPulse(
            state = state,
            greeting = greetingFor(hourOfDay),
            line = line,
        )
    }

    fun focusLine(snapshot: CompanionSnapshot): String {
        val openTasks = snapshot.tasks.filterNot { it.done }
        val first = openTasks.firstOrNull() ?: return "✓ Tasks clear"
        val preview =
            if (first.text.length <= FOCUS_PREVIEW_CHARS) {
                first.text
            } else {
                first.text.take(FOCUS_PREVIEW_CHARS - 1).trimEnd() + "…"
            }

        val more =
            if (openTasks.size > 1) {
                " · +${openTasks.size - 1}"
            } else {
                ""
            }

        return "Focus · ${preview}${more}"
    }

    private fun greetingFor(hourOfDay: Int): String =
        when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Still here"
        }

    private const val FOCUS_PREVIEW_CHARS = 48
}
