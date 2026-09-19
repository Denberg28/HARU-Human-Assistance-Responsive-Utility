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

    fun submit() {
        val command = uiState.command
        uiState = uiState.copy(mood = HaruMood.THINKING, isBusy = true)

        val result = router.route(command)
        uiState = uiState.copy(
            mood = if (result.success) HaruMood.HAPPY else HaruMood.CONFUSED,
            message = result.message,
            command = "",
            isBusy = false
        )
    }
}
