package io.haru.assistant.security

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.haru.assistant.MainActivity
import io.haru.assistant.companion.ReminderBootReceiver
import io.haru.assistant.companion.ReminderReceiver
import io.haru.assistant.lockscreen.HaruLockScreenService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HaruSecurityManifestTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun backupIsDisabled() {
        val info =
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )

        assertFalse(
            info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0
        )
    }

    @Test
    fun onlyLauncherActivityIsExportedAmongReviewedComponents() {
        val packageManager = context.packageManager

        val main =
            packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.GET_META_DATA,
            )
        val reminder =
            packageManager.getReceiverInfo(
                ComponentName(context, ReminderReceiver::class.java),
                PackageManager.GET_META_DATA,
            )
        val boot =
            packageManager.getReceiverInfo(
                ComponentName(context, ReminderBootReceiver::class.java),
                PackageManager.GET_META_DATA,
            )
        val lock =
            packageManager.getServiceInfo(
                ComponentName(context, HaruLockScreenService::class.java),
                PackageManager.GET_META_DATA,
            )

        assertTrue(main.exported)
        assertFalse(reminder.exported)
        assertFalse(boot.exported)
        assertFalse(lock.exported)
    }
}
