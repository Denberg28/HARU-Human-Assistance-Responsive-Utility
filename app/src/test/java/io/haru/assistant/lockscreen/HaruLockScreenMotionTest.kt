package io.haru.assistant.lockscreen

import io.haru.assistant.core.HaruFaces
import io.haru.assistant.core.HaruMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruLockScreenMotionTest {
    @Test
    fun nightUsesSleepingMotion() {
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
    fun petReactionOverridesOtherMotion() {
        assertEquals(
            HaruLockMotion.DELIGHTED,
            HaruLockScreenMotionPolicy.motion(
                hourOfDay = 2,
                nowElapsed = 1_000L,
                quiet = true,
                delightedUntil = 2_000L,
            ),
        )
    }

    @Test
    fun originalFacePackRemainsUsed() {
        assertEquals(
            HaruFaces.forMood(HaruMood.SLEEPY),
            HaruLockScreenScene.faceFor(
                HaruLockMotion.SLEEPING,
                "idle",
                "happy",
            ),
        )
        assertTrue(HaruLockScreenMotionPolicy.RUNNING_FRAME_MS < HaruLockScreenMotionPolicy.IDLE_FRAME_MS)
    }
}
