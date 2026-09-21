package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionSnapshotTest {
    @Test fun todayShowsOpenTasksAndOverdueReminder() {
        val snapshot = CompanionSnapshot(
            tasks = listOf(CompanionTask("Buy milk"), CompanionTask("Finished", done = true)),
            reminders = listOf(CompanionReminder("r1", "Call home", 1L)),
        )
        assertEquals(listOf("Tasks: Buy milk", "⏰ Call home · due now"), snapshot.todayLines(60_001L))
    }

    @Test fun todayKeepsSummaryBounded() {
        val snapshot = CompanionSnapshot(
            tasks = (1..8).map { CompanionTask("Task $it") },
            reminders = (1..8).map { CompanionReminder("$it", "Reminder $it", it * 60_000L) },
        )
        val lines = snapshot.todayLines(0L)
        assertEquals(3, lines.size)
        assertEquals("Tasks: Task 1 · Task 2", lines.first())
    }
}
