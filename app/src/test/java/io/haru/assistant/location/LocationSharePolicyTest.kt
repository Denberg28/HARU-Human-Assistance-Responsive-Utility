package io.haru.assistant.location

import org.junit.Assert.*
import org.junit.Test

class LocationSharePolicyTest {
    @Test fun staleFutureAndMissingFixesAreRejectedWithMonotonicTime() {
        val now = 300_000_000_000L
        assertTrue(LocationSharePolicy.isRecentFix(now - 5_000_000_000L, now, 10_000L))
        assertFalse(LocationSharePolicy.isRecentFix(now - 11_000_000_000L, now, 10_000L))
        assertFalse(LocationSharePolicy.isRecentFix(now + 1, now, 10_000L))
        assertFalse(LocationSharePolicy.isRecentFix(0, now, 10_000L))
    }

    @Test fun futureIssuedAndReversedLifetimesAreRejected() {
        val now = 1_800_000_000_000L
        assertTrue(LocationSharePolicy.isValidLifetime(now, now + 60_000L, now))
        assertFalse(LocationSharePolicy.isValidLifetime(now + 300_000L, now + 360_000L, now))
        assertFalse(LocationSharePolicy.isValidLifetime(now + 30_000L, now + 10_000L, now))
        assertFalse(LocationSharePolicy.isValidLifetime(now, now + 90_000_000L, now))
        assertFalse(LocationSharePolicy.isValidLifetime(now - 60_000L, now - 1L, now))
    }

    @Test fun invalidAccuracyAndSnapshotMetadataAreRejected() {
        assertNull(LocationSharePolicy.validAccuracy(Double.NaN))
        assertNull(LocationSharePolicy.validAccuracy(Double.POSITIVE_INFINITY))
        assertNull(LocationSharePolicy.validAccuracy(-1.0))
        assertEquals(5.0, LocationSharePolicy.validAccuracy(5.0)!!, 0.0)
        val now = 1_800_000_000_000L
        assertTrue(LocationSharePolicy.isValidLiveSnapshot(now, now + 60_000L, 1L, now))
        assertFalse(LocationSharePolicy.isValidLiveSnapshot(now + 300_000L, now + 360_000L, 1L, now))
        assertFalse(LocationSharePolicy.isValidLiveSnapshot(now, now + 60_000L, 0L, now))
    }
}
