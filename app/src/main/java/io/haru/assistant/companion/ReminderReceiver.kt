package io.haru.assistant.companion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.haru.assistant.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminder_id").orEmpty()
        val reminderText = intent.getStringExtra("reminder_text").orEmpty()
        if (reminderId.isBlank() || reminderText.isBlank()) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "HARU reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Personal reminders created in HARU"
                    lockscreenVisibility =
                        android.app.Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                }
            )
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

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(
                    io.haru.assistant.R.drawable.haru_notification_icon
                )
                .setContentTitle("HARU reminder")
                .setContentText(reminderText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(reminderText)
                )
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .build()

        notificationManager.notify(reminderId.hashCode(), notification)

        AndroidCompanionStore(context)
            .removeReminder(reminderId)

        HaruBubbleWidgetProvider.refreshAll(context)
    }

    companion object {
        const val CHANNEL_ID = "haru_reminders_v2"
    }
}
