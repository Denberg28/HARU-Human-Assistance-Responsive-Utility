package io.haru.assistant.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruCheckerPolicyTest {

    @Test
    fun checkerStopsWhenQuietBusyOrTyping() {
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
    fun cadenceIsGentleAndAcknowledgementIsBrief() {
        val intervals =
            (0L..10L)
                .map(HaruIdlePolicy::nextIntervalMillis)

        assertTrue(intervals.all { it in 90_000L..210_000L })
        assertTrue(intervals.distinct().size >= 3)
        assertTrue(HaruIdlePolicy.ACKNOWLEDGEMENT_MS <= 2_000L)
    }
}
