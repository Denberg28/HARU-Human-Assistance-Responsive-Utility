package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruCheckerContentTest {

    @Test
    fun greetingFollowsTimeOfDay() {
        val empty = CompanionSnapshot()

        assertEquals(
            "Good morning",
            HaruCheckerContentFactory.create(8, 0, empty, 0L).greeting,
        )
        assertEquals(
            "Good afternoon",
            HaruCheckerContentFactory.create(14, 0, empty, 0L).greeting,
        )
        assertEquals(
            "Good evening",
            HaruCheckerContentFactory.create(20, 0, empty, 0L).greeting,
        )
    }

    @Test
    fun checkerUsesCountsWithoutPrivateTaskText() {
        val now = 1_000_000L
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("Private task"),
                        CompanionTask("Done", done = true),
                    ),
                reminders =
                    listOf(
                        CompanionReminder(
                            id = "r1",
                            text = "Private reminder",
                            dueAt = now + 60_000L,
                        )
                    ),
            )

        val content =
            HaruCheckerContentFactory.create(
                hourOfDay = 9,
                step = 0,
                snapshot = snapshot,
                now = now,
            )

        assertEquals("1 task · 1 reminder waiting.", content.line)
        assertTrue(!content.line.contains("Private task"))
        assertTrue(!content.line.contains("Private reminder"))
    }

    @Test
    fun acknowledgementIsShortAndVaries() {
        val first = HaruCheckerContentFactory.acknowledgement(0)
        val second = HaruCheckerContentFactory.acknowledgement(1)

        assertTrue(first.face.isNotBlank())
        assertTrue(first.line.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun lockScreenTaskLineShowsTwoOpenTasksAndRemainingCount() {
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("First task"),
                        CompanionTask("Done task", done = true),
                        CompanionTask("Second task"),
                        CompanionTask("Third task"),
                    ),
            )

        assertEquals(
            "Tasks · First task · Second task · +1",
            HaruCheckerContentFactory.lockScreenTaskLine(snapshot),
        )
        assertEquals(
            "✓ Tasks clear",
            HaruCheckerContentFactory.lockScreenTaskLine(CompanionSnapshot()),
        )
    }

    @Test
    fun quietModeIsStable() {
        val snapshot = CompanionSnapshot()
        val first =
            HaruCheckerContentFactory.create(
                23,
                Long.MIN_VALUE,
                snapshot,
                0L,
                quiet = true,
            )
        val second =
            HaruCheckerContentFactory.create(
                23,
                Long.MAX_VALUE,
                snapshot,
                0L,
                quiet = true,
            )

        assertEquals(first, second)
    }
}
