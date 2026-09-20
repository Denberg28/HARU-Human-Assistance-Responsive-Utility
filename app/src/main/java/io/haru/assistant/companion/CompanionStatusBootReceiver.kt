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

        val store = CompanionStatusStore(context)
        if (!store.isEnabled()) return

        CompanionStatusNotifier.refresh(
            context,
            CompanionModeStore(context).load(),
        )
    }
}
