package io.haru.assistant.lockscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
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
import io.haru.assistant.companion.HaruCheckerStore

/**
 * Compact HARU touch bar that is visible only while the device keyguard is locked.
 *
 * MainActivity starts this activity after ACTION_SCREEN_OFF. The activity stays
 * transparent while the display is off, becomes visible only after the keyguard
 * is confirmed locked, and closes on ACTION_USER_PRESENT. It never dismisses the
 * keyguard, keeps the display awake, or draws over the unlocked HARU app.
 */
class HaruLockScreenActivity : Activity() {
    internal lateinit var pet: HaruLockPetView
        private set

    private var resumed = false
    private var receiverRegistered = false

    private val keyguard by lazy {
        getSystemService(KeyguardManager::class.java)
    }
    private val power by lazy {
        getSystemService(PowerManager::class.java)
    }
    private val checker by lazy {
        HaruCheckerStore(applicationContext)
    }

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> hideBar()
                    Intent.ACTION_SCREEN_ON ->
                        pet.postDelayed(
                            { syncBarVisibility() },
                            KEYGUARD_SETTLE_MS,
                        )
                    Intent.ACTION_USER_PRESENT -> {
                        hideBar()
                        finishAndRemoveTask()
                    }
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
        hideBar()

        val screenEvents =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                screenReceiver,
                screenEvents,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            registerReceiver(screenReceiver, screenEvents)
        }
        receiverRegistered = true
    }

    private fun configureBarWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        window.setDimAmount(0f)
    }

    private fun syncBarVisibility() {
        val shouldShow =
            resumed &&
                power.isInteractive &&
                keyguard.isKeyguardLocked &&
                checker.isEnabled()

        if (shouldShow) {
            showBar()
        } else {
            hideBar()
        }
    }

    private fun showBar() {
        sizeBarWindow()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        window.attributes =
            window.attributes.apply {
                alpha = 1f
                dimAmount = 0f
            }
        pet.visibility = View.VISIBLE
        pet.setActive(true)
    }

    private fun hideBar() {
        if (::pet.isInitialized) {
            pet.setActive(false)
            pet.visibility = View.INVISIBLE
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        window.setLayout(1, 1)
        window.attributes =
            window.attributes.apply {
                alpha = 0f
                dimAmount = 0f
            }
    }

    private fun sizeBarWindow() {
        val density = resources.displayMetrics.density
        val fontScale = resources.configuration.fontScale.coerceIn(1f, 1.5f)
        val barHeightPx = ((80f * fontScale + 16f) * density).toInt()
        val bottomOffsetPx = (168f * density).toInt()

        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeightPx,
        )
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
        syncBarVisibility()
    }

    override fun onPause() {
        resumed = false
        hideBar()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (resumed) {
            syncBarVisibility()
        }
    }

    override fun onDestroy() {
        if (::pet.isInitialized) {
            pet.setActive(false)
        }
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        private const val KEYGUARD_SETTLE_MS = 120L
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
        isFocusable = false
        contentDescription = "HARU. Tap for a happy reaction."
        setOnClickListener {
            if (
                active &&
                power.isInteractive &&
                scene.pet(SystemClock.elapsedRealtime())
            ) {
                contentDescription = "HARU is happy. Purr."
                invalidate()
            }
        }
    }

    fun setActive(value: Boolean) {
        active = value
        removeCallbacks(frame)
        if (value && isShown && power.isInteractive) {
            post(frame)
        }
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

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
}
