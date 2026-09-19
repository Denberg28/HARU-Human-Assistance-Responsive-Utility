package io.haru.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.haru.assistant.core.HaruMood

/**
 * Android HARU mascot intentionally mirrors the Streamlit mood pack.
 * Keep this mapping synchronized with streamlit_app.py::emoji_for_mood.
 */
@Composable
fun HaruFace(
    mood: HaruMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "haru-chibi")
    val pulse by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haru-pulse",
    )

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
            modifier = Modifier.scale(pulse),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 42.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
