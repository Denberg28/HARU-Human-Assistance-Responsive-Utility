package io.haru.assistant.skills

import io.haru.assistant.core.CommandRouter
import org.junit.Assert.*
import org.junit.Test

class LocalQueryCoverageTest {
    @Test fun deviceClockDoesNotInterceptSchedulesOrTimeConcepts() {
        val time = TimeSkill()
        assertTrue(time.canHandle("What time is it?"))
        assertTrue(time.canHandle("today's date"))
        listOf("What time is flight 5J 325 today?", "Explain current time synchronization", "What date did the first moon landing happen?").forEach {
            assertFalse(it, time.canHandle(it))
        }
    }
    @Test fun exactArithmeticDoesNotOverflowOrNeedAnApiForZeroDivision() {
        val router = CommandRouter()
        assertEquals("0.1 + 0.2 = 0.3", router.route("0.1 + 0.2").message)
        assertEquals("100000000000000000000 + 1 = 100000000000000000001", router.route("100000000000000000000 + 1").message)
        assertTrue(router.route("1 / 0").success)
        assertTrue(router.route("1 / 3").message.contains("≈"))
    }
}
