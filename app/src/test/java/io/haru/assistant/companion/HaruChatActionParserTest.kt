package io.haru.assistant.companion

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruChatActionParserTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val now =
        LocalDateTime.of(2026, 9, 24, 18, 45)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun naturalTaskRequestIsCapturedLocally() {
        val result =
            HaruChatActionParser.parse(
                "Could you please add inspect rover steering to my task list?",
                now,
                zone,
            )

        assertEquals(
            HaruChatActionResult.AddTask("inspect rover steering"),
            result,
        )
    }

    @Test
    fun relativeReminderGetsExactFutureTime() {
        val result =
            HaruChatActionParser.parse(
                "please remind me in 30 minutes to check the battery",
                now,
                zone,
            )

        val reminder = result as HaruChatActionResult.SetReminder
        assertEquals("check the battery", reminder.text)
        assertEquals(now + 30L * 60_000L, reminder.dueAt)
    }

    @Test
    fun tomorrowReminderUsesDeviceTimezone() {
        val result =
            HaruChatActionParser.parse(
                "remind me tomorrow at 8 AM to call Mom",
                now,
                zone,
            )

        val reminder = result as HaruChatActionResult.SetReminder
        val expected =
            LocalDateTime.of(2026, 9, 25, 8, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        assertEquals("call Mom", reminder.text)
        assertEquals(expected, reminder.dueAt)
    }

    @Test
    fun trailingTimeReminderIsSupported() {
        val result =
            HaruChatActionParser.parse(
                "remind me to check the printer at 8:30 PM",
                now,
                zone,
            )

        val reminder = result as HaruChatActionResult.SetReminder
        val expected =
            LocalDateTime.of(2026, 9, 24, 20, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        assertEquals(expected, reminder.dueAt)
    }

    @Test
    fun pastUnqualifiedClockRollsToTomorrow() {
        val result =
            HaruChatActionParser.parse(
                "set a reminder at 6 PM to inspect the charger",
                now,
                zone,
            )

        val reminder = result as HaruChatActionResult.SetReminder
        val expected =
            LocalDateTime.of(2026, 9, 25, 18, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        assertEquals(expected, reminder.dueAt)
    }

    @Test
    fun explicitPastTodayAsksForCorrection() {
        val result =
            HaruChatActionParser.parse(
                "remind me today at 5 PM to test",
                now,
                zone,
            )

        assertTrue(result is HaruChatActionResult.Clarify)
    }

    @Test
    fun reminderWithoutTimeAsksForTimeInsteadOfPretending() {
        val result =
            HaruChatActionParser.parse(
                "remind me to call the supplier",
                now,
                zone,
            )

        assertTrue(result is HaruChatActionResult.Clarify)
    }

    @Test
    fun normalConversationDoesNotCreateActions() {
        assertEquals(
            HaruChatActionResult.None,
            HaruChatActionParser.parse(
                "What do you think about my rover steering?",
                now,
                zone,
            ),
        )
    }
}
