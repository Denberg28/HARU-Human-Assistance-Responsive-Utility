package io.haru.assistant.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.haru.assistant.lockscreen.HaruLockScreenService
import io.haru.assistant.lockscreen.LockScreenPreferenceStore

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        ReminderScheduler.rescheduleAll(context)

        if (LockScreenPreferenceStore(context).isEnabled()) {
            HaruLockScreenService.start(context)
        }
    }
}
