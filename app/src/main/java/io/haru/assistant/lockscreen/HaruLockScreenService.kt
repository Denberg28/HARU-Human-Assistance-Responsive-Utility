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
import io.haru.assistant.R
import io.haru.assistant.companion.AndroidCompanionStore
import io.haru.assistant.companion.HaruCheckerContentFactory
import io.haru.assistant.companion.HaruCheckerStore
import java.util.Calendar

/**
 * User-enabled lock-screen runtime.
 *
 * The foreground notification is the reliable Android/HyperOS lock-screen
 * surface. HARU is deliberately simple here: one visible companion, one pet
 * action, one cute local reaction. No app launch and no keyguard dismissal.
 */
class HaruLockScreenService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val lockScreenStore by lazy {
        LockScreenPreferenceStore(applicationContext)
    }
    private val checkerStore by lazy {
        HaruCheckerStore(applicationContext)
    }
    private val companionStore by lazy {
        AndroidCompanionStore(applicationContext)
    }
    private val keyguard by lazy {
        getSystemService(KeyguardManager::class.java)
    }
    private val power by lazy {
        getSystemService(PowerManager::class.java)
    }
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var receiverRegistered = false
    private var acknowledgementUntil = 0L
    private var acknowledgementStep = 0L

    private val wakeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> scheduleShowIfLocked()
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(showIfLocked)
                        publishCompanionNotification(isLocked = true)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        handler.removeCallbacks(showIfLocked)
                        acknowledgementUntil = 0L
                        publishCompanionNotification(isLocked = false)
                    }
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
                publishCompanionNotification(isLocked = true)
            }
        }

    private val clearAcknowledgement =
        Runnable {
            acknowledgementUntil = 0L
            publishCompanionNotification(
                isLocked = keyguard.isKeyguardLocked,
            )
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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

        when (intent?.action) {
            ACTION_PET -> acknowledgeHaru()
            else -> scheduleShowIfLocked()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(showIfLocked)
        handler.removeCallbacks(clearAcknowledgement)

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

    private fun acknowledgeHaru() {
        acknowledgementStep += 1L
        acknowledgementUntil =
            System.currentTimeMillis() + ACKNOWLEDGEMENT_MS

        publishCompanionNotification(
            isLocked = keyguard.isKeyguardLocked,
        )

        handler.removeCallbacks(clearAcknowledgement)
        handler.postDelayed(
            clearAcknowledgement,
            ACKNOWLEDGEMENT_MS,
        )
    }

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "HARU lock-screen companion",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "Shows the user-enabled HARU companion on the lock screen."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun startAsForeground() {
        val notification =
            buildCompanionNotification(
                isLocked = keyguard.isKeyguardLocked,
            )

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification,
            )
        }
    }

    private fun publishCompanionNotification(
        isLocked: Boolean,
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildCompanionNotification(isLocked),
        )
    }

    private fun buildCompanionNotification(
        isLocked: Boolean,
    ): Notification {
        val now = System.currentTimeMillis()
        val petIntent =
            PendingIntent.getService(
                this,
                PET_REQUEST_CODE,
                Intent(
                    this,
                    HaruLockScreenService::class.java,
                ).setAction(ACTION_PET),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

        val petAction =
            NotificationCompat.Action.Builder(
                R.drawable.haru_notification_icon,
                "♡  HARU",
                petIntent,
            )
                .setAuthenticationRequired(false)
                .setShowsUserInterface(false)
                .build()

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val step = now / CONTENT_ROTATION_MS
        val quiet = !checkerStore.isEnabled()

        val title: String
        val line: String

        if (now < acknowledgementUntil) {
            val acknowledgement =
                HaruCheckerContentFactory.acknowledgement(
                    acknowledgementStep,
                )
            title = "HARU  ${acknowledgement.face}"
            line = acknowledgement.line
        } else {
            val content =
                HaruCheckerContentFactory.create(
                    hourOfDay = hour,
                    step = step,
                    snapshot = companionStore.load(),
                    now = now,
                    quiet = quiet,
                )
            title = "HARU  ${content.face}"
            line =
                if (isLocked) {
                    "${content.greeting} · ${content.line}"
                } else {
                    "Ready for the next lock screen."
                }
        }

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.haru_notification_icon)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(line)
                    .setBigContentTitle(title)
                    .setSummaryText("Tap ♡ HARU for a little response")
            )
            .addAction(petAction)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID =
            "haru_lock_screen_companion_v2"
        private const val NOTIFICATION_ID = 9108
        private const val PET_REQUEST_CODE = 9109
        private const val KEYGUARD_SETTLE_MS = 300L
        private const val ACKNOWLEDGEMENT_MS = 2_500L
        private const val CONTENT_ROTATION_MS = 15L * 60L * 1000L

        private const val ACTION_PET =
            "io.haru.assistant.action.PET_LOCK_SCREEN_HARU"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(
                    context,
                    HaruLockScreenService::class.java,
                ),
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(
                    context,
                    HaruLockScreenService::class.java,
                )
            )
        }
    }
}
