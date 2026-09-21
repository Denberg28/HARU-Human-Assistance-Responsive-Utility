package io.haru.assistant.companion

import org.junit.Assert.*
import org.junit.Test

class HaruBubblePolicyTest {
    @Test fun enablingOrRequestingDoesNotClaimPlacement() {
        assertEquals("Not on Home screen yet", HaruBubbleStatus(enabled = true).message)
        assertTrue(HaruBubbleStatus(requestPending = true).message.startsWith("Waiting"))
        assertEquals("On Home screen · active", HaruBubbleStatus(installedCount = 1).message)
        assertEquals("On Home screen · paused", HaruBubbleStatus(installedCount = 1, enabled = false).message)
    }

    @Test fun idleStopsWhenPausedQuietBusyOrTyping() {
        assertTrue(HaruIdlePolicy.canAdvance(true, false, false, ""))
        assertFalse(HaruIdlePolicy.canAdvance(false, false, false, ""))
        assertFalse(HaruIdlePolicy.canAdvance(true, true, false, ""))
        assertFalse(HaruIdlePolicy.canAdvance(true, false, true, ""))
        assertFalse(HaruIdlePolicy.canAdvance(true, false, false, "a draft"))
    }
}
