package io.haru.assistant.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object ReminderScheduler {
    fun rescheduleAll(context: Context) {
        AndroidCompanionStore(context).load().reminders.forEach { schedule(context, it) }
    }

    private fun reminderIntent(context: Context, id: String) =
        Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.Builder().scheme("haru").authority("reminder").appendPath(id).build()
            putExtra("reminder_id", id)
        }

    fun schedule(
        context: Context,
        reminder: CompanionReminder,
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Remove pre-0.7.1 alarms as well when upgrading their identity.
        cancel(context, reminder.id)
        val intent = reminderIntent(context, reminder.id)

        val requestCode = reminder.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            maxOf(reminder.dueAt, System.currentTimeMillis()),
            pendingIntent,
        )
    }

    fun cancel(
        context: Context,
        reminderId: String,
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel both new URI identities and legacy hash-only identities.
        listOf(reminderIntent(context, reminderId), Intent(context, ReminderReceiver::class.java))
            .forEach { intent ->
                val pendingIntent = PendingIntent.getBroadcast(
                    context, reminderId.hashCode(), intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
    }
}
