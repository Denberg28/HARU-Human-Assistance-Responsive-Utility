package io.haru.assistant.core

data class CompanionLockStatus(
    val title: String,
    val message: String,
)

fun CompanionMode.lockScreenStatus(
    openTasks: Int = 0,
    upcomingReminders: Int = 0,
    liveShareEnabled: Boolean = false,
    liveMonitorEnabled: Boolean = false,
): CompanionLockStatus {
    val generic =
        when (this) {
            CompanionMode.NORMAL ->
                "Everyday companion • ready"
            CompanionMode.FLIGHT ->
                "Flight companion • checklist and flight support"
            CompanionMode.TRAVEL ->
                "Travel companion • navigation and hazard support"
            CompanionMode.WORK ->
                "Work companion • tasks and reminders"
            CompanionMode.SAFETY ->
                "Safety companion • location and hazard support"
            CompanionMode.REST ->
                "Rest companion • quiet standby"
        }

    val message =
        when {
            liveShareEnabled ->
                "Live location share enabled • $label mode"
            liveMonitorEnabled ->
                "Live location tracking enabled • $label mode"
            upcomingReminders > 0 ->
                "$upcomingReminders upcoming reminder" +
                    (if (upcomingReminders == 1) "" else "s") +
                    " • $label mode"
            openTasks > 0 ->
                "$openTasks open task" +
                    (if (openTasks == 1) "" else "s") +
                    " • $label mode"
            else -> generic
        }

    return CompanionLockStatus(
        title = "HARU • $label",
        message = message,
    )
}
