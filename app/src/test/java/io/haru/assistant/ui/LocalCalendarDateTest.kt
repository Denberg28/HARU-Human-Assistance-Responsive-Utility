package io.haru.assistant.ui

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class LocalCalendarDateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clockAndZoneChangesRefreshTheVisibleDateWithoutReopening() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12"))
            compose.setContent { Text(rememberLocalCalendarDate().toString()) }
            compose.onNodeWithText(LocalDate.now().toString()).assertIsDisplayed()
            compose.runOnIdle {
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
                RuntimeEnvironment.getApplication().sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            }
            compose.waitForIdle()
            compose.onNodeWithText(LocalDate.now().toString()).assertIsDisplayed()
        } finally { TimeZone.setDefault(original) }
    }
}
