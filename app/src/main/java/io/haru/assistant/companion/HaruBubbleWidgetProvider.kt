package io.haru.assistant.companion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import io.haru.assistant.R
import io.haru.assistant.core.CompanionMode
import io.haru.assistant.core.CompanionModeStore
import java.util.Calendar

class HaruBubbleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, options: Bundle,
    ) {
        updateWidget(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "io.haru.assistant.action.HARU_BUBBLE_ACKNOWLEDGE"
        const val ACTION_REFRESH = "io.haru.assistant.action.HARU_BUBBLE_REFRESH"
        const val ACTION_PINNED = "io.haru.assistant.action.HARU_BUBBLE_PINNED"
        private const val AUTO_ROTATION_MS = 30L * 60L * 1000L

        fun installedCount(context: Context): Int =
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, HaruBubbleWidgetProvider::class.java),
            ).size

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HaruBubbleWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val store = HaruBubbleStore(context)
            val enabled = store.isEnabled()
            val now = System.currentTimeMillis()
            val content = HaruBubbleContentFactory.create(
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                step = store.step() + now / AUTO_ROTATION_MS,
                snapshot = AndroidCompanionStore(context).load(),
                now = now,
                quiet = CompanionModeStore(context).load() == CompanionMode.REST,
            )
            val views = RemoteViews(context.packageName, R.layout.haru_bubble_widget)
            views.setViewVisibility(R.id.haru_bubble_content, if (enabled) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.haru_bubble_paused, if (enabled) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.haru_bubble_face, content.face)
            views.setTextViewText(R.id.haru_bubble_greeting, content.greeting)
            views.setTextViewText(R.id.haru_bubble_line, content.line)
            views.setContentDescription(R.id.haru_bubble_face, "HARU. Tap to acknowledge her check-in")
            views.setContentDescription(R.id.haru_bubble_line, content.line)
            val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
            val compact = height < 130 * context.resources.configuration.fontScale
            views.setViewVisibility(R.id.haru_bubble_greeting, if (compact) View.GONE else View.VISIBLE)
            views.setInt(R.id.haru_bubble_line, "setMaxLines", if (height < 120) 1 else 2)

            val cycleIntent = Intent(context, HaruBubbleActionReceiver::class.java).apply {
                action = ACTION_ACKNOWLEDGE
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val acknowledge = PendingIntent.getBroadcast(context, id, cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.haru_bubble_face, acknowledge)
            manager.updateAppWidget(id, views)
        }
    }
}

class HaruBubbleStore(context: Context) {
    private val preferences = context.getSharedPreferences("haru_bubble", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean("enabled", true)
    fun setEnabled(enabled: Boolean) { preferences.edit().putBoolean("enabled", enabled).apply() }
    fun step(): Long = preferences.getLong("step", 0L)
    fun advance() {
        preferences.edit().putLong("step", Math.floorMod(step() + 1L, 120L)).apply()
    }
}
