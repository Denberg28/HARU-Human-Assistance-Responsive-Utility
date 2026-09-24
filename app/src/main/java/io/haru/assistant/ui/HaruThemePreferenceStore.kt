package io.haru.assistant.ui

import android.content.Context

enum class HaruThemeColor(
    val label: String,
) {
    LAVENDER("Lavender"),
    BLUE("Blue"),
    GREEN("Green"),
    ROSE("Rose"),
    AMBER("Amber"),
}

class HaruThemePreferenceStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_theme_preferences",
            Context.MODE_PRIVATE,
        )

    fun load(): HaruThemeColor =
        runCatching {
            HaruThemeColor.valueOf(
                preferences.getString(
                    KEY_THEME_COLOR,
                    HaruThemeColor.LAVENDER.name,
                ) ?: HaruThemeColor.LAVENDER.name
            )
        }.getOrDefault(HaruThemeColor.LAVENDER)

    fun save(color: HaruThemeColor) {
        preferences.edit()
            .putString(KEY_THEME_COLOR, color.name)
            .apply()
    }

    private companion object {
        const val KEY_THEME_COLOR = "theme_color"
    }
}
