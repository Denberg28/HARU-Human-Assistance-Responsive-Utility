package io.haru.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.haru.assistant.journal.JournalEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class JournalArchiveTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-11-11")
    private val entries = listOf(
        JournalEntry(today, "Today"),
        JournalEntry(LocalDate.parse("2026-11-06"), "Friday note", true),
        JournalEntry(LocalDate.parse("2026-11-05"), "Thursday note"),
        JournalEntry(LocalDate.parse("2026-11-03"), "Tuesday note"),
    )

    @Test fun completedWeekExpandsEditsAndCollapsesAllItsDays() {
        var edited: LocalDate? = null
        compose.setContent { MaterialTheme {
            JournalArchive(entries, today, false, {}, true, false, "Saved", {}, { edited = it })
        } }
        compose.onNodeWithText("3 days · Week").assertIsDisplayed()
        compose.onNodeWithText("Friday note").assertDoesNotExist()
        compose.onNodeWithText("2 Nov – 8 Nov").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Thursday note"))
        compose.onNodeWithText("Thursday note").performClick()
        compose.runOnIdle { assertEquals(LocalDate.parse("2026-11-05"), edited) }
        compose.onNodeWithText("Collapse all").performClick()
        compose.onNodeWithText("Friday note").assertDoesNotExist()
        compose.onNodeWithText("Thursday note").assertDoesNotExist()
        compose.onNodeWithText("Tuesday note").assertDoesNotExist()
        compose.onNodeWithText("2 Nov – 8 Nov").assertIsDisplayed()
        compose.onNodeWithText("Collapse all").assertDoesNotExist()
    }

    @Test fun memorableFilterKeepsWeekIdentityAndRestoresExpansion() {
        compose.setContent { MaterialTheme {
            var memorable by remember { mutableStateOf(false) }
            JournalArchive(entries, today, memorable, { memorable = it }, true, false, "Saved", {}, {})
        } }
        compose.onNodeWithText("2 Nov – 8 Nov").performClick()
        compose.onNodeWithText("★ Memorable").performClick()
        compose.onNodeWithText("1 day · Week").assertIsDisplayed()
        compose.onNodeWithText("Friday note").assertIsDisplayed()
        compose.onNodeWithText("Thursday note").assertDoesNotExist()
        compose.onNodeWithText("★ Memorable").performClick()
        compose.onNodeWithText("3 days · Week").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Thursday note"))
        compose.onNodeWithText("Thursday note").assertIsDisplayed()
    }
}
