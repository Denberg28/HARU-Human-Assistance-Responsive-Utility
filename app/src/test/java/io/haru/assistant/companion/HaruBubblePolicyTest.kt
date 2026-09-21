package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruBubblePolicyTest {
    @Test
    fun enablingOrRequestingDoesNotClaimPlacement() {
        assertEquals(
            "Not on Home screen yet",
            HaruBubbleStatus(enabled = true).message,
        )
        assertTrue(
            HaruBubbleStatus(requestPending = true)
                .message
                .startsWith("Waiting")
        )
        assertEquals(
            "On Home screen · check-ins active",
            HaruBubbleStatus(installedCount = 1).message,
        )
        assertEquals(
            "On Home screen · check-ins paused",
            HaruBubbleStatus(
                installedCount = 1,
                enabled = false,
            ).message,
        )
    }

    @Test
    fun checkerStopsWhenPausedQuietBusyOrTyping() {
        assertTrue(HaruIdlePolicy.canAdvance(true, false, false, ""))
        assertFalse(HaruIdlePolicy.canAdvance(false, false, false, ""))
        assertFalse(HaruIdlePolicy.canAdvance(true, true, false, ""))
        assertFalse(HaruIdlePolicy.canAdvance(true, false, true, ""))
        assertFalse(
            HaruIdlePolicy.canAdvance(
                true,
                false,
                false,
                "a user draft",
            )
        )
    }

    @Test
    fun checkInCadenceIsGentleAndBounded() {
        val intervals =
            (0L..10L)
                .map(HaruIdlePolicy::nextIntervalMillis)

        assertTrue(intervals.all { it in 90_000L..210_000L })
        assertTrue(intervals.distinct().size >= 3)
        assertTrue(HaruIdlePolicy.ACKNOWLEDGEMENT_MS <= 2_000L)
    }
}
