package io.haru.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.haru.assistant.core.HaruMood

@Composable
fun HaruFace(
    mood: HaruMood,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "haru-face")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val accent = when (mood) {
        HaruMood.HAPPY -> Color(0xFF2E7D32)
        HaruMood.CONFUSED -> Color(0xFFF57C00)
        HaruMood.ALERT -> Color(0xFFC62828)
        HaruMood.THINKING, HaruMood.WORKING -> Color(0xFF1565C0)
        HaruMood.SLEEPY -> Color(0xFF6A1B9A)
        else -> Color(0xFF263238)
    }

    Box(modifier = modifier.size(240.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val radius = size.minDimension * 0.42f * pulse

            drawCircle(Color(0xFFF4F6F8), radius, c)
            drawCircle(
                color = accent,
                radius = radius,
                center = c,
                style = Stroke(width = size.minDimension * 0.025f)
            )

            val eyeY = c.y - radius * 0.18f
            val eyeDx = radius * 0.34f
            val eyeRadius = radius * 0.08f

            when (mood) {
                HaruMood.SLEEPY -> {
                    val w = radius * 0.18f
                    drawLine(accent, Offset(c.x - eyeDx - w, eyeY), Offset(c.x - eyeDx + w, eyeY), radius * 0.05f, cap = StrokeCap.Round)
                    drawLine(accent, Offset(c.x + eyeDx - w, eyeY), Offset(c.x + eyeDx + w, eyeY), radius * 0.05f, cap = StrokeCap.Round)
                }
                HaruMood.HAPPY -> {
                    val w = radius * 0.16f
                    drawArc(
                        color = accent,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(c.x - eyeDx - w, eyeY - w),
                        size = Size(w * 2f, w * 2f),
                        style = Stroke(radius * 0.05f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = accent,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(c.x + eyeDx - w, eyeY - w),
                        size = Size(w * 2f, w * 2f),
                        style = Stroke(radius * 0.05f, cap = StrokeCap.Round),
                    )
                }
                else -> {
                    drawCircle(accent, eyeRadius, Offset(c.x - eyeDx, eyeY))
                    drawCircle(accent, eyeRadius, Offset(c.x + eyeDx, eyeY))
                }
            }

            val mouthY = c.y + radius * 0.30f
            when (mood) {
                HaruMood.HAPPY -> drawArc(
                    color = accent,
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius * 0.25f, mouthY - radius * 0.10f),
                    size = Size(radius * 0.50f, radius * 0.28f),
                    style = Stroke(radius * 0.05f, cap = StrokeCap.Round),
                )
                HaruMood.CONFUSED -> drawArc(
                    color = accent,
                    startAngle = 190f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius * 0.22f, mouthY),
                    size = Size(radius * 0.44f, radius * 0.18f),
                    style = Stroke(radius * 0.05f, cap = StrokeCap.Round),
                )
                HaruMood.ALERT -> drawOval(
                    accent,
                    topLeft = Offset(c.x - radius * 0.08f, mouthY - radius * 0.06f),
                    size = Size(radius * 0.16f, radius * 0.20f),
                    style = Stroke(radius * 0.04f)
                )
                else -> drawLine(
                    accent,
                    Offset(c.x - radius * 0.15f, mouthY),
                    Offset(c.x + radius * 0.15f, mouthY),
                    radius * 0.05f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
