package io.haru.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.haru.assistant.finance.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class FinancePaneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun categoriesStartCollapsedAndExpandToEditableEntries() {
        val entry = FinanceEntry(cents = 12500, category = "Food", note = "Lunch")
        var edited: FinanceEntry? = null
        compose.setContent { MaterialTheme { FinanceContent(listOf(entry), true, false, null, {}, {}, { edited = it }) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Expense · 1 entry"))
        compose.onNodeWithText("Lunch").assertDoesNotExist()
        compose.onNodeWithText("Expense · 1 entry").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lunch"))
        compose.onNodeWithText("Lunch").performClick()
        compose.runOnIdle { assertEquals(entry, edited) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Collapse all"))
        compose.onNodeWithText("Collapse all").performClick()
        compose.onNodeWithText("Lunch").assertDoesNotExist()
    }

    @Test fun allTimeIncludesOlderEntriesAndChartHandlesEmptyExpenses() {
        val entry = FinanceEntry(date = LocalDate.now().minusYears(1), type = FinanceType.INCOME, cents = 99900, category = "Salary")
        compose.setContent { MaterialTheme { FinanceContent(listOf(entry), true, false, null, {}, {}, {}) } }
        compose.onNodeWithText("No expenses in this period").assertExists()
        compose.onNodeWithText("All").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Income · 1 entry"))
        compose.onNodeWithText("Income · 1 entry").assertIsDisplayed()
    }

    @Test fun invalidAmountsCannotSaveAndValidEntryHasExactAmountAndCategory() {
        var saved: FinanceEntry? = null
        compose.setContent { MaterialTheme { FinanceEditor(null, emptyList(), false, null, {}, { saved = it }, {}) } }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Amount (PHP)").performTextInput("1.005")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Amount (PHP)").performTextReplacement("100.25")
        compose.onNodeWithText("Category").performTextReplacement("Rover parts")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(10025L, saved?.cents); assertEquals("Rover parts", saved?.category) }
    }
}
