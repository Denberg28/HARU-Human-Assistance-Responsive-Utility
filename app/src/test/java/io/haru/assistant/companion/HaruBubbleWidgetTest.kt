package io.haru.assistant.companion

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import android.widget.TextView
import io.haru.assistant.R
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun reset() {
        context
            .getSharedPreferences(
                "haru_home_widget",
                android.content.Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()
        HaruCheckerStore(context).setEnabled(true)
    }

    private fun addWidget() =
        shadowOf(manager).createWidget(
            HaruBubbleWidgetProvider::class.java,
            R.layout.haru_bubble_widget,
        )

    private fun view(id: Int) =
        shadowOf(manager).getViewFor(id)

    @Test
    fun placementRendersCompactInteractiveBar() {
        val id = addWidget()

        assertEquals(
            1,
            HaruBubbleWidgetProvider.installedCount(context),
        )
        assertTrue(
            view(id)
                .findViewById<TextView>(
                    R.id.haru_bubble_line
                )
                .text
                .isNotBlank()
        )
        assertEquals(
            View.VISIBLE,
            view(id)
                .findViewById<View>(
                    R.id.haru_bubble_content
                )
                .visibility,
        )
    }

    @Test
    fun barTapAcknowledgesWithoutOpeningActivity() {
        val id = addWidget()
        val before =
            view(id)
                .findViewById<TextView>(
                    R.id.haru_bubble_line
                )
                .text
                .toString()

        view(id)
            .findViewById<View>(
                R.id.haru_bubble_content
            )
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            view(id)
                .findViewById<TextView>(
                    R.id.haru_bubble_line
                )
                .text
                .toString()
                .contains("~")
        )
        assertTrue(
            shadowOf(context).nextStartedActivity == null
        )

        shadowOf(Looper.getMainLooper()).idleFor(
            HaruIdlePolicy.ACKNOWLEDGEMENT_MS,
            TimeUnit.MILLISECONDS,
        )

        assertEquals(
            1L,
            HaruHomeWidgetStore(context).step(),
        )
        assertNotEquals(
            before,
            view(id)
                .findViewById<TextView>(
                    R.id.haru_bubble_line
                )
                .text
                .toString(),
        )
    }

    @Test
    fun pausePersistsAcrossRefreshAndBlocksTap() {
        val id = addWidget()
        HaruCheckerStore(context).setEnabled(false)
        HaruBubbleWidgetProvider.refreshAll(context)

        assertEquals(
            View.GONE,
            view(id)
                .findViewById<View>(
                    R.id.haru_bubble_content
                )
                .visibility,
        )
        assertEquals(
            View.VISIBLE,
            view(id)
                .findViewById<View>(
                    R.id.haru_bubble_paused
                )
                .visibility,
        )

        HaruBubbleActionReceiver().onReceive(
            context,
            Intent(
                HaruBubbleWidgetProvider
                    .ACTION_ACKNOWLEDGE
            ),
        )
        shadowOf(Looper.getMainLooper()).idleFor(
            HaruIdlePolicy.ACKNOWLEDGEMENT_MS,
            TimeUnit.MILLISECONDS,
        )
        assertEquals(
            0L,
            HaruHomeWidgetStore(context).step(),
        )
    }

    @Test
    fun privateActionReceiverAndPublicWidgetHostAreSeparated() {
        addWidget()

        val actionInfo =
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context,
                    HaruBubbleActionReceiver::class.java,
                ),
                0,
            )
        assertFalse(actionInfo.exported)

        val widgetInfo =
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context,
                    HaruBubbleWidgetProvider::class.java,
                ),
                PackageManager.GET_META_DATA,
            )
        assertTrue(widgetInfo.exported)
        assertNotEquals(
            0,
            widgetInfo.metaData.getInt(
                "android.appwidget.provider"
            ),
        )
    }
}
