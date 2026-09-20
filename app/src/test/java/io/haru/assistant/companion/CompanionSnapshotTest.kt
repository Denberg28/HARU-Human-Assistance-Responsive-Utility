package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionSnapshotTest {

    @Test
    fun lockScreenLinesPreferOpenTasksThenReminders() {
        val now = 1_000_000L
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("Buy milk"),
                        CompanionTask("Finished", done = true),
                        CompanionTask("Charge drone"),
                    ),
                reminders =
                    listOf(
                        CompanionReminder(
                            id = "r1",
                            text = "Call home",
                            dueAt = now + 30 * 60_000L,
                        )
                    ),
            )

        assertEquals(
            listOf(
                "□ Buy milk",
                "□ Charge drone",
                "⏰ Call home · in 30 min",
            ),
            snapshot.lockScreenLines(now),
        )
    }

    @Test
    fun lockScreenLinesAreBounded() {
        val snapshot =
            CompanionSnapshot(
                tasks =
                    (1..8).map {
                        CompanionTask("Task $it")
                    }
            )

        assertEquals(
            3,
            snapshot.lockScreenLines().size,
        )
    }
}
