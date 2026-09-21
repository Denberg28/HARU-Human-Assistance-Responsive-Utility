package io.haru.assistant.companion

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class ReminderDeliveryTest {
    @Before fun setClock() { android.os.SystemClock.setCurrentTimeMillis(1_800_000_000_000L) }
    private val context get() = RuntimeEnvironment.getApplication()
    private val store get() = AndroidCompanionStore(context)
    private val notifications get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun due() = store.addReminder("Saved reminder", System.currentTimeMillis() - 1_000)
    private fun deliver(id: String) = ReminderReceiver().onReceive(context,
        Intent(context, ReminderReceiver::class.java).putExtra("reminder_id", id)
            .putExtra("reminder_text", "Untrusted stale text"))
    private fun permit() {
        shadowOf(context).grantPermissions("android.permission.POST_NOTIFICATIONS")
        shadowOf(notifications).setNotificationsEnabled(true)
    }

    @Test fun deletedReminderDoesNotNotify() {
        permit()
        val reminder = due()
        store.removeReminder(reminder.id)
        deliver(reminder.id)
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test fun deliveryUsesStoredTextAndDoesNotRepeat() {
        permit()
        val reminder = due()
        deliver(reminder.id)
        assertEquals("Saved reminder", shadowOf(notifications).allNotifications.single()
            .extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(store.load().reminders.isEmpty())
        notifications.cancelAll()
        deliver(reminder.id)
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test fun blockedNotificationsRetainReminderForRetry() {
        permit()
        val reminder = due()
        shadowOf(notifications).setNotificationsEnabled(false)
        deliver(reminder.id)
        assertEquals(1, store.load().reminders.size)
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test fun mutedChannelRetainsReminder() {
        permit()
        notifications.createNotificationChannel(NotificationChannel(
            ReminderReceiver.CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_NONE))
        val reminder = due()
        deliver(reminder.id)
        assertEquals(1, store.load().reminders.size)
    }

    @Test fun hashCollisionDoesNotReplaceAnotherReminderAlarm() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val now = System.currentTimeMillis()
        ReminderScheduler.schedule(context, CompanionReminder("Aa", "First", now + 60_000))
        ReminderScheduler.schedule(context, CompanionReminder("BB", "Second", now + 120_000))
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertEquals(2, alarms.scheduledAlarms.size)
        ReminderScheduler.cancel(context, "Aa")
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test fun rebootReschedulesMissedReminder() {
        due()
        ReminderBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertEquals(1, alarms.scheduledAlarms.size)
    }
}
