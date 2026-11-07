package io.haru.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.haru.assistant.finance.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    @Test fun reportedAmountsProduceTwoBarsOnTheSameScale() {
        compose.setContent { MaterialTheme { FinanceCashFlowChart(FinanceSummary(70000, 1500000)) } }
        compose.onNodeWithText("Income vs expenses").assertIsDisplayed()
        compose.onNodeWithText(Finance.money(70000)).assertIsDisplayed()
        compose.onNodeWithText(Finance.money(1500000)).assertIsDisplayed()
        compose.onNodeWithText("Expenses exceed income by ${Finance.money(1430000)}").assertIsDisplayed()
        val income = compose.onNodeWithTag("finance-bar-Income").fetchSemanticsNode().boundsInRoot
        val expense = compose.onNodeWithTag("finance-bar-Expenses").fetchSemanticsNode().boundsInRoot
        assertTrue(income.height > 0f)
        assertEquals(700f / 15000f, income.height / expense.height, 0.01f)
        assertEquals(income.bottom, expense.bottom, 0.1f)
    }

    @Test fun expenseOnlyRecordsKeepARealZeroIncomeAndReadableExpenseBar() {
        compose.setContent { MaterialTheme { FinanceCashFlowChart(FinanceSummary(0, 1500000)) } }
        compose.onNodeWithText(Finance.money(0)).assertIsDisplayed()
        compose.onNodeWithText("Expenses exceed income by ${Finance.money(1500000)}").assertIsDisplayed()
        val income = compose.onNodeWithTag("finance-bar-Income").fetchSemanticsNode().boundsInRoot
        val expense = compose.onNodeWithTag("finance-bar-Expenses").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, income.height, 0.1f)
        assertTrue(expense.height > 0f)
    }

    @Test fun emptyComparisonExplainsHowToPopulateTheGraph() {
        compose.setContent { MaterialTheme { FinanceCashFlowChart(FinanceSummary(0, 0)) } }
        compose.onNodeWithText("Add an income or expense to see your graph.").assertIsDisplayed()
        compose.onNodeWithTag("finance-plot").assertDoesNotExist()
    }

    @Test fun monthStartsAtTheLeftEdgeAndNavigationStillWorks() {
        compose.setContent { MaterialTheme { FinanceContent(emptyList(), true, false, null, {}, {}, {}) } }
        val header = compose.onNodeWithTag("finance-period-header").fetchSemanticsNode().boundsInRoot
        val month = compose.onNodeWithTag("finance-period-label").fetchSemanticsNode().boundsInRoot
        assertEquals(header.left, month.left, 0.1f)
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month").performClick()
        val previous = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
        compose.onNodeWithText(previous).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").assertIsEnabled()
    }
}
