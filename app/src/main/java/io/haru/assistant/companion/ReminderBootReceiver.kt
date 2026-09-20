package io.haru.assistant.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val now = System.currentTimeMillis()
        AndroidCompanionStore(context)
            .load()
            .reminders
            .filter { it.dueAt > now }
            .forEach { ReminderScheduler.schedule(context, it) }

        HaruBubbleWidgetProvider.refreshAll(context)
    }
}
