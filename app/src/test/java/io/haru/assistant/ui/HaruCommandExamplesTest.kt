package io.haru.assistant.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruCommandExamplesTest {

    @Test
    fun showsExamplesOnlyBeforeFirstInteraction() {
        assertTrue(
            shouldShowCommandExamples(
                command = "",
                latestUserMessage = "",
            )
        )
    }

    @Test
    fun hidesExamplesWhileUserIsTypingOrUsingCompanionDraft() {
        assertFalse(
            shouldShowCommandExamples(
                command = "How is the weather?",
                latestUserMessage = "",
            )
        )
    }

    @Test
    fun keepsExamplesHiddenAfterMessageIsSentAndInputClears() {
        assertFalse(
            shouldShowCommandExamples(
                command = "",
                latestUserMessage = "How is the weather?",
            )
        )
    }

    @Test
    fun blankWhitespaceDoesNotCountAsConversation() {
        assertTrue(
            shouldShowCommandExamples(
                command = "   ",
                latestUserMessage = "   ",
            )
        )
    }
}
