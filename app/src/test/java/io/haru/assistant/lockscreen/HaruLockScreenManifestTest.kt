package io.haru.assistant.lockscreen

import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruLockScreenManifestTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun legacyLockBarActivityIsNotRegistered() {
        context.packageManager.getActivityInfo(
            ComponentName(
                context.packageName,
                "io.haru.assistant.lockscreen.HaruLockScreenActivity",
            ),
            PackageManager.GET_META_DATA,
        )
    }

    @Test
    fun lockScreenRuntimeServiceIsPrivateAndSpecialUse() {
        val info =
            context.packageManager.getServiceInfo(
                ComponentName(
                    context,
                    HaruLockScreenService::class.java,
                ),
                PackageManager.GET_META_DATA,
            )

        assertFalse(info.exported)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            assertFalse(info.foregroundServiceType == 0)
        }
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun homeWidgetIsNotRegistered() {
        context.packageManager.getReceiverInfo(
            ComponentName(
                context.packageName,
                "io.haru.assistant.companion.HaruBubbleWidgetProvider",
            ),
            0,
        )
    }
}
