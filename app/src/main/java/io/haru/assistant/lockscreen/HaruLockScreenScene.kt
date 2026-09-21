package io.haru.assistant.lockscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.HaruCheckerContent
import io.haru.assistant.companion.HaruCheckerContentFactory
import io.haru.assistant.companion.HaruCheckerStore
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.CompanionModeStore
import io.haru.assistant.core.HaruFaces
import io.haru.assistant.core.HaruMood
import java.util.Calendar
import kotlin.math.min
import kotlin.math.sin

/** Shared original face pack for wallpaper and the touchable lock-screen session. */
internal class HaruLockScreenScene(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val fontScale = context.resources.configuration.fontScale.coerceAtMost(1.5f)
    private val checker = HaruCheckerStore(context)
    private val companion = AndroidCompanionStore(context)
    private val mode = CompanionModeStore(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val tapBounds = RectF()
    private var content: HaruCheckerContent? = null
    private var contentKey = Long.MIN_VALUE
    private var lastQuiet = false
    private var lastPet: Long? = null
    internal var delightedUntil = 0L
        private set
    internal var nextFrameDelayMs = HaruLockScreenMotionPolicy.IDLE_FRAME_MS
        private set

    fun contains(x: Float, y: Float) = tapBounds.contains(x, y)

    fun pet(now: Long): Boolean {
        if (!checker.isEnabled() || lastPet?.let { now - it < 250L } == true) return false
        lastPet = now
        delightedUntil = now + HaruLockScreenMotionPolicy.INTERACTION_MS
        return true
    }

    fun draw(canvas: Canvas, width: Int, height: Int, elapsed: Long, wall: Long) {
        tapBounds.setEmpty()
        if (width <= 0 || height <= 0) return
        if (!checker.isEnabled()) {
            nextFrameDelayMs = 1_000L
            return
        }
        val hour = Calendar.getInstance().apply { timeInMillis = wall }.get(Calendar.HOUR_OF_DAY)
        val quiet = mode.load() == CompanionMode.REST
        val step = wall / 90_000L
        if (content == null || contentKey != step || quiet != lastQuiet) {
            content = HaruCheckerContentFactory.create(hour, step, companion.load(), wall, quiet)
            contentKey = step
            lastQuiet = quiet
        }
        val current = requireNotNull(content)
        val motion = HaruLockScreenMotionPolicy.motion(hour, elapsed, quiet, delightedUntil)
        nextFrameDelayMs = HaruLockScreenMotionPolicy.frameDelayMillis(motion)
        val acknowledgement = HaruCheckerContentFactory.acknowledgement(step)
        val face = faceFor(motion, current.face, acknowledgement.face)
        val greeting = when (motion) {
            HaruLockMotion.DELIGHTED, HaruLockMotion.RUNNING -> "HARU"
            HaruLockMotion.SLEEPING -> "HARU is sleeping"
            HaruLockMotion.RESTING -> "HARU is resting"
            HaruLockMotion.IDLE -> current.greeting
        }
        val line = when (motion) {
            HaruLockMotion.DELIGHTED -> acknowledgement.line
            HaruLockMotion.SLEEPING -> "Resting here with you."
            HaruLockMotion.RUNNING -> "A little happy wander~"
            else -> current.line
        }
        val cardWidth = min(width - 24f * density, 340f * density).coerceAtLeast(1f)
        val cardHeight = min(64f * density * fontScale, height.toFloat())
        bounds.set(
            (width - cardWidth) / 2f,
            (height - cardHeight) / 2f,
            (width + cardWidth) / 2f,
            (height + cardHeight) / 2f,
        )
        paint.color = Color.rgb(237, 229, 243)
        canvas.drawRoundRect(bounds, 18f * density, 18f * density, paint)
        // The visual card is the only HARU hit target.
        tapBounds.set(bounds)
        val faceWidth = min(120f * density, cardWidth * 0.40f)
        val textLeft = bounds.left + 12f * density
        val textWidth = (cardWidth - faceWidth - 30f * density).coerceAtLeast(1f)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 12.5f * density * fontScale
        text.color = Color.rgb(46, 35, 55)
        text.typeface = Typeface.DEFAULT_BOLD
        val baseline = bounds.centerY() - 4f * density
        canvas.drawText(TextUtils.ellipsize(greeting, text, textWidth, TextUtils.TruncateAt.END).toString(), textLeft, baseline, text)
        text.typeface = Typeface.DEFAULT
        text.color = Color.rgb(100, 85, 111)
        canvas.drawText(TextUtils.ellipsize(line, text, textWidth, TextUtils.TruncateAt.END).toString(), textLeft, baseline + 19f * density * fontScale, text)

        text.textSize = 25f * density
        val measured = text.measureText(face)
        if (measured > faceWidth - 12f * density) text.textSize *= (faceWidth - 12f * density) / measured
        text.textAlign = Paint.Align.CENTER
        text.color = Color.rgb(69, 55, 82)
        val cx = bounds.right - faceWidth / 2f - 6f * density
        val cy = bounds.centerY()
        val wave = sin(elapsed / 130.0).toFloat()
        val dx = if (motion == HaruLockMotion.RUNNING) sin(elapsed / 750.0).toFloat() * 4f * density else 0f
        val dy = if (motion == HaruLockMotion.DELIGHTED || motion == HaruLockMotion.RUNNING) wave * 2f * density else 0f
        val breath = if (motion == HaruLockMotion.SLEEPING) 1f + sin(elapsed / 700.0).toFloat() * 0.025f else 1f
        val checkpoint = canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(dx, dy)
        canvas.scale(1f, breath, cx, cy)
        canvas.drawText(face, cx, cy - (text.ascent() + text.descent()) / 2f, text)
        canvas.restoreToCount(checkpoint)
    }

    companion object {
        internal fun faceFor(motion: HaruLockMotion, idle: String, acknowledgement: String): String = when (motion) {
            HaruLockMotion.DELIGHTED -> acknowledgement
            HaruLockMotion.SLEEPING, HaruLockMotion.RESTING -> HaruFaces.forMood(HaruMood.SLEEPY)
            HaruLockMotion.RUNNING -> HaruFaces.forMood(HaruMood.HAPPY)
            HaruLockMotion.IDLE -> idle
        }
    }
}
