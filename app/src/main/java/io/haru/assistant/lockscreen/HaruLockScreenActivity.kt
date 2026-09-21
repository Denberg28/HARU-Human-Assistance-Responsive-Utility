package io.haru.assistant.lockscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Explicit foreground session. Never launches itself or dismisses the device keyguard. */
class HaruLockScreenActivity : Activity() {
    internal lateinit var pet: HaruLockPetView
        private set
    private var resumed = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pet.setActive(false)
                Intent.ACTION_SCREEN_ON -> pet.setActive(resumed)
                Intent.ACTION_USER_PRESENT -> finishAndRemoveTask()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 branch listens only to protected system actions.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        // No TURN_SCREEN_ON, KEEP_SCREEN_ON, dismiss-keyguard, overlay or accessibility service.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(250, 247, 252))
            val inset = (24 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset, inset, inset)
            fitsSystemWindows = true
        }
        fun label(value: String, size: Float) = TextView(this).apply {
            text = value
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(69, 55, 82))
        }
        root.addView(label("HARU", 26f))
        root.addView(label("Interactive lock-screen session", 14f))
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 2f))
        pet = HaruLockPetView(this)
        root.addView(pet, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            (104 * resources.displayMetrics.density * resources.configuration.fontScale.coerceAtMost(1.5f)).toInt()))
        root.addView(label("Tap HARU for a happy reaction.", 14f))
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(label("Leave this session open, then lock your phone with the power button. Wake the screen to pet HARU. Your normal screen lock stays in place.", 14f))
        root.addView(Button(this).apply {
            text = "Close session"
            setOnClickListener { finishAndRemoveTask() }
        })
        setContentView(root)
        val screenEvents = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenEvents, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // These three broadcasts can only be sent by Android. No AndroidX private permission needed.
            registerReceiver(screenReceiver, screenEvents)
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
    private val frame = object : Runnable {
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
        scene.draw(canvas, width, height, SystemClock.elapsedRealtime(), System.currentTimeMillis(), 0.5f)
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
