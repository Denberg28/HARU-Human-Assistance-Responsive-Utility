package io.haru.assistant.lockscreen

import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruLockScreenManifestTest {

    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun interactiveBarIsPrivateAndUsesDedicatedTheme() {
        val info =
            context.packageManager.getActivityInfo(
                ComponentName(
                    context,
                    HaruLockScreenActivity::class.java,
                ),
                PackageManager.GET_META_DATA,
            )

        assertFalse(info.exported)
        assertEquals("io.haru.assistant.pet", info.taskAffinity)
        assertFalse(info.themeResource == 0)
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun retiredLiveWallpaperServiceIsNotRegistered() {
        context.packageManager.getServiceInfo(
            ComponentName(
                context.packageName,
                "io.haru.assistant.lockscreen.HaruLockScreenWallpaperService",
            ),
            0,
        )
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun retiredHomeWidgetReceiverIsNotRegistered() {
        context.packageManager.getReceiverInfo(
            ComponentName(
                context.packageName,
                "io.haru.assistant.companion.HaruBubbleWidgetProvider",
            ),
            0,
        )
    }
}
