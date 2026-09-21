package io.haru.assistant.lockscreen

import android.app.WallpaperManager
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.Calendar

class HaruLockScreenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = HaruEngine()

    private inner class HaruEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scene by lazy { HaruLockScreenScene(applicationContext) }
        private var visible = false
        private var surfaceReady = false
        private var widthPx = 0
        private var heightPx = 0
        private val gestures = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean = petAt(e.x, e.y)
        })
        private val frame = object : Runnable {
            override fun run() {
                if (!visible || !surfaceReady) return
                drawFrame()
                handler.postDelayed(this, scene.nextFrameDelayMs)
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(false)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            scheduleFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
            widthPx = width
            heightPx = height
            scheduleFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            surfaceReady = false
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (visible && surfaceReady) gestures.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        override fun onCommand(action: String, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) petAt(x.toFloat(), y.toFloat())
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun petAt(x: Float, y: Float): Boolean {
            if (!visible || !surfaceReady || !scene.contains(x, y)) return false
            if (!scene.pet(SystemClock.elapsedRealtime())) return false
            scheduleFrame()
            return true
        }

        private fun scheduleFrame() {
            handler.removeCallbacks(frame)
            if (visible && surfaceReady) handler.post(frame)
        }

        private fun drawFrame() {
            if (widthPx <= 0 || heightPx <= 0) return
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val night = HaruLockScreenMotionPolicy.isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                canvas.drawColor(if (night) Color.rgb(24, 22, 31) else Color.rgb(250, 247, 252))
                scene.draw(canvas, widthPx, heightPx, SystemClock.elapsedRealtime(), System.currentTimeMillis())
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
