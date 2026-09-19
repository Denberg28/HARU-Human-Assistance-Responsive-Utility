package io.haru.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import io.haru.assistant.core.HaruMood

@Composable
fun HaruFace(
    mood: HaruMood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "haru-chibi")
    val pulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haru-pulse",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulse),
        ) {
            val w = size.width
            val h = size.height
            val c = Offset(w / 2f, h / 2f + h * 0.03f)
            val r = minOf(w, h) * 0.33f

            val fur = Color(0xFFF6C9A8)
            val furDark = Color(0xFFE9A97C)
            val ink = Color(0xFF3B2F2A)
            val blush = Color(0xFFF3A7A7)
            val cream = Color(0xFFFFF4EC)

            // ears
            val leftEar = Path().apply {
                moveTo(c.x - r * 0.72f, c.y - r * 0.62f)
                lineTo(c.x - r * 0.95f, c.y - r * 1.18f)
                lineTo(c.x - r * 0.30f, c.y - r * 0.86f)
                close()
            }
            val rightEar = Path().apply {
                moveTo(c.x + r * 0.72f, c.y - r * 0.62f)
                lineTo(c.x + r * 0.95f, c.y - r * 1.18f)
                lineTo(c.x + r * 0.30f, c.y - r * 0.86f)
                close()
            }
            drawPath(leftEar, fur)
            drawPath(rightEar, fur)
            drawPath(leftEar, furDark, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
            drawPath(rightEar, furDark, style = Stroke(r * 0.055f, cap = StrokeCap.Round))

            // big chibi head
            drawOval(
                color = fur,
                topLeft = Offset(c.x - r, c.y - r * 0.85f),
                size = Size(r * 2f, r * 1.8f),
            )

            // muzzle
            drawOval(
                color = cream,
                topLeft = Offset(c.x - r * 0.46f, c.y + r * 0.02f),
                size = Size(r * 0.92f, r * 0.58f),
            )

            val eyeY = c.y - r * 0.20f
            val eyeDx = r * 0.38f

            fun dotEye(x: Float, radius: Float = r * 0.10f) {
                drawCircle(ink, radius, Offset(x, eyeY))
                drawCircle(Color.White, radius * 0.28f, Offset(x - radius * 0.25f, eyeY - radius * 0.25f))
            }

            when (mood) {
                HaruMood.HAPPY -> {
                    drawArc(
                        ink, 200f, 140f, false,
                        Offset(c.x - eyeDx - r * 0.12f, eyeY - r * 0.04f),
                        Size(r * 0.24f, r * 0.18f),
                        style = Stroke(r * 0.055f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        ink, 200f, 140f, false,
                        Offset(c.x + eyeDx - r * 0.12f, eyeY - r * 0.04f),
                        Size(r * 0.24f, r * 0.18f),
                        style = Stroke(r * 0.055f, cap = StrokeCap.Round),
                    )
                }
                HaruMood.SLEEPY -> {
                    drawLine(
                        ink,
                        Offset(c.x - eyeDx - r * 0.10f, eyeY),
                        Offset(c.x - eyeDx + r * 0.10f, eyeY),
                        r * 0.045f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        ink,
                        Offset(c.x + eyeDx - r * 0.10f, eyeY),
                        Offset(c.x + eyeDx + r * 0.10f, eyeY),
                        r * 0.045f,
                        StrokeCap.Round,
                    )
                }
                HaruMood.CONFUSED -> {
                    dotEye(c.x - eyeDx, r * 0.085f)
                    dotEye(c.x + eyeDx, r * 0.12f)
                }
                HaruMood.ALERT -> {
                    dotEye(c.x - eyeDx, r * 0.13f)
                    dotEye(c.x + eyeDx, r * 0.13f)
                }
                else -> {
                    dotEye(c.x - eyeDx)
                    dotEye(c.x + eyeDx)
                }
            }

            // cheeks
            drawOval(
                blush.copy(alpha = 0.65f),
                Offset(c.x - r * 0.78f, c.y + r * 0.10f),
                Size(r * 0.30f, r * 0.16f),
            )
            drawOval(
                blush.copy(alpha = 0.65f),
                Offset(c.x + r * 0.48f, c.y + r * 0.10f),
                Size(r * 0.30f, r * 0.16f),
            )

            // nose
            val nose = Path().apply {
                moveTo(c.x, c.y + r * 0.12f)
                lineTo(c.x - r * 0.09f, c.y + r * 0.18f)
                lineTo(c.x + r * 0.09f, c.y + r * 0.18f)
                close()
            }
            drawPath(nose, blush)

            val mouthY = c.y + r * 0.30f
            when (mood) {
                HaruMood.HAPPY -> drawArc(
                    color = ink,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.25f, mouthY - r * 0.08f),
                    size = Size(r * 0.50f, r * 0.28f),
                    style = Stroke(r * 0.05f, cap = StrokeCap.Round),
                )
                HaruMood.CONFUSED -> drawArc(
                    color = ink,
                    startAngle = 190f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.20f, mouthY),
                    size = Size(r * 0.40f, r * 0.16f),
                    style = Stroke(r * 0.05f, cap = StrokeCap.Round),
                )
                HaruMood.ALERT -> drawCircle(
                    color = ink,
                    radius = r * 0.10f,
                    center = Offset(c.x, mouthY + r * 0.02f),
                )
                else -> {
                    drawLine(
                        ink,
                        Offset(c.x, c.y + r * 0.18f),
                        Offset(c.x, mouthY),
                        r * 0.04f,
                        StrokeCap.Round,
                    )
                    drawArc(
                        color = ink,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(c.x - r * 0.18f, mouthY - r * 0.05f),
                        size = Size(r * 0.36f, r * 0.18f),
                        style = Stroke(r * 0.045f, cap = StrokeCap.Round),
                    )
                }
            }

            if (mood == HaruMood.THINKING || mood == HaruMood.WORKING) {
                drawCircle(
                    color = cream,
                    radius = r * 0.13f,
                    center = Offset(c.x + r * 0.76f, c.y - r * 0.78f),
                )
                drawCircle(
                    color = cream,
                    radius = r * 0.07f,
                    center = Offset(c.x + r * 0.58f, c.y - r * 0.57f),
                )
            }
        }
    }
}
