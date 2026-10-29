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
}

data class JournalRow(val node: JournalNode, val depth: Int)

/** Calendar buckets keep real months, leap days, and year boundaries intact.
 * A crossing week is clipped to its parent month, so each day appears once.
 * Recent seven dates stay open; older buckets roll up at 7 days / 4 weeks / 12 months.
 */
object JournalTimeline {
    fun build(entries: List<JournalEntry>, today: LocalDate): List<JournalNode> {
        val sorted = entries.sortedByDescending { it.date }
        val recent = sorted.filter { it.date > today.minusDays(7) }.map(::day)
        val weekly = sorted.filter { it.date <= today.minusDays(7) && it.date > today.minusWeeks(4) }
            .groupBy { YearMonth.from(it.date) }.values.flatMap { weeks(it) }
        val monthly = sorted.filter { it.date <= today.minusWeeks(4) && it.date > today.minusMonths(12) }
            .groupBy { YearMonth.from(it.date) }.values.map { month(it) }
        val yearly = sorted.filter { it.date <= today.minusMonths(12) }
            .groupBy { it.date.year }.values.map(::year)
        return (recent + weekly + monthly + yearly).sortedByDescending { it.entries.first().date }
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
        entries.groupBy { YearMonth.from(it.date) }.values.map { month(it, "year:${entries.first().date.year}/") },
    )
    private fun month(entries: List<JournalEntry>, prefix: String = ""): JournalNode = JournalNode(
        "${prefix}month:${YearMonth.from(entries.first().date)}",
        entries.first().date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
        JournalLevel.MONTH, entries, weeks(entries, "${prefix}month:${YearMonth.from(entries.first().date)}/"),
    )
    private fun weeks(entries: List<JournalEntry>, prefix: String = ""): List<JournalNode> = entries
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (monday, days) ->
            val month = YearMonth.from(days.first().date)
            val start = maxOf(monday, month.atDay(1))
            val end = minOf(monday.plusDays(6), month.atEndOfMonth())
            val format = DateTimeFormatter.ofPattern("d MMM")
            JournalNode("${prefix}week:$month:$monday", "${start.format(format)} – ${end.format(format)}",
                JournalLevel.WEEK, days, days.map(::day))
        }
}
