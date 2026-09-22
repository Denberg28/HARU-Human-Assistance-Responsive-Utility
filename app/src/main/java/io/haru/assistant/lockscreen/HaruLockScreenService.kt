package io.haru.assistant.lockscreen

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.haru.assistant.MainActivity
import io.haru.assistant.R

/**
 * Keeps the lock-screen event receiver alive while the user has explicitly
 * enabled Lock-screen HARU.
 *
 * ACTION_SCREEN_ON/OFF cannot be handled by a manifest receiver. A foreground
 * service keeps HARU out of Android's cached process state so the wake event is
 * received reliably even after MainActivity is no longer visible.
 */
class HaruLockScreenService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val lockScreenStore by lazy {
        LockScreenPreferenceStore(applicationContext)
    }
    private val keyguard by lazy {
        getSystemService(KeyguardManager::class.java)
    }
    private val power by lazy {
        getSystemService(PowerManager::class.java)
    }

    private var receiverRegistered = false

    private val wakeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> scheduleShowIfLocked()
                    Intent.ACTION_SCREEN_OFF,
                    Intent.ACTION_USER_PRESENT -> handler.removeCallbacks(showIfLocked)
                }
            }
        }

    private val showIfLocked =
        Runnable {
            if (
                lockScreenStore.isEnabled() &&
                power.isInteractive &&
                keyguard.isKeyguardLocked
            ) {
                HaruLockScreenLauncher.launch(applicationContext)
            }
        }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        registerWakeReceiver()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!lockScreenStore.isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        scheduleShowIfLocked()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(showIfLocked)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(wakeReceiver) }
            receiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleShowIfLocked() {
        handler.removeCallbacks(showIfLocked)
        handler.postDelayed(showIfLocked, KEYGUARD_SETTLE_MS)
    }

    private fun registerWakeReceiver() {
        if (receiverRegistered) return

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                wakeReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(wakeReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun startAsForeground() {
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "HARU lock-screen runtime",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description =
                        "Keeps HARU ready for the user-enabled lock-screen companion."
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }

        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.haru_notification_icon)
                .setContentTitle("HARU lock-screen companion")
                .setContentText("Ready while Lock-screen HARU is on.")
                .setContentIntent(openApp)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "haru_lock_screen_runtime_v1"
        private const val NOTIFICATION_ID = 9108
        private const val KEYGUARD_SETTLE_MS = 260L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HaruLockScreenService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, HaruLockScreenService::class.java)
            )
        }
    }
}
