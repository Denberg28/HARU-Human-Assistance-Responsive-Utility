package io.haru.assistant.companion

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import io.haru.assistant.R
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class HaruBubbleWidgetTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    private val manager
        get() = AppWidgetManager.getInstance(context)

    private fun addWidget() =
        shadowOf(manager).createWidget(
            HaruBubbleWidgetProvider::class.java,
            R.layout.haru_bubble_widget,
        )

    private fun view(id: Int) =
        shadowOf(manager).getViewFor(id)

    @Test
    fun firstPlacementRendersAndReportsActualWidget() {
        assertEquals(
            0,
            HaruBubbleWidgetProvider.installedCount(context),
        )
        val id = addWidget()
        assertEquals(
            1,
            HaruBubbleWidgetProvider.installedCount(context),
        )
        assertTrue(
            view(id)
                .findViewById<TextView>(R.id.haru_bubble_line)
                .text
                .isNotBlank()
        )
        assertEquals(
            View.VISIBLE,
            view(id)
                .findViewById<View>(R.id.haru_bubble_content)
                .visibility,
        )
    }

    @Test
    fun faceTapAcknowledgesWithoutLaunchingChat() {
        val id = addWidget()
        val before =
            view(id)
                .findViewById<TextView>(R.id.haru_bubble_line)
                .text
                .toString()

        view(id)
            .findViewById<View>(R.id.haru_bubble_face)
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val reaction =
            view(id)
                .findViewById<TextView>(R.id.haru_bubble_line)
                .text
                .toString()

        assertNotEquals(before, reaction)
        assertTrue(
            reaction.contains("Purr") ||
                reaction.contains("noticed") ||
                reaction.contains("Happy") ||
                reaction.contains("here")
        )
        assertTrue(shadowOf(context).nextStartedActivity == null)

        shadowOf(Looper.getMainLooper()).idleFor(
            HaruIdlePolicy.ACKNOWLEDGEMENT_MS,
            TimeUnit.MILLISECONDS,
        )

        assertEquals(1L, HaruBubbleStore(context).step())
    }

    @Test
    fun pauseSurvivesRefreshAndBlocksAcknowledgement() {
        val id = addWidget()
        HaruBubbleStore(context).setEnabled(false)
        HaruBubbleWidgetProvider.refreshAll(context)

        assertEquals(
            View.GONE,
            view(id)
                .findViewById<View>(R.id.haru_bubble_content)
                .visibility,
        )
        assertEquals(
            View.VISIBLE,
            view(id)
                .findViewById<View>(R.id.haru_bubble_paused)
                .visibility,
        )

        HaruBubbleActionReceiver().onReceive(
            context,
            Intent(HaruBubbleWidgetProvider.ACTION_ACKNOWLEDGE),
        )
        shadowOf(Looper.getMainLooper()).idleFor(
            HaruIdlePolicy.ACKNOWLEDGEMENT_MS,
            TimeUnit.MILLISECONDS,
        )
        assertEquals(0L, HaruBubbleStore(context).step())

        HaruBubbleStore(context).setEnabled(true)
        HaruBubbleWidgetProvider.refreshAll(context)
        assertEquals(
            View.VISIBLE,
            view(id)
                .findViewById<View>(R.id.haru_bubble_content)
                .visibility,
        )
    }

    @Test
    fun resizeAndSystemRefreshKeepWidgetUsable() {
        val id = addWidget()
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    100,
                )
            },
        )

        assertEquals(
            View.GONE,
            view(id)
                .findViewById<View>(R.id.haru_bubble_greeting)
                .visibility,
        )
        assertEquals(
            1,
            view(id)
                .findViewById<TextView>(R.id.haru_bubble_line)
                .maxLines,
        )

        HaruBubbleWidgetProvider().onReceive(
            context,
            Intent(Intent.ACTION_TIMEZONE_CHANGED),
        )
        assertTrue(
            view(id)
                .findViewById<TextView>(R.id.haru_bubble_line)
                .text
                .isNotBlank()
        )

        ReminderBootReceiver().onReceive(
            context,
            Intent(Intent.ACTION_MY_PACKAGE_REPLACED),
        )
        assertEquals(
            1,
            HaruBubbleWidgetProvider.installedCount(context),
        )
    }

    @Test
    fun publicProviderIgnoresPrivateAcknowledgeCommand() {
        addWidget()

        HaruBubbleWidgetProvider().onReceive(
            context,
            Intent(HaruBubbleWidgetProvider.ACTION_ACKNOWLEDGE),
        )
        assertEquals(0L, HaruBubbleStore(context).step())

        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context,
                    HaruBubbleActionReceiver::class.java,
                ),
                0,
            )
        assertFalse(info.exported)
    }

    @Test
    fun widgetReceiverIsExportedForLauncherHosts() {
        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context,
                    HaruBubbleWidgetProvider::class.java,
                ),
                PackageManager.GET_META_DATA,
            )

        assertTrue(info.exported)
        assertNotEquals(
            0,
            info.metaData.getInt("android.appwidget.provider"),
        )
    }
}
