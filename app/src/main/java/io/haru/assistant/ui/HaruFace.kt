package io.haru.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.haru.assistant.core.HaruMood

@Composable
fun HaruFace(
    mood: HaruMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "haru-chibi")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val face = when (mood) {
        HaruMood.HAPPY -> "😸"
        HaruMood.LISTENING -> "😺"
        HaruMood.THINKING -> "😼"
        HaruMood.WORKING -> "😼"
        HaruMood.CONFUSED -> "😿"
        HaruMood.ALERT -> "🙀"
        HaruMood.SLEEPY -> "😽"
        HaruMood.IDLE -> "😺"
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = face,
            style = TextStyle(fontSize = 112.sp),
            modifier = Modifier.scale(pulse),
        )
    }
}
