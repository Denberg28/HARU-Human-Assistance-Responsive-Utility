package io.haru.assistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.haru.assistant.core.HaruMood

/**
 * Static chibi rendering intentionally avoids an always-running animation loop.
 * Short-lived thinking animation is handled separately only while work is active.
 */
@Composable
fun HaruFace(
    mood: HaruMood,
    modifier: Modifier = Modifier,
) {
    val face = when (mood) {
        HaruMood.IDLE -> "₍^. .^₎⟆"
        HaruMood.HAPPY -> "₍^ >ヮ<^₎♡"
        HaruMood.LISTENING -> "₍^. ̫ .^₎♫"
        HaruMood.THINKING -> "₍^. .^₎?"
        HaruMood.WORKING -> "₍^•⩊•^₎⚙"
        HaruMood.CONFUSED -> "₍^. .^₎՞"
        HaruMood.ALERT -> "₍⊙ᆺ⊙₎!"
        HaruMood.SLEEPY -> "₍^-.-^₎ zZ"
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = face,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 42.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
