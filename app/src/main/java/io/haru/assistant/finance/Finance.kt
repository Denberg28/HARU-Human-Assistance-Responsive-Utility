package io.haru.assistant.finance

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

enum class FinanceType(val label: String) { EXPENSE("Expense"), INCOME("Income") }

data class FinanceEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate = LocalDate.now(),
    val type: FinanceType = FinanceType.EXPENSE,
    val cents: Long,
    val category: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(id.isNotBlank())
        require(cents in 1..MAX_CENTS)
        require(category.isNotBlank() && category.length <= 32 && category == category.trim())
        require(note.length <= 200)
    }
    companion object { const val MAX_CENTS = 9_999_999_999L }
}

data class FinanceGroup(val type: FinanceType, val category: String, val entries: List<FinanceEntry>) {
    val id: String get() = "${type.name}:${category.lowercase(Locale.ROOT)}"
    val cents: Long get() = entries.sumOf { it.cents }
}

data class FinanceSummary(val income: Long, val expenses: Long) {
    val net: Long get() = income - expenses
}

object Finance {
    /** Exact centavos; never round or accept scientific notation or negative entries. */
    fun parseAmount(text: String): Long? {
        val value = text.trim()
        if (!Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(value)) return null
        return runCatching { BigDecimal(value).movePointRight(2).longValueExact() }
            .getOrNull()?.takeIf { it in 1..FinanceEntry.MAX_CENTS }
    }

    fun amountText(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
        .format(BigDecimal.valueOf(cents, 2))

    fun filter(entries: List<FinanceEntry>, month: YearMonth?): List<FinanceEntry> = entries
        .filter { month == null || YearMonth.from(it.date) == month }
        .sortedWith(compareByDescending<FinanceEntry> { it.date }.thenByDescending { it.createdAt }.thenBy { it.id })

    fun summary(entries: List<FinanceEntry>) = FinanceSummary(
        entries.filter { it.type == FinanceType.INCOME }.sumOf { it.cents },
        entries.filter { it.type == FinanceType.EXPENSE }.sumOf { it.cents },
    )

    fun groups(entries: List<FinanceEntry>): List<FinanceGroup> = entries
        .groupBy { it.type to it.category.lowercase(Locale.ROOT) }
        .map { (key, values) -> FinanceGroup(key.first, values.first().category, values) }
        .sortedWith(compareBy<FinanceGroup> { it.type.ordinal }.thenByDescending { it.cents }.thenBy { it.category })

    fun categories(type: FinanceType): List<String> = if (type == FinanceType.EXPENSE)
        listOf("Food", "Transport", "Bills", "Shopping", "Health", "Projects", "Other")
    else listOf("Salary", "Business", "Freelance", "Gifts", "Other")
}
