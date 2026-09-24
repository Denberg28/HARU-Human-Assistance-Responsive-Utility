package io.haru.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun HaruTheme(
    themeColor: HaruThemeColor = HaruThemeColor.LAVENDER,
    backgroundTheme: HaruBackgroundTheme = HaruBackgroundTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    val lightAccent =
        when (themeColor) {
            HaruThemeColor.LAVENDER -> Color(0xFF8774B2)
            HaruThemeColor.BLUE -> Color(0xFF4676A8)
            HaruThemeColor.GREEN -> Color(0xFF5B8065)
            HaruThemeColor.ROSE -> Color(0xFFA76B7D)
            HaruThemeColor.AMBER -> Color(0xFF9A6A2F)
        }

    val darkAccent =
        when (themeColor) {
            HaruThemeColor.LAVENDER -> Color(0xFFC9B7F2)
            HaruThemeColor.BLUE -> Color(0xFFA8C8F0)
            HaruThemeColor.GREEN -> Color(0xFFA8D5B2)
            HaruThemeColor.ROSE -> Color(0xFFE6B1BE)
            HaruThemeColor.AMBER -> Color(0xFFE0BE80)
        }

    val scheme =
        when (backgroundTheme) {
            HaruBackgroundTheme.LIGHT ->
                lightColorScheme(
                    primary = lightAccent,
                    secondary = lightAccent,
                    tertiary = lightAccent,
                    onPrimary = Color.White,
                    background = Color(0xFFF8F8FB),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF0EEF4),
                    onBackground = Color(0xFF1D1B20),
                    onSurface = Color(0xFF1D1B20),
                    onSurfaceVariant = Color(0xFF49454F),
                    outline = Color(0xFF79747E),
                )

            HaruBackgroundTheme.SEPIA ->
                lightColorScheme(
                    primary = lightAccent,
                    secondary = lightAccent,
                    tertiary = lightAccent,
                    onPrimary = Color.White,
                    background = Color(0xFFD9C5A3),
                    surface = Color(0xFFE4D0AD),
                    surfaceVariant = Color(0xFFCBB38B),
                    onBackground = Color(0xFF2B241C),
                    onSurface = Color(0xFF2B241C),
                    onSurfaceVariant = Color(0xFF57493A),
                    outline = Color(0xFF79654C),
                )

            HaruBackgroundTheme.DARK ->
                darkColorScheme(
                    primary = darkAccent,
                    secondary = darkAccent,
                    tertiary = darkAccent,
                    onPrimary = Color(0xFF211C29),
                    background = Color(0xFF111116),
                    surface = Color(0xFF18181F),
                    surfaceVariant = Color(0xFF24242D),
                    onBackground = Color(0xFFF1EDF4),
                    onSurface = Color(0xFFF1EDF4),
                    onSurfaceVariant = Color(0xFFCBC6D1),
                    outline = Color(0xFF918A98),
                )
        }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
