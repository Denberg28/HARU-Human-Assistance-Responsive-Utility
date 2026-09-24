package io.haru.assistant.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class HaruDailyPulseTest {

    @Test
    fun morningPulseKeepsTaskTextPrivate() {
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("Private first task"),
                        CompanionTask("Second task"),
                    ),
            )

        val pulse = HaruDailyPulseFactory.create(8, snapshot)

        assertEquals(HaruDailyState.MORNING, pulse.state)
        assertEquals("Good morning", pulse.greeting)
        assertEquals("2 tasks today. Start with one small win.", pulse.line)
    }

    @Test
    fun completedListCelebratesLocally() {
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("Finished", done = true),
                    ),
            )

        val pulse = HaruDailyPulseFactory.create(18, snapshot)

        assertEquals(HaruDailyState.CELEBRATING, pulse.state)
        assertEquals("Tasks clear. Nice work today. ✨", pulse.line)
    }

    @Test
    fun focusLineUsesFirstOpenTaskAndRemainingCount() {
        val snapshot =
            CompanionSnapshot(
                tasks =
                    listOf(
                        CompanionTask("First"),
                        CompanionTask("Done", done = true),
                        CompanionTask("Second"),
                        CompanionTask("Third"),
                    ),
            )

        assertEquals(
            "Focus · First · +2",
            HaruDailyPulseFactory.focusLine(snapshot),
        )
        assertEquals(
            "✓ Tasks clear",
            HaruDailyPulseFactory.focusLine(CompanionSnapshot()),
        )
    }
}
