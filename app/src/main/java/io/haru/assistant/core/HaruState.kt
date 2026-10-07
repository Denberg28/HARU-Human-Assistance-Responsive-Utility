package io.haru.assistant.core

import io.haru.assistant.onlineai.WebSource

enum class HaruMood {
    IDLE,
    LISTENING,
    THINKING,
    WORKING,
    HAPPY,
    CONFUSED,
    ALERT,
    SLEEPY
}

data class HaruUiState(
    val mood: HaruMood = HaruMood.IDLE,
    val message: String = "Hello. I'm HARU.",
    val command: String = "",
    val latestUserMessage: String = "",
    val webSources: List<WebSource> = emptyList(),
    val searchSuggestionsHtml: String = "",
    val isBusy: Boolean = false
) {
    val canSubmit: Boolean
        get() = !isBusy && command.isNotBlank()
}
