package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruBubbleContentTest {

    @Test
    fun greetingFollowsTimeOfDay() {
        val empty = CompanionSnapshot()

        assertEquals(
            "Good morning",
            HaruBubbleContentFactory.create(
                hourOfDay = 8,
                step = 0,
                snapshot = empty,
                now = 0L,
            ).greeting,
        )
        assertEquals(
            "Good afternoon",
            HaruBubbleContentFactory.create(
                hourOfDay = 14,
                step = 0,
                snapshot = empty,
                now = 0L,
            ).greeting,
        )
        assertEquals(
            "Good evening",
            HaruBubbleContentFactory.create(
                hourOfDay = 20,
                step = 0,
                snapshot = empty,
                now = 0L,
            ).greeting,
        )
    }

    @Test
    fun bubblePrioritizesUsefulLocalContext() {
        val now = 1_000_000L
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("Buy milk"),
                        CompanionTask("Done", done = true),
                    ),
                reminders =
                    listOf(
                        CompanionReminder(
                            id = "r1",
                            text = "Call home",
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

        assertEquals(
            "1 task · 1 reminder",
            content.line,
        )
    }

    @Test
    fun idleStepChangesFaceOrLineWithoutAi() {
        val empty = CompanionSnapshot()
        val first =
            HaruBubbleContentFactory.create(
                hourOfDay = 10,
                step = 0,
                snapshot = empty,
                now = 0L,
            )
        val second =
            HaruBubbleContentFactory.create(
                hourOfDay = 10,
                step = 1,
                snapshot = empty,
                now = 0L,
            )

        assertTrue(
            first.face != second.face ||
                first.line != second.line
        )
    }

    @Test
    fun pendingTasksDoNotSuppressIdleConversation() {
        val snapshot = CompanionSnapshot(tasks = listOf(CompanionTask("Private task")))
        val lines = (0L..5L).map { HaruBubbleContentFactory.create(10, it, snapshot, 0L) }
        assertTrue(lines.map { it.line }.distinct().size >= 4)
        assertTrue(lines.any { it.conversationPrompt != "today" })
        assertTrue(lines.all { !it.line.contains("Private task") })
    }

    @Test
    fun overdueRemindersStayVisible() {
        val snapshot = CompanionSnapshot(reminders = listOf(CompanionReminder("a", "Private reminder", 10L)))
        val content = HaruBubbleContentFactory.create(10, 0L, snapshot, 20L)
        assertEquals("1 reminder due. Open Today.", content.line)
        assertEquals("today", content.conversationPrompt)
    }

    @Test
    fun quietModeHasStableContentAndNegativeStepsAreSafe() {
        val snapshot = CompanionSnapshot()
        val first = HaruBubbleContentFactory.create(23, Long.MIN_VALUE, snapshot, 0L, quiet = true)
        val second = HaruBubbleContentFactory.create(23, Long.MAX_VALUE, snapshot, 0L, quiet = true)
        assertEquals(first, second)
        assertTrue(HaruBubbleContentFactory.create(5, Long.MIN_VALUE, snapshot, 0L).line.isNotBlank())
    }
}
