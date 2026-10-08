package io.haru.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.haru.assistant.core.CommandRouter
import io.haru.assistant.core.HaruMood
import io.haru.assistant.core.HaruUiState
import io.haru.assistant.onlineai.WebSource

class HaruViewModel(
    private val router: CommandRouter = CommandRouter()
) : ViewModel() {

    var uiState by mutableStateOf(HaruUiState())
        private set

    fun updateCommand(value: String) {
        val clean = sanitizeCommand(value)
        uiState = uiState.copy(command = clean.take(MAX_COMMAND_CHARS + 1), commandTooLong = clean.length > MAX_COMMAND_CHARS)
    }

    fun recordLatestUser(value: String) {
        val clean = sanitizeCommand(value).trim()
        if (clean.isNotBlank()) {
            uiState = uiState.copy(latestUserMessage = clean)
        }
    }

    fun setListening() {
        if (uiState.isBusy) return
        uiState = uiState.copy(
            mood = HaruMood.LISTENING,
            message = "Listening…",
            webSources = emptyList(),
            searchSuggestionsHtml = "",
            isBusy = true
        )
    }

    fun cancelListening(message: String = "Ready when you are.") {
        uiState = uiState.copy(
            mood = HaruMood.IDLE,
            message = message,
            webSources = emptyList(),
            searchSuggestionsHtml = "",
        )
    }

    fun submit() {
        val command = uiState.command.trim()
        if (!uiState.canSubmit) return
        recordLatestUser(command)
        uiState = uiState.copy(mood = HaruMood.THINKING, isBusy = true)

        val result = router.route(command)
        uiState = uiState.copy(
            mood = if (result.success) HaruMood.HAPPY else HaruMood.CONFUSED,
            message = result.message,
            webSources = emptyList(),
            searchSuggestionsHtml = "",
            command = "",
            isBusy = false
        )
    }

    fun prepareAiPrompt(): String? {
        val command = uiState.command.trim()
        if (!uiState.canSubmit) return null
        recordLatestUser(command)

        val localResult = router.route(command)
        if (localResult.success) {
            uiState = uiState.copy(
                mood = HaruMood.HAPPY,
                message = localResult.message,
                webSources = emptyList(),
                searchSuggestionsHtml = "",
                command = "",
                isBusy = false,
            )
            return null
        }

        uiState = uiState.copy(
            mood = HaruMood.THINKING,
            message = "Thinking…",
            webSources = emptyList(),
            searchSuggestionsHtml = "",
            command = "",
            isBusy = true,
        )
        return command
    }

    fun completeAi(message: String, success: Boolean = true, webSources: List<WebSource> = emptyList(), searchSuggestionsHtml: String = "") {
        uiState = uiState.copy(
            mood = if (success) HaruMood.HAPPY else HaruMood.CONFUSED,
            message = message,
            webSources = webSources,
            searchSuggestionsHtml = searchSuggestionsHtml,
            isBusy = false,
        )
    }

    fun resetConversation() {
        uiState = HaruUiState(
            message = "Conversation memory cleared. Ready for a fresh chat."
        )
    }

    private fun sanitizeCommand(value: String): String =
        value
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"""), "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .take(MAX_COMMAND_CHARS + 1)

    companion object {
        internal const val MAX_COMMAND_CHARS = 8000
    }
}
