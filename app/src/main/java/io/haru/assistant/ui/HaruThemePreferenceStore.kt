package io.haru.assistant.ui

import android.content.Context

enum class HaruThemeColor(
    val label: String,
) {
    LAVENDER("Lilac"),
    BLUE("Blue"),
    GREEN("Green"),
    ROSE("Rose"),
    AMBER("Amber"),
}

enum class HaruBackgroundTheme(
    val label: String,
) {
    LIGHT("Light"),
    SEPIA("Sepia"),
    DARK("Dark"),
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

    fun loadBackground(): HaruBackgroundTheme =
        runCatching {
            HaruBackgroundTheme.valueOf(
                preferences.getString(
                    KEY_BACKGROUND_THEME,
                    HaruBackgroundTheme.LIGHT.name,
                ) ?: HaruBackgroundTheme.LIGHT.name
            )
        }.getOrDefault(HaruBackgroundTheme.LIGHT)

    fun saveBackground(background: HaruBackgroundTheme) {
        preferences.edit()
            .putString(KEY_BACKGROUND_THEME, background.name)
            .apply()
    }

    private companion object {
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_BACKGROUND_THEME = "background_theme"
    }
}
