package io.haru.assistant.journal

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class JournalTimelineTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun entry(date: String, star: Boolean = false) = JournalEntry(LocalDate.parse(date), date, star)

    @Test fun ageThresholdsRollUpAndExpandInLevels() {
        val nodes = JournalTimeline.build(listOf(entry("2026-10-29"), entry("2026-10-23"),
            entry("2026-10-22"), entry("2026-10-02"), entry("2026-10-01"), entry("2025-10-29", true)), today)
        assertEquals(listOf(JournalLevel.DAY, JournalLevel.DAY, JournalLevel.WEEK, JournalLevel.WEEK, JournalLevel.MONTH, JournalLevel.YEAR), nodes.map { it.level })
        val year = nodes.last()
        assertEquals(1, year.stars)
        assertEquals(JournalLevel.MONTH, year.children.single().level)
        assertEquals(JournalLevel.WEEK, year.children.single().children.single().level)
        assertEquals(JournalLevel.DAY, year.children.single().children.single().children.single().level)
        assertEquals(nodes.size, JournalTimeline.rows(nodes, emptySet()).size)
        assertEquals(nodes.size + 1, JournalTimeline.rows(nodes, setOf(year.id)).size)
    }
    @Test fun denseArchiveKeepsEveryDateOnceAcrossLeapDaysAndCrossingWeeks() {
        val dates = (0L..1500L).map { today.minusDays(it) }
        val nodes = JournalTimeline.build(dates.map { JournalEntry(it, "day", it.dayOfMonth == 1) }, today)
        fun allNodes(nodes: List<JournalNode>): List<JournalNode> = nodes.flatMap { listOf(it) + allNodes(it.children) }
        val all = allNodes(nodes)
        val expanded = JournalTimeline.rows(nodes, all.map { it.id }.toSet())
        val leaves = expanded.filter { it.node.level == JournalLevel.DAY }.map { it.node.entries.single().date }
        assertEquals(dates, leaves)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(leaves.contains(LocalDate.of(2024, 2, 29)))
        assertEquals(dates.count { it.dayOfMonth == 1 }, nodes.sumOf { it.stars })
    }
    @Test fun filteringAndRebuildKeepGroupIdentityAndCollapsedChildState() {
        val entries = listOf(entry("2024-05-31", true), entry("2024-05-30"), entry("2024-06-01", true))
        val full = JournalTimeline.build(entries, today)
        val starred = JournalTimeline.build(entries.filter { it.memorable }, today)
        assertEquals(full.single().id, starred.single().id)
        val month = full.single().children.last()
        val childExpansion = setOf(month.id)
        assertEquals(1, JournalTimeline.rows(full, childExpansion).size)
        assertTrue(JournalTimeline.rows(full, childExpansion + full.single().id).any { it.node.level == JournalLevel.WEEK })
    }
}
