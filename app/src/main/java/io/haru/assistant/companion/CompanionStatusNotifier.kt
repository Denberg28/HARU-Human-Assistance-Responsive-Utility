package io.haru.assistant.companion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.haru.assistant.MainActivity
import io.haru.assistant.R
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.lockScreenStatus

class CompanionStatusStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_companion_status",
            Context.MODE_PRIVATE,
        )

    fun isEnabled(): Boolean =
        preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "lock_screen_enabled"
    }
}

object CompanionStatusNotifier {
    const val CHANNEL_ID = "haru_companion_status"
    private const val NOTIFICATION_ID = 4107

    fun refresh(
        context: Context,
        mode: CompanionMode,
    ) {
        val store = CompanionStatusStore(context)
        if (!store.isEnabled()) {
            cancel(context)
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        ensureChannel(manager)

        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                4107,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

        val status = mode.lockScreenStatus()

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.haru_notification_icon)
                .setContentTitle(status.title)
                .setContentText(status.message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(status.message)
                )
                .setContentIntent(openPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)

        chibiBitmap(context)?.let(builder::setLargeIcon)

        manager.notify(
            NOTIFICATION_ID,
            builder.build(),
        )
    }

    fun cancel(context: Context) {
        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(
        manager: NotificationManager,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val existing =
            manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "HARU lock-screen companion",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "Quiet HARU companion status shown on the lock screen"
                lockscreenVisibility =
                    Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun chibiBitmap(
        context: Context,
    ): Bitmap? {
        val drawable =
            ContextCompat.getDrawable(
                context,
                R.drawable.haru_launcher_icon,
            ) ?: return null

        val width =
            drawable.intrinsicWidth
                .takeIf { it > 0 }
                ?: 108
        val height =
            drawable.intrinsicHeight
                .takeIf { it > 0 }
                ?: 108

        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
            )

        drawable.setBounds(
            0,
            0,
            width,
            height,
        )
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
