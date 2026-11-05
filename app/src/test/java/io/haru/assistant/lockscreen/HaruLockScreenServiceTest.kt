package io.haru.assistant.lockscreen

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import io.haru.assistant.companion.AndroidCompanionStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class HaruLockScreenServiceTest {
    @Test fun disablingLockScreenRemovesItsTaskNotification() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions("android.permission.POST_NOTIFICATIONS")
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .setIsKeyguardLocked(true)
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        AndroidCompanionStore(context).addTask("Newest task")
        val controller = Robolectric.buildService(HaruLockScreenService::class.java).create()
        controller.get().onStartCommand(Intent().setAction("io.haru.assistant.action.REFRESH_LOCK_SCREEN_HARU"), 0, 1)
        assertNotNull(shadowOf(notifications).getNotification(9110))
        controller.destroy()
        assertNull(shadowOf(notifications).getNotification(9110))
    }
}
