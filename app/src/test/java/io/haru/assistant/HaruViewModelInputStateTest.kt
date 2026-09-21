package io.haru.assistant

import io.haru.assistant.core.HaruMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruViewModelInputStateTest {

    @Test
    fun inputContainsOnlyUserOrVoiceText() {
        val viewModel = HaruViewModel()

        assertEquals("", viewModel.uiState.command)
        viewModel.updateCommand("hello")

        assertEquals("hello", viewModel.uiState.command)
        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun inputIsSanitizedBoundedAndSubmitGuardIsAuthoritative() {
        val viewModel = HaruViewModel()
        val oversized = "\u0000" + "a".repeat(HaruViewModel.MAX_COMMAND_CHARS + 50) + "\nnext"

        viewModel.updateCommand(oversized)

        assertEquals(HaruViewModel.MAX_COMMAND_CHARS, viewModel.uiState.command.length)
        assertFalse(viewModel.uiState.command.contains('\u0000'))
        assertFalse(viewModel.uiState.command.contains('\n'))
        assertTrue(viewModel.uiState.canSubmit)

        viewModel.setListening()
        assertFalse(viewModel.uiState.canSubmit)
        assertEquals(HaruMood.LISTENING, viewModel.uiState.mood)
    }

    @Test
    fun blankAndBusyStatesCannotSubmit() {
        val viewModel = HaruViewModel()
        assertFalse(viewModel.uiState.canSubmit)

        viewModel.updateCommand("hello")
        assertTrue(viewModel.uiState.canSubmit)

        viewModel.setListening()
        assertFalse(viewModel.uiState.canSubmit)
        viewModel.submit()
        assertEquals(HaruMood.LISTENING, viewModel.uiState.mood)
    }

    @Test
    fun submittedInputClearsAndIsRememberedAsLatestUserMessage() {
        val viewModel = HaruViewModel()
        viewModel.updateCommand("hello")

        viewModel.submit()

        assertEquals("", viewModel.uiState.command)
        assertEquals("hello", viewModel.uiState.latestUserMessage)
    }

    @Test
    fun resetClearsInputAndConversationMarker() {
        val viewModel = HaruViewModel()
        viewModel.updateCommand("hello")
        viewModel.recordLatestUser("hello")

        viewModel.resetConversation()

        assertEquals("", viewModel.uiState.command)
        assertEquals("", viewModel.uiState.latestUserMessage)
    }
}
