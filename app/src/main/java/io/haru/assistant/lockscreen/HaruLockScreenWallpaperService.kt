package io.haru.assistant.lockscreen

import android.app.WallpaperManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.HaruCheckerContentFactory
import io.haru.assistant.companion.HaruCheckerStore
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.CompanionModeStore
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class HaruLockScreenWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HaruEngine()

    private inner class HaruEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val catPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val greetingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bubbleBounds = RectF()
        private val catBounds = RectF()
        private val earPath = Path()

        private val checkerStore by lazy { HaruCheckerStore(applicationContext) }
        private val companionStore by lazy { AndroidCompanionStore(applicationContext) }
        private val modeStore by lazy { CompanionModeStore(applicationContext) }

        private var widthPx = 0
        private var heightPx = 0
        private var visible = false
        private var delightedUntil = 0L
        private var lastInteractionAt = 0L
        private val tapBounds = RectF()
        private var nextFrameDelayMs = HaruLockScreenMotionPolicy.IDLE_FRAME_MS

        private val frame =
            object : Runnable {
                override fun run() {
                    drawFrame()
                    if (visible) {
                        handler.postDelayed(this, nextFrameDelayMs)
                    }
                }
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(false)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            if (visible) {
                handler.post(frame)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            widthPx = width
            heightPx = height
            if (visible) {
                handler.removeCallbacks(frame)
                handler.post(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (
                checkerStore.isEnabled() &&
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                tapBounds.contains(event.x, event.y)
            ) {
                reactToPet()
            }
            super.onTouchEvent(event)
        }

        override fun onCommand(
            action: String,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean,
        ): Bundle? {
            if (
                checkerStore.isEnabled() &&
                action == WallpaperManager.COMMAND_TAP &&
                tapBounds.contains(x.toFloat(), y.toFloat())
            ) {
                reactToPet()
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun reactToPet() {
            val now = SystemClock.elapsedRealtime()
            if (now - lastInteractionAt < TAP_DEBOUNCE_MS) return
            lastInteractionAt = now
            delightedUntil =
                now +
                    HaruLockScreenMotionPolicy.INTERACTION_MS
            handler.removeCallbacks(frame)
            if (visible) {
                handler.post(frame)
            }
        }

        private fun drawFrame() {
            if (!visible || widthPx <= 0 || heightPx <= 0) return

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return

                val nowWall = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtime()
                val hour =
                    Calendar
                        .getInstance()
                        .get(Calendar.HOUR_OF_DAY)
                val quiet = modeStore.load() == CompanionMode.REST
                val motion =
                    HaruLockScreenMotionPolicy.motion(
                        hourOfDay = hour,
                        nowElapsed = nowElapsed,
                        quiet = quiet,
                        delightedUntil = delightedUntil,
                    )

                nextFrameDelayMs =
                    HaruLockScreenMotionPolicy.frameDelayMillis(motion)

                canvas.drawColor(
                    if (HaruLockScreenMotionPolicy.isNight(hour)) {
                        Color.rgb(24, 22, 31)
                    } else {
                        Color.rgb(250, 247, 252)
                    }
                )

                if (!checkerStore.isEnabled()) return

                val density = resources.displayMetrics.density
                val bubbleHeight = 74f * density
                val horizontalMargin = 18f * density
                val maxBubbleWidth = 318f * density
                val bubbleWidth =
                    min(
                        widthPx - horizontalMargin * 2f,
                        maxBubbleWidth,
                    )
                val baseTop =
                    heightPx * 0.69f -
                        bubbleHeight / 2f

                val travelRange =
                    (widthPx - bubbleWidth - horizontalMargin * 2f)
                        .coerceAtLeast(0f)
                val left =
                    when (motion) {
                        HaruLockMotion.RUNNING -> {
                            val phase =
                                (
                                    nowElapsed % RUN_CYCLE_MS
                                ).toFloat() / RUN_CYCLE_MS
                            horizontalMargin +
                                pingPong(phase) * travelRange
                        }

                        else -> (widthPx - bubbleWidth) / 2f
                    }

                val bounce =
                    when (motion) {
                        HaruLockMotion.RUNNING ->
                            abs(
                                sin(
                                    nowElapsed /
                                        115.0
                                )
                            ).toFloat() *
                                5f * density

                        HaruLockMotion.DELIGHTED ->
                            sin(
                                nowElapsed /
                                    90.0
                            ).toFloat() *
                                4f * density

                        else -> 0f
                    }

                bubbleBounds.set(
                    left,
                    baseTop - bounce,
                    left + bubbleWidth,
                    baseTop + bubbleHeight - bounce,
                )

                val step = nowWall / 90_000L
                val snapshot = companionStore.load()
                val content =
                    HaruCheckerContentFactory.create(
                        hourOfDay = hour,
                        step = step,
                        snapshot = snapshot,
                        now = nowWall,
                        quiet = quiet,
                    )
                val acknowledgement =
                    HaruCheckerContentFactory.acknowledgement(step)

                val greeting: String
                val line: String
                when (motion) {
                    HaruLockMotion.DELIGHTED -> {
                        greeting = "HARU"
                        line = acknowledgement.line
                    }

                    HaruLockMotion.SLEEPING -> {
                        greeting = "HARU is sleeping"
                        line = "Tap for a sleepy purr."
                    }

                    HaruLockMotion.RESTING -> {
                        greeting = "HARU is resting"
                        line = content.line
                    }

                    HaruLockMotion.RUNNING -> {
                        greeting = "HARU"
                        line = "zoom~"
                    }

                    HaruLockMotion.IDLE -> {
                        greeting = content.greeting
                        line = content.line
                    }
                }

                drawBubble(
                    canvas = canvas,
                    bounds = bubbleBounds,
                    greeting = greeting,
                    line = line,
                    density = density,
                )
                drawCat(
                    canvas = canvas,
                    bounds = bubbleBounds,
                    density = density,
                    motion = motion,
                    nowElapsed = nowElapsed,
                )
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawBubble(
            canvas: Canvas,
            bounds: RectF,
            greeting: String,
            line: String,
            density: Float,
        ) {
            bubblePaint.style = Paint.Style.FILL
            bubblePaint.color = Color.argb(224, 249, 243, 252)
            canvas.drawRoundRect(
                bounds,
                21f * density,
                21f * density,
                bubblePaint,
            )

            bubblePaint.style = Paint.Style.STROKE
            bubblePaint.strokeWidth = density
            bubblePaint.color = Color.argb(220, 215, 203, 227)
            canvas.drawRoundRect(
                bounds,
                21f * density,
                21f * density,
                bubblePaint,
            )

            greetingPaint.color = Color.rgb(36, 28, 43)
            greetingPaint.textSize = 12.5f * density
            greetingPaint.textAlign = Paint.Align.LEFT
            greetingPaint.typeface =
                android.graphics.Typeface.DEFAULT_BOLD

            linePaint.color = Color.rgb(103, 94, 109)
            linePaint.textSize = 12.5f * density
            linePaint.textAlign = Paint.Align.LEFT

            val leftText = bounds.left + 12f * density
            val centerY = bounds.centerY()
            canvas.drawText(
                greeting,
                leftText,
                centerY - 6f * density,
                greetingPaint,
            )
            canvas.drawText(
                line.take(32),
                leftText,
                centerY + 16f * density,
                linePaint,
            )
        }

        private fun drawCat(
            canvas: Canvas,
            bounds: RectF,
            density: Float,
            motion: HaruLockMotion,
            nowElapsed: Long,
        ) {
            val baseCx = bounds.right - 48f * density
            val baseCy = bounds.centerY() + 3f * density
            val breathing =
                if (motion == HaruLockMotion.SLEEPING) {
                    1f +
                        0.045f *
                        sin(
                            nowElapsed *
                                2.0 *
                                PI /
                                1_700.0
                        ).toFloat()
                } else {
                    1f
                }
            val sway =
                if (
                    motion == HaruLockMotion.IDLE ||
                    motion == HaruLockMotion.RESTING
                ) {
                    sin(nowElapsed / 420.0).toFloat() * 2f * density
                } else {
                    0f
                }
            val tailSwing =
                sin(
                    nowElapsed /
                        if (motion == HaruLockMotion.DELIGHTED) 95.0 else 230.0
                ).toFloat()
            val blinkClosed =
                motion == HaruLockMotion.SLEEPING ||
                    (
                        motion != HaruLockMotion.RUNNING &&
                            nowElapsed % 4_200L in 0L..180L
                        )
            val pawPhase =
                if (motion == HaruLockMotion.RUNNING) {
                    sin(nowElapsed / 105.0).toFloat()
                } else {
                    0f
                }

            val cx = baseCx
            val cy = baseCy + sway
            val headR = 16f * density
            val headCy = cy - 11f * density
            val body = RectF(
                cx - 21f * density,
                cy + 1f * density,
                cx + 18f * density,
                cy + (26f * breathing) * density,
            )

            catPaint.style = Paint.Style.STROKE
            catPaint.strokeWidth = 3f * density
            catPaint.strokeCap = Paint.Cap.ROUND
            catPaint.strokeJoin = Paint.Join.ROUND
            catPaint.color = Color.rgb(69, 60, 77)

            canvas.drawCircle(cx, headCy, headR, catPaint)

            earPath.reset()
            earPath.moveTo(cx - 11f * density, headCy - 10f * density)
            earPath.lineTo(cx - 16f * density, headCy - 27f * density)
            earPath.lineTo(cx - 4f * density, headCy - 17f * density)
            canvas.drawPath(earPath, catPaint)

            earPath.reset()
            earPath.moveTo(cx + 4f * density, headCy - 17f * density)
            earPath.lineTo(cx + 16f * density, headCy - 27f * density)
            earPath.lineTo(cx + 11f * density, headCy - 10f * density)
            canvas.drawPath(earPath, catPaint)

            canvas.drawOval(body, catPaint)

            val tail = Path()
            tail.moveTo(body.left + 4f * density, body.centerY())
            tail.cubicTo(
                body.left - 18f * density,
                body.centerY() - 9f * density,
                body.left - 24f * density,
                body.centerY() + (tailSwing * 10f + 14f) * density,
                body.left - 6f * density,
                body.bottom - 1f * density,
            )
            canvas.drawPath(tail, catPaint)

            val pawY = body.bottom
            canvas.drawLine(
                cx - 8f * density,
                pawY,
                cx - 10f * density,
                pawY + (5f + pawPhase * 4f) * density,
                catPaint,
            )
            canvas.drawLine(
                cx + 7f * density,
                pawY,
                cx + 9f * density,
                pawY + (5f - pawPhase * 4f) * density,
                catPaint,
            )

            detailPaint.style = Paint.Style.STROKE
            detailPaint.strokeWidth = 1.7f * density
            detailPaint.strokeCap = Paint.Cap.ROUND
            detailPaint.color = Color.rgb(69, 60, 77)

            if (blinkClosed) {
                canvas.drawLine(
                    cx - 8f * density,
                    headCy,
                    cx - 3f * density,
                    headCy + 1f * density,
                    detailPaint,
                )
                canvas.drawLine(
                    cx + 3f * density,
                    headCy + 1f * density,
                    cx + 8f * density,
                    headCy,
                    detailPaint,
                )
            } else {
                detailPaint.style = Paint.Style.FILL
                canvas.drawCircle(
                    cx - 5f * density,
                    headCy,
                    1.5f * density,
                    detailPaint,
                )
                canvas.drawCircle(
                    cx + 5f * density,
                    headCy,
                    1.5f * density,
                    detailPaint,
                )
            }

            detailPaint.style = Paint.Style.STROKE
            detailPaint.strokeWidth = 1.8f * density
            detailPaint.color = Color.rgb(197, 109, 145)
            canvas.drawCircle(
                cx,
                headCy + 5f * density,
                1.2f * density,
                detailPaint,
            )
            canvas.drawArc(
                RectF(
                    cx - 5f * density,
                    headCy + 3f * density,
                    cx + 5f * density,
                    headCy + 10f * density,
                ),
                if (motion == HaruLockMotion.DELIGHTED) 0f else 20f,
                if (motion == HaruLockMotion.DELIGHTED) 180f else 140f,
                false,
                detailPaint,
            )

            catBounds.set(
                body.left - 24f * density,
                headCy - 30f * density,
                body.right + 20f * density,
                pawY + 14f * density,
            )
            tapBounds.set(bounds)
            tapBounds.union(catBounds)
            tapBounds.inset(-24f * density, -18f * density)

            if (motion == HaruLockMotion.SLEEPING) {
                detailPaint.style = Paint.Style.FILL
                detailPaint.textAlign = Paint.Align.LEFT
                detailPaint.textSize = 10f * density
                detailPaint.color = Color.rgb(180, 167, 192)
                canvas.drawText(
                    "z",
                    cx + 18f * density,
                    headCy - 12f * density,
                    detailPaint,
                )
            } else if (motion == HaruLockMotion.DELIGHTED) {
                detailPaint.style = Paint.Style.FILL
                detailPaint.textAlign = Paint.Align.CENTER
                detailPaint.textSize = 12f * density
                detailPaint.color = Color.rgb(197, 109, 145)
                canvas.drawText(
                    "♡",
                    cx + 18f * density,
                    headCy - 20f * density,
                    detailPaint,
                )
            }
        }

        private fun pingPong(phase: Float): Float =
            if (phase <= 0.5f) {
                phase * 2f
            } else {
                (1f - phase) * 2f
            }
    }

    companion object {
        private const val RUN_CYCLE_MS = 18_000L
        private const val TAP_DEBOUNCE_MS = 250L
    }
}
