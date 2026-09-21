package io.haru.assistant

import io.haru.assistant.core.CommandSource
import io.haru.assistant.core.HaruMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruViewModelInputStateTest {

    @Test
    fun companionDraftIsTrackedAndCanBeClearedWithoutTouchingUserText() {
        val viewModel = HaruViewModel()

        viewModel.setCompanionDraft("Help me reflect on today.")
        assertEquals(CommandSource.COMPANION, viewModel.uiState.commandSource)
        assertEquals("Help me reflect on today.", viewModel.uiState.command)

        viewModel.clearCompanionDraft()
        assertEquals("", viewModel.uiState.command)
        assertEquals(CommandSource.NONE, viewModel.uiState.commandSource)

        viewModel.updateCommand("My own draft")
        viewModel.clearCompanionDraft()
        assertEquals("My own draft", viewModel.uiState.command)
        assertEquals(CommandSource.USER, viewModel.uiState.commandSource)
    }

    @Test
    fun editingCompanionDraftTransfersOwnershipToUser() {
        val viewModel = HaruViewModel()

        viewModel.setCompanionDraft("Starter")
        viewModel.updateCommand("Starter edited")

        assertEquals(CommandSource.USER, viewModel.uiState.commandSource)
        viewModel.clearCompanionDraft()
        assertEquals("Starter edited", viewModel.uiState.command)
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
    fun resetRemovesAllDraftOwnership() {
        val viewModel = HaruViewModel()
        viewModel.setCompanionDraft("Starter")

        viewModel.resetConversation()

        assertEquals("", viewModel.uiState.command)
        assertEquals(CommandSource.NONE, viewModel.uiState.commandSource)
        assertEquals("", viewModel.uiState.latestUserMessage)
    }
}
