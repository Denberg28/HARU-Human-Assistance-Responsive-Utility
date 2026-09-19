package io.haru.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.haru.assistant.core.CommandRouter
import io.haru.assistant.core.HaruMood
import io.haru.assistant.core.HaruUiState

class HaruViewModel(
    private val router: CommandRouter = CommandRouter()
) : ViewModel() {

    var uiState by mutableStateOf(HaruUiState())
        private set

    fun updateCommand(value: String) {
        uiState = uiState.copy(command = value)
    }

    fun recordLatestUser(value: String) {
        val clean = value.trim()
        if (clean.isNotBlank()) {
            uiState = uiState.copy(latestUserMessage = clean)
        }
    }

    fun setListening() {
        uiState = uiState.copy(
            mood = HaruMood.LISTENING,
            message = "Listening…",
            isBusy = true
        )
    }

    fun cancelListening(message: String = "Ready when you are.") {
        uiState = uiState.copy(
            mood = HaruMood.IDLE,
            message = message,
            isBusy = false
        )
    }

    fun submit() {
        val command = uiState.command
        recordLatestUser(command)
        uiState = uiState.copy(mood = HaruMood.THINKING, isBusy = true)

        val result = router.route(command)
        uiState = uiState.copy(
            mood = if (result.success) HaruMood.HAPPY else HaruMood.CONFUSED,
            message = result.message,
            command = "",
            isBusy = false
        )
    }

    fun prepareAiPrompt(): String? {
        val command = uiState.command.trim()
        recordLatestUser(command)
        if (command.isBlank()) {
            submit()
            return null
        }

        val localResult = router.route(command)
        if (localResult.success) {
            uiState = uiState.copy(
                mood = HaruMood.HAPPY,
                message = localResult.message,
                command = "",
                isBusy = false,
            )
            return null
        }

        uiState = uiState.copy(
            mood = HaruMood.THINKING,
            message = "Thinking…",
            command = "",
            isBusy = true,
        )
        return command
    }

    fun completeAi(message: String, success: Boolean = true) {
        uiState = uiState.copy(
            mood = if (success) HaruMood.HAPPY else HaruMood.CONFUSED,
            message = message,
            isBusy = false,
        )
    }

}
