package io.haru.assistant.lockscreen

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.view.WindowManager
import io.haru.assistant.companion.HaruCheckerStore
import io.haru.assistant.core.HaruFaces
import io.haru.assistant.core.HaruMood
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HaruLockScreenInteractionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun originalFacePackCoversSleepRunAndPetting() {
        assertEquals(HaruFaces.forMood(HaruMood.SLEEPY),
            HaruLockScreenScene.faceFor(HaruLockMotion.SLEEPING, "idle", "happy"))
        assertEquals(HaruFaces.forMood(HaruMood.HAPPY),
            HaruLockScreenScene.faceFor(HaruLockMotion.RUNNING, "idle", "happy"))
        assertEquals("happy", HaruLockScreenScene.faceFor(HaruLockMotion.DELIGHTED, "idle", "happy"))
        assertEquals("idle", HaruLockScreenScene.faceFor(HaruLockMotion.IDLE, "idle", "happy"))
    }

    @Test
    fun petAtNightOverridesSleepThenReturnsToSleepAndDebouncesDuplicateDelivery() {
        HaruCheckerStore(context).setEnabled(true)
        val scene = HaruLockScreenScene(context)
        assertTrue(scene.pet(0L))
        assertFalse(scene.pet(100L))
        assertEquals(HaruLockMotion.DELIGHTED,
            HaruLockScreenMotionPolicy.motion(3, 100, true, scene.delightedUntil))
        assertEquals(HaruLockMotion.SLEEPING,
            HaruLockScreenMotionPolicy.motion(3, scene.delightedUntil, true, scene.delightedUntil))
        HaruCheckerStore(context).setEnabled(false)
        assertFalse(scene.pet(5_000))
    }

    @Test
    fun fullBubbleIsTouchableAndDisabledCheckerClearsItsHitArea() {
        HaruCheckerStore(context).setEnabled(true)
        val scene = HaruLockScreenScene(context)
        val canvas = Canvas(Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888))
        scene.draw(canvas, 400, 800, 10_000, 0)
        assertTrue(scene.contains(200f, 552f))
        assertFalse(scene.contains(200f, 50f))
        HaruCheckerStore(context).setEnabled(false)
        scene.draw(canvas, 400, 800, 10_100, 0)
        assertFalse(scene.contains(200f, 552f))
    }

    @Test
    fun lockedSessionHandlesClickWithoutUnlockingAndStopsOnScreenOff() {
        HaruCheckerStore(context).setEnabled(true)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguard).setIsKeyguardLocked(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val controller = Robolectric.buildActivity(HaruLockScreenActivity::class.java).setup()
        val activity = controller.get()
        val view = activity.pet
        view.performClick()
        assertTrue(view.scene.delightedUntil > 0)
        assertTrue(keyguard.isKeyguardLocked)
        val flags = activity.window.attributes.flags
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        activity.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(view.active)
        controller.pause()
        controller.stop().destroy()
        val until = view.scene.delightedUntil
        view.performClick()
        assertEquals(until, view.scene.delightedUntil)
        assertFalse(view.active)
    }

    @Test
    fun sessionHasPrivateSeparateTaskAndCannotExposeMainActivityOnLockedBack() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, HaruLockScreenActivity::class.java), PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        assertEquals("io.haru.assistant.pet", info.taskAffinity)
        val controller = Robolectric.buildActivity(HaruLockScreenActivity::class.java).setup()
        controller.get().sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
