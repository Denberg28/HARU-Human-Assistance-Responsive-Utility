package io.haru.assistant.journal

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class JournalTimelineTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun entry(date: String, star: Boolean = false) = JournalEntry(LocalDate.parse(date), date, star)

    @Test fun completedCalendarBucketsRollUpAsAWholeAndExpandInLevels() {
        val nodes = JournalTimeline.build(listOf(entry("2026-10-29"), entry("2026-10-26"),
            entry("2026-10-25"), entry("2026-10-22"), entry("2026-10-01"),
            entry("2026-09-30"), entry("2024-12-31", true)), today)
        assertEquals(listOf(JournalLevel.DAY, JournalLevel.DAY, JournalLevel.WEEK,
            JournalLevel.WEEK, JournalLevel.MONTH, JournalLevel.YEAR), nodes.map { it.level })
        assertEquals(listOf(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-22")), nodes[2].entries.map { it.date })
        val year = nodes.last()
        assertEquals(1, year.stars)
        assertEquals(JournalLevel.MONTH, year.children.single().level)
        assertEquals(JournalLevel.WEEK, year.children.single().children.single().level)
        assertEquals(JournalLevel.DAY, year.children.single().children.single().children.single().level)
        assertEquals(nodes.size, JournalTimeline.rows(nodes, emptySet()).size)
        assertEquals(nodes.size + 1, JournalTimeline.rows(nodes, setOf(year.id)).size)
    }

    @Test fun screenshotNovemberEntriesBelongToOneCompletedWeek() {
        val nodes = JournalTimeline.build(listOf(entry("2026-11-11"), entry("2026-11-06"),
            entry("2026-11-05"), entry("2026-11-03")), LocalDate.parse("2026-11-11"))
        assertEquals(listOf(JournalLevel.DAY, JournalLevel.WEEK), nodes.map { it.level })
        val week = nodes.last()
        assertEquals("2 Nov – 8 Nov", week.label)
        assertEquals("3 days · Week", week.summary)
        assertEquals(3, week.children.size)
        assertEquals(2, JournalTimeline.rows(nodes, emptySet()).size)
        assertEquals(5, JournalTimeline.rows(nodes, setOf(week.id)).size)
    }

    @Test fun sundayStaysDailyAndMondayArchivesTheEntirePreviousWeek() {
        val entries = (2L..8L).map { entry("2026-11-${it.toString().padStart(2, '0')}") }
        assertTrue(JournalTimeline.build(entries, LocalDate.parse("2026-11-08")).all { it.level == JournalLevel.DAY })
        val next = JournalTimeline.build(entries, LocalDate.parse("2026-11-09")).single()
        assertEquals(JournalLevel.WEEK, next.level)
        assertEquals(7, next.entries.size)
    }

    @Test fun monthAndYearThresholdsDoNotSplitParents() {
        val monthEntries = listOf(entry("2026-10-01"), entry("2026-10-31"))
        val weeks = JournalTimeline.build(monthEntries, LocalDate.parse("2026-11-27"))
        assertTrue(weeks.all { it.level == JournalLevel.WEEK })
        val month = JournalTimeline.build(monthEntries, LocalDate.parse("2026-11-28")).single()
        assertEquals(JournalLevel.MONTH, month.level)
        assertEquals(2, month.entries.size)
        val yearEntries = listOf(entry("2025-01-01"), entry("2025-12-31"))
        assertTrue(JournalTimeline.build(yearEntries, LocalDate.parse("2026-12-30")).all { it.level == JournalLevel.MONTH })
        assertEquals(JournalLevel.YEAR, JournalTimeline.build(yearEntries, LocalDate.parse("2026-12-31")).single().level)
    }

    @Test fun crossingWeeksAreLabelledWithinTheirParentWithoutMissingDates() {
        val entries = listOf(entry("2026-10-31"), entry("2026-11-01"))
        assertTrue(JournalTimeline.build(entries, LocalDate.parse("2026-11-01")).all { it.level == JournalLevel.DAY })
        val weeks = JournalTimeline.build(entries, LocalDate.parse("2026-11-02"))
        assertEquals(listOf("1 Nov – 1 Nov", "26 Oct – 31 Oct"), weeks.map { it.label })
        assertEquals(listOf(1, 1), weeks.map { it.entries.size })
        assertEquals("1 day · Week", weeks.first().summary)
    }

    @Test fun bucketIdsStayStableAfterPromotionAndFiltering() {
        val entries = listOf(entry("2024-02-29", true), entry("2024-02-28"))
        val week = JournalTimeline.build(entries, LocalDate.parse("2024-03-04")).single()
        val month = JournalTimeline.build(entries, LocalDate.parse("2024-03-29")).single()
        val year = JournalTimeline.build(entries, LocalDate.parse("2026-01-01")).single()
        assertEquals(week.id, month.children.single().id)
        assertEquals(month.id, year.children.single().id)
        assertEquals(week.id, year.children.single().children.single().id)
        val starred = JournalTimeline.build(entries.filter { it.memorable }, LocalDate.parse("2024-03-04")).single()
        assertEquals(week.id, starred.id)
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
