package io.haru.assistant.ui

import android.content.pm.PackageManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruLauncherIconManifestTest {
    @Test
    fun launcherIconUsesMipmapResourceAndResolves() {
        val context = RuntimeEnvironment.getApplication()
        val info =
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )

        val resourceName = context.resources.getResourceName(info.icon)
        assertTrue(resourceName.endsWith(":mipmap/ic_launcher"))
        assertNotNull(context.getDrawable(info.icon))
    }
}
