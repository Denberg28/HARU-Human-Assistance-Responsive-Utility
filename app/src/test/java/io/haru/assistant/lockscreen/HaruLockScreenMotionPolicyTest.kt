package io.haru.assistant.lockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruLockScreenMotionPolicyTest {

    @Test
    fun nightDefaultsToSleeping() {
        assertEquals(
            HaruLockMotion.SLEEPING,
            HaruLockScreenMotionPolicy.motion(
                hourOfDay = 2,
                nowElapsed = 10_000L,
                quiet = false,
                delightedUntil = 0L,
            ),
        )
    }

    @Test
    fun petReactionOverridesNightSleepBriefly() {
        val now = 50_000L
        assertEquals(
            HaruLockMotion.DELIGHTED,
            HaruLockScreenMotionPolicy.motion(
                hourOfDay = 2,
                nowElapsed = now,
                quiet = false,
                delightedUntil = now + 2_000L,
            ),
        )
    }

    @Test
    fun nightAnimationStillUsesBreathingCadence() {
        assertTrue(
            HaruLockScreenMotionPolicy.frameDelayMillis(
                HaruLockMotion.SLEEPING,
            ) <= 200L
        )
    }

    @Test
    fun interactionLastsLongEnoughToBeVisible() {
        assertTrue(HaruLockScreenMotionPolicy.INTERACTION_MS >= 3_000L)
    }
}
