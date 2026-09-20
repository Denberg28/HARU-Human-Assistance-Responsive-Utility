package io.haru.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionLockStatusTest {

    @Test
    fun lockScreenStatusAvoidsPrivateUserData() {
        CompanionMode.entries.forEach { mode ->
            val status = mode.lockScreenStatus()

            assertEquals(
                "HARU • " + mode.label,
                status.title,
            )
            assertFalse(
                status.message.contains("latitude", ignoreCase = true)
            )
            assertFalse(
                status.message.contains("longitude", ignoreCase = true)
            )
            assertFalse(
                status.message.contains("task:", ignoreCase = true)
            )
            assertFalse(
                status.message.contains("reminder:", ignoreCase = true)
            )
        }
    }

    @Test
    fun restModeAdvertisesQuietStandby() {
        assertEquals(
            "Rest companion • quiet standby",
            CompanionMode.REST.lockScreenStatus().message,
        )
    }
}
