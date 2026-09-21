package io.haru.assistant.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Only HARU and HARU-created immutable PendingIntents may invoke these actions. */
class HaruBubbleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HaruBubbleWidgetProvider.ACTION_CYCLE -> {
                if (HaruBubbleWidgetProvider.installedCount(context) == 0 ||
                    !HaruBubbleStore(context).isEnabled()) return
                HaruBubbleStore(context).advance()
                HaruBubbleWidgetProvider.refreshAll(context)
            }
            HaruBubbleWidgetProvider.ACTION_PINNED,
            HaruBubbleWidgetProvider.ACTION_REFRESH -> HaruBubbleWidgetProvider.refreshAll(context)
        }
    }
}
