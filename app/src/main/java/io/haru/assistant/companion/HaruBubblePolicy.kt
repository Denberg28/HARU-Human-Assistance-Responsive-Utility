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
            installedCount > 0 && !enabled -> "On Home screen · check-ins paused"
            installedCount > 0 -> "On Home screen · check-ins active"
            requestPending -> "Waiting for launcher confirmation. If no prompt appears, use the manual steps below."
            else -> "Not on Home screen yet"
        }
}

object HaruIdlePolicy {
    const val ACKNOWLEDGEMENT_MS = 1_400L
    private val CHECK_INTERVALS_MS =
        longArrayOf(
            90_000L,
            150_000L,
            210_000L,
        )

    fun canAdvance(enabled: Boolean, quiet: Boolean, busy: Boolean, draft: String): Boolean =
        enabled && !quiet && !busy && draft.isBlank()

    fun nextIntervalMillis(step: Long): Long =
        CHECK_INTERVALS_MS[
            Math.floorMod(step, CHECK_INTERVALS_MS.size.toLong()).toInt()
        ]
}
