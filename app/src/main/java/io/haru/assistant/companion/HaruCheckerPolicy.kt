package io.haru.assistant.companion

object HaruIdlePolicy {
    const val ACKNOWLEDGEMENT_MS = 1_400L

    private val CHECK_INTERVALS_MS =
        longArrayOf(
            90_000L,
            150_000L,
            210_000L,
        )

    fun canAdvance(
        enabled: Boolean,
        quiet: Boolean,
        busy: Boolean,
        draft: String,
    ): Boolean =
        enabled && !quiet && !busy && draft.isBlank()

    fun nextIntervalMillis(step: Long): Long =
        CHECK_INTERVALS_MS[
            Math.floorMod(
                step,
                CHECK_INTERVALS_MS.size.toLong(),
            ).toInt()
        ]
}
