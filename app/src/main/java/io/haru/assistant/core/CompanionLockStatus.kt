package io.haru.assistant.core

data class CompanionLockStatus(
    val title: String,
    val message: String,
)

fun CompanionMode.lockScreenStatus(): CompanionLockStatus =
    CompanionLockStatus(
        title = "HARU • $label",
        message = when (this) {
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
        },
    )
