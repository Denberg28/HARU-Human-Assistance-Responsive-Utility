package io.haru.assistant.companion

/** Placement is verified from the launcher, never inferred from the enabled switch. */
data class HaruBubbleStatus(
    val installedCount: Int = 0,
    val enabled: Boolean = true,
    val pinSupported: Boolean = false,
    val requestPending: Boolean = false,
) {
    val message: String
        get() = when {
            installedCount > 0 && !enabled -> "On Home screen · paused"
            installedCount > 0 -> "On Home screen · active"
            requestPending -> "Waiting for launcher confirmation. If no prompt appears, use the manual steps below."
            else -> "Not on Home screen yet"
        }
}

object HaruIdlePolicy {
    const val INTERVAL_MS = 20_000L

    fun canAdvance(enabled: Boolean, quiet: Boolean, busy: Boolean, draft: String): Boolean =
        enabled && !quiet && !busy && draft.isBlank()
}
