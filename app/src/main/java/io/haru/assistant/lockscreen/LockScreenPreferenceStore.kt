package io.haru.assistant.lockscreen

import android.content.Context

class LockScreenPreferenceStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_lock_screen",
            Context.MODE_PRIVATE,
        )

    fun isEnabled(): Boolean =
        preferences.getBoolean(KEY_ENABLED, true)

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
