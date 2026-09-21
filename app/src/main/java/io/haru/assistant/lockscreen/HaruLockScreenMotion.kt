package io.haru.assistant.lockscreen

enum class HaruLockMotion {
    IDLE,
    RUNNING,
    RESTING,
    SLEEPING,
    DELIGHTED,
}

object HaruLockScreenMotionPolicy {
    const val INTERACTION_MS = 4_200L
    const val MOTION_SLOT_MS = 9_000L

    const val DELIGHTED_FRAME_MS = 80L
    const val RUNNING_FRAME_MS = 100L
    const val SLEEPING_FRAME_MS = 160L
    const val IDLE_FRAME_MS = 220L

    fun isNight(hourOfDay: Int): Boolean =
        hourOfDay >= 22 || hourOfDay <= 5

    fun motion(
        hourOfDay: Int,
        nowElapsed: Long,
        quiet: Boolean,
        delightedUntil: Long,
    ): HaruLockMotion {
        if (nowElapsed < delightedUntil) {
            return HaruLockMotion.DELIGHTED
        }
        if (quiet || isNight(hourOfDay)) {
            return HaruLockMotion.SLEEPING
        }

        val slot =
            Math.floorMod(
                nowElapsed / MOTION_SLOT_MS,
                12L,
            )

        return when (slot) {
            0L, 1L, 6L -> HaruLockMotion.RUNNING
            2L, 7L, 10L -> HaruLockMotion.RESTING
            else -> HaruLockMotion.IDLE
        }
    }

    fun frameDelayMillis(motion: HaruLockMotion): Long =
        when (motion) {
            HaruLockMotion.DELIGHTED -> DELIGHTED_FRAME_MS
            HaruLockMotion.RUNNING -> RUNNING_FRAME_MS
            HaruLockMotion.SLEEPING -> SLEEPING_FRAME_MS
            HaruLockMotion.RESTING,
            HaruLockMotion.IDLE -> IDLE_FRAME_MS
        }
}
