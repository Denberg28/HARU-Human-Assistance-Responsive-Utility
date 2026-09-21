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
        private var touchDownInside = false
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
            if (!checkerStore.isEnabled()) {
                super.onTouchEvent(event)
                return
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownInside =
                        bubbleBounds.contains(event.x, event.y) ||
                            catBounds.contains(event.x, event.y)
                }

                MotionEvent.ACTION_UP -> {
                    if (
                        touchDownInside &&
                        (
                            bubbleBounds.contains(event.x, event.y) ||
                                catBounds.contains(event.x, event.y)
                            )
                    ) {
                        reactToPet()
                    }
                    touchDownInside = false
                }

                MotionEvent.ACTION_CANCEL -> touchDownInside = false
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
                (
                    bubbleBounds.contains(x.toFloat(), y.toFloat()) ||
                        catBounds.contains(x.toFloat(), y.toFloat())
                    )
            ) {
                reactToPet()
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun reactToPet() {
            delightedUntil =
                SystemClock.elapsedRealtime() +
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

            val bodyW = 48f * density * breathing
            val bodyH = 30f * density * breathing
            val headR = 17f * density
            val bodyLeft = baseCx - 25f * density
            val bodyTop = baseCy - 2f * density
            val headCx = baseCx + 14f * density
            val headCy = baseCy - 10f * density

            catPaint.style = Paint.Style.FILL
            catPaint.color = Color.rgb(69, 60, 77)
            canvas.drawOval(
                RectF(
                    bodyLeft,
                    bodyTop,
                    bodyLeft + bodyW,
                    bodyTop + bodyH,
                ),
                catPaint,
            )
            canvas.drawCircle(headCx, headCy, headR, catPaint)

            earPath.reset()
            earPath.moveTo(
                headCx - 13f * density,
                headCy - 9f * density,
            )
            earPath.lineTo(
                headCx - 7f * density,
                headCy - 27f * density,
            )
            earPath.lineTo(
                headCx - 1f * density,
                headCy - 10f * density,
            )
            earPath.close()
            canvas.drawPath(earPath, catPaint)

            earPath.reset()
            earPath.moveTo(
                headCx + 2f * density,
                headCy - 10f * density,
            )
            earPath.lineTo(
                headCx + 10f * density,
                headCy - 27f * density,
            )
            earPath.lineTo(
                headCx + 15f * density,
                headCy - 7f * density,
            )
            earPath.close()
            canvas.drawPath(earPath, catPaint)

            detailPaint.style = Paint.Style.STROKE
            detailPaint.strokeCap = Paint.Cap.ROUND
            detailPaint.strokeWidth = 3f * density
            detailPaint.color = Color.rgb(69, 60, 77)

            val tailStartX = bodyLeft + 5f * density
            val tailStartY = bodyTop + 15f * density
            val tail = Path()
            tail.moveTo(tailStartX, tailStartY)
            tail.cubicTo(
                tailStartX - 19f * density,
                tailStartY - 7f * density,
                tailStartX - 22f * density,
                tailStartY + (tailSwing * 12f + 13f) * density,
                tailStartX - 7f * density,
                tailStartY + 17f * density,
            )
            canvas.drawPath(tail, detailPaint)

            detailPaint.style = Paint.Style.FILL
            detailPaint.color = Color.rgb(246, 235, 245)

            if (blinkClosed) {
                detailPaint.style = Paint.Style.STROKE
                detailPaint.strokeWidth = 1.7f * density
                canvas.drawLine(
                    headCx - 8f * density,
                    headCy,
                    headCx - 3f * density,
                    headCy + 1f * density,
                    detailPaint,
                )
                canvas.drawLine(
                    headCx + 3f * density,
                    headCy + 1f * density,
                    headCx + 8f * density,
                    headCy,
                    detailPaint,
                )
            } else {
                detailPaint.style = Paint.Style.FILL
                canvas.drawCircle(
                    headCx - 5f * density,
                    headCy,
                    1.8f * density,
                    detailPaint,
                )
                canvas.drawCircle(
                    headCx + 6f * density,
                    headCy,
                    1.8f * density,
                    detailPaint,
                )
            }

            detailPaint.color = Color.rgb(230, 177, 195)
            canvas.drawCircle(
                headCx + 0.5f * density,
                headCy + 5f * density,
                1.7f * density,
                detailPaint,
            )

            detailPaint.style = Paint.Style.STROKE
            detailPaint.strokeWidth = 2.4f * density
            detailPaint.strokeCap = Paint.Cap.ROUND
            detailPaint.color = Color.rgb(69, 60, 77)

            val pawY =
                bodyTop +
                    bodyH -
                    1f * density
            canvas.drawLine(
                baseCx - 8f * density,
                pawY,
                baseCx - 10f * density,
                pawY + (5f + pawPhase * 4f) * density,
                detailPaint,
            )
            canvas.drawLine(
                baseCx + 7f * density,
                pawY,
                baseCx + 9f * density,
                pawY + (5f - pawPhase * 4f) * density,
                detailPaint,
            )

            catBounds.set(
                bodyLeft - 24f * density,
                headCy - 28f * density,
                headCx + 20f * density,
                pawY + 14f * density,
            )

            if (motion == HaruLockMotion.SLEEPING) {
                detailPaint.style = Paint.Style.FILL
                detailPaint.textAlign = Paint.Align.LEFT
                detailPaint.textSize = 10f * density
                detailPaint.color = Color.rgb(180, 167, 192)
                canvas.drawText(
                    "z",
                    headCx + 18f * density,
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
                    headCx,
                    headCy - 28f * density,
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
    }
}
