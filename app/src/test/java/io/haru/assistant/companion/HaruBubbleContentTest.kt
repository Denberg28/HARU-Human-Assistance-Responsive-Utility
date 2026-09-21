package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruBubbleContentTest {

    @Test
    fun greetingFollowsTimeOfDay() {
        val empty = CompanionSnapshot()

        assertEquals(
            "Good morning",
            HaruBubbleContentFactory.create(8, 0, empty, 0L).greeting,
        )
        assertEquals(
            "Good afternoon",
            HaruBubbleContentFactory.create(14, 0, empty, 0L).greeting,
        )
        assertEquals(
            "Good evening",
            HaruBubbleContentFactory.create(20, 0, empty, 0L).greeting,
        )
    }

    @Test
    fun checkerUsesUsefulLocalContextWithoutExposingTaskText() {
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
            HaruBubbleContentFactory.create(
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
    fun checkInsVaryLocallyWithoutAi() {
        val empty = CompanionSnapshot()
        val lines =
            (0L..9L)
                .map {
                    HaruBubbleContentFactory.create(
                        hourOfDay = 10,
                        step = it,
                        snapshot = empty,
                        now = 0L,
                    ).line
                }

        assertTrue(lines.distinct().size >= 6)
    }

    @Test
    fun acknowledgementIsShortDelightedAndVaries() {
        val first = HaruBubbleContentFactory.acknowledgement(0)
        val second = HaruBubbleContentFactory.acknowledgement(1)

        assertTrue(first.line.isNotBlank())
        assertTrue(first.face.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun overdueReminderGetsGentleNudgeWithoutPrivateText() {
        val snapshot =
            CompanionSnapshot(
                reminders =
                    listOf(
                        CompanionReminder(
                            "a",
                            "Private reminder",
                            10L,
                        )
                    )
            )

        val content =
            HaruBubbleContentFactory.create(
                10,
                0L,
                snapshot,
                20L,
            )

        assertEquals(
            "1 reminder due. Just a gentle nudge.",
            content.line,
        )
        assertTrue(!content.line.contains("Private reminder"))
    }

    @Test
    fun quietModeIsStableAndNegativeStepsAreSafe() {
        val snapshot = CompanionSnapshot()
        val first =
            HaruBubbleContentFactory.create(
                23,
                Long.MIN_VALUE,
                snapshot,
                0L,
                quiet = true,
            )
        val second =
            HaruBubbleContentFactory.create(
                23,
                Long.MAX_VALUE,
                snapshot,
                0L,
                quiet = true,
            )

        assertEquals(first, second)
        assertTrue(
            HaruBubbleContentFactory
                .create(5, Long.MIN_VALUE, snapshot, 0L)
                .line
                .isNotBlank()
        )
    }
}
