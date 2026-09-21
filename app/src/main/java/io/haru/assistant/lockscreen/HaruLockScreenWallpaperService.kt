package io.haru.assistant.lockscreen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.HaruCheckerContentFactory
import io.haru.assistant.companion.HaruCheckerStore
import io.haru.assistant.companion.HaruIdlePolicy
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.CompanionModeStore
import java.util.Calendar
import kotlin.math.min

class HaruLockScreenWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HaruEngine()

    private inner class HaruEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val greetingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bubbleBounds = RectF()

        private val checkerStore by lazy {
            HaruCheckerStore(applicationContext)
        }
        private val companionStore by lazy {
            AndroidCompanionStore(applicationContext)
        }
        private val modeStore by lazy {
            CompanionModeStore(applicationContext)
        }

        private var widthPx = 0
        private var heightPx = 0
        private var visible = false
        private var delightedUntil = 0L
        private var touchDownInside = false

        private val frame =
            object : Runnable {
                override fun run() {
                    drawFrame()
                    if (visible) {
                        handler.postDelayed(this, FRAME_MS)
                    }
                }
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
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
            if (!checkerStore.isEnabled()) return

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownInside =
                        bubbleBounds.contains(
                            event.x,
                            event.y,
                        )
                }

                MotionEvent.ACTION_UP -> {
                    if (
                        touchDownInside &&
                        bubbleBounds.contains(
                            event.x,
                            event.y,
                        )
                    ) {
                        delightedUntil =
                            SystemClock.elapsedRealtime() +
                                HaruIdlePolicy.ACKNOWLEDGEMENT_MS
                        handler.removeCallbacks(frame)
                        handler.post(frame)
                    }
                    touchDownInside = false
                }

                MotionEvent.ACTION_CANCEL ->
                    touchDownInside = false
            }

            super.onTouchEvent(event)
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

                canvas.drawColor(
                    if (hour >= 22 || hour <= 5) {
                        Color.rgb(25, 22, 31)
                    } else {
                        Color.rgb(252, 247, 253)
                    }
                )

                if (!checkerStore.isEnabled()) return
                val step = nowWall / 90_000L
                val quiet =
                    modeStore.load() == CompanionMode.REST
                val motion =
                    motionState(
                        hour = hour,
                        nowElapsed = nowElapsed,
                        quiet = quiet,
                    )

                val density = resources.displayMetrics.density
                val bubbleHeight = 88f * density
                val horizontalMargin = 18f * density
                val maxBubbleWidth = 330f * density
                val bubbleWidth =
                    min(
                        widthPx - horizontalMargin * 2f,
                        maxBubbleWidth,
                    )
                val baseTop =
                    heightPx * 0.68f -
                        bubbleHeight / 2f
                val runPhase =
                    (
                        nowElapsed %
                            RUN_CYCLE_MS
                    ).toFloat() / RUN_CYCLE_MS

                val left =
                    if (motion == Motion.RUNNING) {
                        val range =
                            (widthPx - bubbleWidth)
                                .coerceAtLeast(0f)
                        range * runPhase
                    } else {
                        (widthPx - bubbleWidth) / 2f
                    }

                bubbleBounds.set(
                    left,
                    baseTop,
                    left + bubbleWidth,
                    baseTop + bubbleHeight,
                )

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
                    HaruCheckerContentFactory
                        .acknowledgement(step)

                val face: String
                val greeting: String
                val line: String

                when (motion) {
                    Motion.DELIGHTED -> {
                        face = acknowledgement.face
                        greeting = "HARU"
                        line = acknowledgement.line
                    }

                    Motion.SLEEPING -> {
                        face = "(－ω－) zzZ"
                        greeting = "HARU is sleeping"
                        line = "Zzz…"
                    }

                    Motion.RESTING -> {
                        face = "(˶ᵔ ᵕ ᵔ˶)"
                        greeting = "HARU is resting"
                        line = content.line
                    }

                    Motion.RUNNING -> {
                        val frameIndex =
                            (
                                nowElapsed /
                                    220L
                            ) % 2L
                        face =
                            if (frameIndex == 0L) {
                                "ᕕ( ᐛ )ᕗ"
                            } else {
                                "ᕕ( ^‿^ )ᕗ"
                            }
                        greeting = "HARU"
                        line = "zoom~"
                    }

                    Motion.IDLE -> {
                        face = content.face
                        greeting = content.greeting
                        line = content.line
                    }
                }

                drawBubble(
                    canvas = canvas,
                    bounds = bubbleBounds,
                    face = face,
                    greeting = greeting,
                    line = line,
                    density = density,
                )
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun motionState(
            hour: Int,
            nowElapsed: Long,
            quiet: Boolean,
        ): Motion {
            if (nowElapsed < delightedUntil) {
                return Motion.DELIGHTED
            }
            if (quiet || hour >= 22 || hour <= 5) {
                return Motion.SLEEPING
            }

            val slot =
                Math.floorMod(
                    nowElapsed / MOTION_SLOT_MS,
                    12L,
                )

            return when (slot) {
                0L, 1L -> Motion.RUNNING
                2L, 3L, 8L -> Motion.RESTING
                else -> Motion.IDLE
            }
        }

        private fun drawBubble(
            canvas: Canvas,
            bounds: RectF,
            face: String,
            greeting: String,
            line: String,
            density: Float,
        ) {
            bubblePaint.color = Color.argb(224, 249, 243, 252)
            bubblePaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                bounds,
                22f * density,
                22f * density,
                bubblePaint,
            )

            bubblePaint.color = Color.argb(220, 215, 203, 227)
            bubblePaint.style = Paint.Style.STROKE
            bubblePaint.strokeWidth = density
            canvas.drawRoundRect(
                bounds,
                22f * density,
                22f * density,
                bubblePaint,
            )

            val faceX =
                bounds.right -
                    54f * density
            val centerY = bounds.centerY()

            facePaint.color = Color.rgb(36, 28, 43)
            facePaint.textSize = 23f * density
            facePaint.textAlign = Paint.Align.CENTER
            facePaint.typeface =
                android.graphics.Typeface.DEFAULT_BOLD

            greetingPaint.color = Color.rgb(36, 28, 43)
            greetingPaint.textSize = 13f * density
            greetingPaint.textAlign = Paint.Align.LEFT
            greetingPaint.typeface =
                android.graphics.Typeface.DEFAULT_BOLD

            linePaint.color = Color.rgb(103, 94, 109)
            linePaint.textSize = 13f * density
            linePaint.textAlign = Paint.Align.LEFT

            val leftText =
                bounds.left +
                    12f * density
            val greetingY =
                centerY -
                    7f * density
            val lineY =
                centerY +
                    16f * density

            canvas.drawText(
                greeting,
                leftText,
                greetingY,
                greetingPaint,
            )
            canvas.drawText(
                line.take(34),
                leftText,
                lineY,
                linePaint,
            )
            canvas.drawText(
                face,
                faceX,
                centerY + 8f * density,
                facePaint,
            )
        }
    }

    private enum class Motion {
        IDLE,
        RUNNING,
        RESTING,
        SLEEPING,
        DELIGHTED,
    }

    companion object {
        private const val FRAME_MS = 80L
        private const val MOTION_SLOT_MS = 10_000L
        private const val RUN_CYCLE_MS = 20_000L
    }
}
