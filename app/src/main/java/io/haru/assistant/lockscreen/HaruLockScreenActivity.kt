package io.haru.assistant.lockscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Compact, explicitly opened HARU touch window above the device keyguard.
 *
 * The window is limited to HARU's bar. It does not paint a replacement wallpaper,
 * dismiss the keyguard, keep the display awake, or consume touches outside the bar.
 */
class HaruLockScreenActivity : Activity() {
    internal lateinit var pet: HaruLockPetView
        private set

    private var resumed = false

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> pet.setActive(false)
                    Intent.ACTION_SCREEN_ON -> pet.setActive(resumed)
                    Intent.ACTION_USER_PRESENT -> finishAndRemoveTask()
                }
            }
        }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        configureBarWindow()
        pet = HaruLockPetView(this)
        setContentView(pet)
        sizeBarWindow()

        val screenEvents =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenEvents, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, screenEvents)
        }
    }

    private fun configureBarWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setDimAmount(0f)
    }

    private fun sizeBarWindow() {
        val density = resources.displayMetrics.density
        val fontScale = resources.configuration.fontScale.coerceIn(1f, 1.5f)
        val barHeightPx = ((80f * fontScale + 16f) * density).toInt()
        val bottomOffsetPx = (168f * density).toInt()

        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, barHeightPx)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.attributes =
            window.attributes.apply {
                y = bottomOffsetPx
                dimAmount = 0f
            }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        pet.setActive(true)
    }

    override fun onPause() {
        resumed = false
        pet.setActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        pet.setActive(false)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}

internal class HaruLockPetView(context: Context) : View(context) {
    internal val scene = HaruLockScreenScene(context)
    internal var active = false
        private set

    private val power = context.getSystemService(PowerManager::class.java)

    private val frame =
        object : Runnable {
            override fun run() {
                if (!active || !isShown || !power.isInteractive) return
                invalidate()
                postDelayed(this, scene.nextFrameDelayMs)
            }
        }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "HARU. Tap for a happy reaction."
        setOnClickListener {
            if (active && power.isInteractive && scene.pet(SystemClock.elapsedRealtime())) {
                contentDescription = "HARU is happy. Purr."
                invalidate()
            }
        }
    }

    fun setActive(value: Boolean) {
        active = value
        removeCallbacks(frame)
        if (value && isShown && power.isInteractive) post(frame)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scene.draw(
            canvas,
            width,
            height,
            SystemClock.elapsedRealtime(),
            System.currentTimeMillis(),
        )
        if (SystemClock.elapsedRealtime() >= scene.delightedUntil) {
            contentDescription = "HARU. Tap for a happy reaction."
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        setActive(active)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setActive(active)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
}
