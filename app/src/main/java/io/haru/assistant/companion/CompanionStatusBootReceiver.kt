package io.haru.assistant.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.haru.assistant.core.CompanionModeStore

class CompanionStatusBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val statusStore = CompanionStatusStore(context)
        if (!statusStore.isEnabled()) return

        val snapshot = AndroidCompanionStore(context).load()
        val now = System.currentTimeMillis()

        CompanionStatusNotifier.refresh(
            context = context,
            mode = CompanionModeStore(context).load(),
            openTasks = snapshot.tasks.count { !it.done },
            upcomingReminders =
                snapshot.reminders.count { it.dueAt > now },
            todayLines = snapshot.lockScreenLines(now),
        )
    }
}
