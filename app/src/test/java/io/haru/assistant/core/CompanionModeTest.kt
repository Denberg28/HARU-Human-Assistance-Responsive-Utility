package io.haru.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionModeTest {

    @Test
    fun parsesExplicitModeCommands() {
        assertEquals(
            CompanionMode.FLIGHT,
            CompanionMode.fromCommand("flight mode"),
        )
        assertEquals(
            CompanionMode.TRAVEL,
            CompanionMode.fromCommand("switch to travel mode"),
        )
        assertEquals(
            CompanionMode.WORK,
            CompanionMode.fromCommand("start work"),
        )
        assertEquals(
            CompanionMode.SAFETY,
            CompanionMode.fromCommand("activate HARU safety mode"),
        )
    }

    @Test
    fun doesNotHijackOrdinaryQuestions() {
        assertNull(
            CompanionMode.fromCommand("what is a flight controller?")
        )
        assertNull(
            CompanionMode.fromCommand("help me plan a trip")
        )
    }

    @Test
    fun invalidStoredModeFallsBackToNormal() {
        assertEquals(
            CompanionMode.NORMAL,
            CompanionMode.fromStored("OLD_UNKNOWN_MODE"),
        )
    }
}
