package io.haru.assistant.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters

data class JournalEntry(
    val date: LocalDate,
    val text: String = "",
    val memorable: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class JournalLevel { YEAR, MONTH, WEEK, DAY }

data class JournalNode(
    val id: String,
    val label: String,
    val level: JournalLevel,
    val entries: List<JournalEntry>,
    val children: List<JournalNode> = emptyList(),
) {
    val stars: Int get() = entries.count { it.memorable }
    val summary: String get() = "${entries.size} ${if (entries.size == 1) "day" else "days"} · ${level.name.lowercase().replaceFirstChar { it.uppercase() }}"
}

data class JournalRow(val node: JournalNode, val depth: Int)

/** Promote complete calendar buckets, never individual entries by their age.
 * This week stays daily; completed weeks collapse together. A whole month rolls up
 * once its final date is at least four weeks old, and a whole year after twelve months.
 * Weeks crossing a month boundary are clipped to that month and labelled accordingly,
 * so the year > month > week > day hierarchy contains every saved date exactly once.
 */
object JournalTimeline {
    fun build(entries: List<JournalEntry>, today: LocalDate): List<JournalNode> {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return entries.sortedByDescending { it.date }.groupBy { it.date.year }.flatMap { (yearValue, yearEntries) ->
            if (LocalDate.of(yearValue, 12, 31) <= today.minusMonths(12)) listOf(year(yearEntries))
            else yearEntries.groupBy { YearMonth.from(it.date) }.flatMap { (monthValue, monthEntries) ->
                if (monthValue.atEndOfMonth() <= today.minusWeeks(4)) listOf(month(monthEntries))
                else weeks(monthEntries).flatMap { week ->
                    // Use the full calendar week's end even when the month clips its label.
                    // A week in progress must not archive early on the last day of a month.
                    val end = week.entries.first().date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    if (end < weekStart) listOf(week) else week.children
                }
            }
        }
    }

    fun rows(nodes: List<JournalNode>, expanded: Set<String>): List<JournalRow> = buildList {
        fun visit(node: JournalNode, depth: Int) {
            add(JournalRow(node, depth))
            if (node.id in expanded) node.children.forEach { visit(it, depth + 1) }
        }
        nodes.forEach { visit(it, 0) }
    }

    private fun day(entry: JournalEntry) = JournalNode(
        "day:${entry.date}", entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        JournalLevel.DAY, listOf(entry),
    )
    private fun year(entries: List<JournalEntry>): JournalNode = JournalNode(
        "year:${entries.first().date.year}", entries.first().date.year.toString(),
        JournalLevel.YEAR, entries,
        entries.groupBy { YearMonth.from(it.date) }.values.map(::month),
    )
    private fun month(entries: List<JournalEntry>): JournalNode = JournalNode(
        "month:${YearMonth.from(entries.first().date)}",
        entries.first().date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
        JournalLevel.MONTH, entries, weeks(entries),
    )
    private fun weeks(entries: List<JournalEntry>): List<JournalNode> = entries
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (monday, days) ->
            val month = YearMonth.from(days.first().date)
            val start = maxOf(monday, month.atDay(1))
            val end = minOf(monday.plusDays(6), month.atEndOfMonth())
            val format = DateTimeFormatter.ofPattern("d MMM")
            JournalNode("week:$month:$monday", "${start.format(format)} – ${end.format(format)}",
                JournalLevel.WEEK, days, days.map(::day))
        }
}
