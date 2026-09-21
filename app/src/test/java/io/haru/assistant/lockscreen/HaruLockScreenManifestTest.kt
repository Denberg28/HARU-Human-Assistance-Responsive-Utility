package io.haru.assistant.lockscreen

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import io.haru.assistant.companion.ReminderReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruLockScreenManifestTest {

    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun wallpaperServiceIsRegisteredWithSystemPermission() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(
                    context,
                    HaruLockScreenWallpaperService::class.java,
                ),
                PackageManager.GET_META_DATA,
            )

        assertTrue(info.exported)
        assertEquals(
            Manifest.permission.BIND_WALLPAPER,
            info.permission,
        )
        assertNotEquals(
            0,
            info.metaData.getInt(
                "android.service.wallpaper"
            ),
        )
    }

    @Test
    fun reminderReceiverStillExistsAfterWidgetRetirement() {
        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(
                    context,
                    ReminderReceiver::class.java,
                ),
                0,
            )

        assertFalse(info.exported)
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
