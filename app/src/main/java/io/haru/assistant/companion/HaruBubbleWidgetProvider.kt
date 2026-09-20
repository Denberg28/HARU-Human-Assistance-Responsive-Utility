package io.haru.assistant.companion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.haru.assistant.MainActivity
import io.haru.assistant.R
import java.util.Calendar

class HaruBubbleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                widgetId = widgetId,
            )
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_CYCLE -> {
                HaruBubbleStore(context).advance()
                refreshAll(context)
            }
            ACTION_REFRESH ->
                refreshAll(context)
        }
    }

    companion object {
        const val ACTION_CYCLE =
            "io.haru.assistant.action.HARU_BUBBLE_CYCLE"
        const val ACTION_REFRESH =
            "io.haru.assistant.action.HARU_BUBBLE_REFRESH"

        fun refreshAll(context: Context) {
            val manager =
                AppWidgetManager.getInstance(context)
            val component =
                ComponentName(
                    context,
                    HaruBubbleWidgetProvider::class.java,
                )
            val ids =
                manager.getAppWidgetIds(component)

            ids.forEach { widgetId ->
                updateWidget(
                    context = context,
                    appWidgetManager = manager,
                    widgetId = widgetId,
                )
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
        ) {
            val store = HaruBubbleStore(context)
            val enabled = store.isEnabled()
            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.haru_bubble_widget,
                )

            if (!enabled) {
                views.setViewVisibility(
                    R.id.haru_bubble_content,
                    View.GONE,
                )
                views.setViewVisibility(
                    R.id.haru_bubble_paused,
                    View.VISIBLE,
                )
            } else {
                val now = System.currentTimeMillis()
                val calendar = Calendar.getInstance()
                val content =
                    HaruBubbleContentFactory.create(
                        hourOfDay =
                            calendar.get(Calendar.HOUR_OF_DAY),
                        step = store.step(),
                        snapshot =
                            AndroidCompanionStore(context).load(),
                        now = now,
                    )

                views.setViewVisibility(
                    R.id.haru_bubble_content,
                    View.VISIBLE,
                )
                views.setViewVisibility(
                    R.id.haru_bubble_paused,
                    View.GONE,
                )
                views.setTextViewText(
                    R.id.haru_bubble_face,
                    content.face,
                )
                views.setTextViewText(
                    R.id.haru_bubble_greeting,
                    content.greeting,
                )
                views.setTextViewText(
                    R.id.haru_bubble_line,
                    content.line,
                )
            }

            val openIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val openPendingIntent =
                PendingIntent.getActivity(
                    context,
                    7001,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(
                R.id.haru_bubble_root,
                openPendingIntent,
            )

            val cycleIntent =
                Intent(
                    context,
                    HaruBubbleWidgetProvider::class.java,
                ).apply {
                    action = ACTION_CYCLE
                }
            val cyclePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    7002,
                    cycleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(
                R.id.haru_bubble_face,
                cyclePendingIntent,
            )

            appWidgetManager.updateAppWidget(
                widgetId,
                views,
            )
        }
    }
}

class HaruBubbleStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_bubble",
            Context.MODE_PRIVATE,
        )

    fun isEnabled(): Boolean =
        preferences.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun step(): Long =
        preferences.getLong(KEY_STEP, 0L)

    fun advance() {
        preferences.edit()
            .putLong(KEY_STEP, step() + 1L)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STEP = "step"
    }
}
