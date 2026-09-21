package io.haru.assistant.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import io.haru.assistant.R

/** Only HARU and HARU-created immutable PendingIntents may invoke these actions. */
class HaruBubbleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HaruBubbleWidgetProvider.ACTION_ACKNOWLEDGE -> {
                if (
                    HaruBubbleWidgetProvider.installedCount(context) == 0 ||
                    !HaruBubbleStore(context).isEnabled()
                ) {
                    return
                }

                val pending = goAsync()
                val store = HaruBubbleStore(context)
                val acknowledgement =
                    HaruBubbleContentFactory.acknowledgement(store.step())
                val manager = AppWidgetManager.getInstance(context)
                val ids =
                    manager.getAppWidgetIds(
                        ComponentName(
                            context,
                            HaruBubbleWidgetProvider::class.java,
                        )
                    )

                ids.forEach { id ->
                    val views =
                        RemoteViews(
                            context.packageName,
                            R.layout.haru_bubble_widget,
                        )
                    views.setTextViewText(
                        R.id.haru_bubble_greeting,
                        "HARU",
                    )
                    views.setTextViewText(
                        R.id.haru_bubble_line,
                        acknowledgement.line,
                    )
                    views.setTextViewText(
                        R.id.haru_bubble_face,
                        acknowledgement.face,
                    )
                    manager.partiallyUpdateAppWidget(id, views)
                }

                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        store.advance()
                        HaruBubbleWidgetProvider.refreshAll(context)
                        pending?.finish()
                    },
                    HaruIdlePolicy.ACKNOWLEDGEMENT_MS,
                )
            }

            HaruBubbleWidgetProvider.ACTION_PINNED,
            HaruBubbleWidgetProvider.ACTION_REFRESH ->
                HaruBubbleWidgetProvider.refreshAll(context)
        }
    }
}
