package io.haru.assistant.finance

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class FinanceTest {
    private val date = LocalDate.of(2026, 11, 7)
    private fun entry(amount: String, type: FinanceType = FinanceType.EXPENSE, category: String = "Food", day: LocalDate = date) =
        FinanceEntry(date = day, type = type, cents = requireNotNull(Finance.parseAmount(amount)), category = category)

    @Test fun moneyUsesExactCentavosAndRejectsAmbiguousOrInvalidInput() {
        assertEquals(10L, Finance.parseAmount("0.10"))
        assertEquals(1001L, Finance.parseAmount(" 10.01 "))
        assertEquals(FinanceEntry.MAX_CENTS, Finance.parseAmount("99999999.99"))
        listOf("", "0", "-1", "1.001", "1e5", "NaN", "1,000", "100000000", "1.2.3").forEach {
            assertNull("Must reject $it", Finance.parseAmount(it))
        }
        assertEquals("0.01", Finance.amountText(1))
    }

    @Test fun monthFilteringAndCategoryGroupsDoNotMixIncomeWithSpending() {
        val entries = listOf(entry("0.10"), entry("0.20", category = "food"),
            entry("20.00", FinanceType.INCOME, "Food"), entry("99", day = date.minusMonths(1)))
        val november = Finance.filter(entries, YearMonth.of(2026, 11))
        assertEquals(FinanceSummary(2000, 30), Finance.summary(november))
        assertEquals(1970L, Finance.summary(november).net)
        assertEquals(2, Finance.groups(november).size)
        assertEquals(30L, Finance.groups(november).first().cents)
        assertEquals(4, Finance.filter(entries, null).size)
        assertEquals(FinanceSummary(0, 0), Finance.summary(emptyList()))
        assertEquals(-30L, Finance.summary(november.filter { it.type == FinanceType.EXPENSE }).net)
    }

    @Test fun monthBoundariesAndNewestEntryOrderAreStable() {
        val lastYear = entry("1", day = LocalDate.of(2025, 12, 31))
        val first = entry("2", day = LocalDate.of(2026, 1, 1)).copy(createdAt = 1)
        val latest = first.copy(id = "newer", createdAt = 2)
        assertEquals(listOf(latest, first), Finance.filter(listOf(lastYear, first, latest), YearMonth.of(2026, 1)))
        assertEquals(listOf(lastYear), Finance.filter(listOf(lastYear, first), YearMonth.of(2025, 12)))
    }
}
