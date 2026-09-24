package io.haru.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun HaruTheme(
    themeColor: HaruThemeColor = HaruThemeColor.LAVENDER,
    content: @Composable () -> Unit,
) {
    val seed =
        when (themeColor) {
            HaruThemeColor.LAVENDER -> Color(0xFF7059A8)
            HaruThemeColor.BLUE -> Color(0xFF376AA6)
            HaruThemeColor.GREEN -> Color(0xFF4F7A5D)
            HaruThemeColor.ROSE -> Color(0xFFA85E72)
            HaruThemeColor.AMBER -> Color(0xFF9A6A2F)
        }

    val scheme =
        lightColorScheme(
            primary = seed,
            secondary = seed,
            tertiary = seed,
        )

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
