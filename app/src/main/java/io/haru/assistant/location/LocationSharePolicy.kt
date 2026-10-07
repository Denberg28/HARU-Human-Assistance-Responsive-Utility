package io.haru.assistant.location

/** Validation shared by GPS acquisition and imported location payloads. */
internal object LocationSharePolicy {
    fun isRecentFix(fixElapsedNanos: Long, nowElapsedNanos: Long, maxAgeMs: Long): Boolean =
        fixElapsedNanos > 0L && nowElapsedNanos >= fixElapsedNanos &&
            (nowElapsedNanos - fixElapsedNanos) / 1_000_000L <= maxAgeMs

    fun validAccuracy(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 0.0..100_000.0 }

    fun isValidLifetime(issued: Long, expires: Long, now: Long): Boolean =
        issued > 0L && issued <= now + 60_000L && expires > issued && expires > now &&
            expires - issued <= 24L * 60L * 60L * 1000L + 60_000L

    fun isValidLiveSnapshot(captured: Long, expires: Long, seq: Long, now: Long): Boolean =
        seq > 0L && captured > 0L && captured <= now + 60_000L &&
            expires > captured && expires > now
}
