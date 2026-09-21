package io.haru.assistant.companion

import android.content.Context

class HaruCheckerStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_checker",
            Context.MODE_PRIVATE,
        )

    private val legacy =
        context.getSharedPreferences(
            "haru_bubble",
            Context.MODE_PRIVATE,
        )

    fun isEnabled(): Boolean =
        if (preferences.contains(KEY_ENABLED)) {
            preferences.getBoolean(KEY_ENABLED, true)
        } else {
            legacy.getBoolean(KEY_ENABLED, true)
        }

    fun setEnabled(enabled: Boolean) {
        preferences
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
    }
}
