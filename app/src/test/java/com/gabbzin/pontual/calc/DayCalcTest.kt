package com.gabbzin.pontual.calc

import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

private val settings = Settings(balanceStart = LocalDate.of(2026, 11, 2))
private val friday = LocalDate.of(2026, 11, 13)

private fun at(local: String): Instant =
    LocalDateTime.parse(local).atZone(settings.zoneId).toInstant()

private fun session(start: String, end: String?) = WorkSession(
    id = "s-$start",
    startedAt = at(start),
    endedAt = end?.let { at(it) },
    startSource = Source.APP,
    endSource = if (end == null) null else Source.APP,
    note = null,
    createdAt = at(start),
)

class DayCalcTest {

    @Test
    fun worked_sumsClosedSessions() {
        val sessions = listOf(
            session("2026-11-13T08:02:00", "2026-11-13T12:00:00"),
            session("2026-11-13T13:01:00", "2026-11-13T15:00:00"),
        )
        val day = summarizeDays(friday, friday, sessions, emptyList(), at("2026-11-13T20:00:00"), settings).single()
        assertEquals(Duration.ofMinutes(357), day.worked)
    }

    @Test
    fun worked_openSessionCountsUntilNow() {
        val sessions = listOf(session("2026-11-13T13:00:00", null))
        val day = summarizeDays(friday, friday, sessions, emptyList(), at("2026-11-13T14:30:00"), settings).single()
        assertEquals(Duration.ofMinutes(90), day.worked)
    }

    @Test
    fun expected_workday_is6h() {
        assertEquals(
            Duration.ofMinutes(360),
            expectedFor(friday, emptySet(), friday, settings),
        )
    }

    @Test
    fun expected_saturday_isZero() {
        assertEquals(
            Duration.ZERO,
            expectedFor(LocalDate.of(2026, 11, 14), emptySet(), friday, settings),
        )
    }

    @Test
    fun expected_dayOff_isZero() {
        assertEquals(
            Duration.ZERO,
            expectedFor(friday, setOf(friday), friday, settings),
        )
    }

    @Test
    fun expected_beforeBalanceStart_isZero() {
        assertEquals(
            Duration.ZERO,
            expectedFor(LocalDate.of(2026, 10, 30), emptySet(), friday, settings),
        )
    }

    @Test
    fun expected_futureDay_isZero() {
        assertEquals(
            Duration.ZERO,
            expectedFor(LocalDate.of(2026, 11, 16), emptySet(), LocalDate.of(2026, 11, 13), settings),
        )
    }

    @Test
    fun summarize_withoutBalanceStart_expectsZero() {
        val noStart = settings.copy(balanceStart = null)
        val days = summarizeDays(
            from = LocalDate.of(2026, 11, 9),
            to = LocalDate.of(2026, 11, 15),
            sessions = emptyList(),
            daysOff = emptyList(),
            now = at("2026-11-16T10:00:00"),
            s = noStart,
        )
        assertEquals(7, days.size)
        assertTrue(days.all { it.expected == Duration.ZERO })
    }

    @Test
    fun summarize_weekend_workCountsAsExtra() {
        val saturday = LocalDate.of(2026, 11, 14)
        val sessions = listOf(session("2026-11-14T10:00:00", "2026-11-14T12:00:00"))
        val day = summarizeDays(saturday, saturday, sessions, emptyList(), at("2026-11-16T10:00:00"), settings).single()
        assertEquals(Duration.ofHours(2), day.balance)
    }

    @Test
    fun summarize_marksDayOffKind() {
        val daysOff = listOf(DayOff(friday, DayOffKind.HOLIDAY, null))
        val day = summarizeDays(friday, friday, emptyList(), daysOff, at("2026-11-13T20:00:00"), settings).single()
        assertEquals(DayOffKind.HOLIDAY, day.dayOff)
        assertEquals(Duration.ZERO, day.expected)
    }

    @Test
    fun totalBalance_sumsDays() {
        val days = listOf(
            DaySummary(friday, worked = Duration.ZERO, expected = Duration.ofMinutes(108), dayOff = null),
            DaySummary(friday.plusDays(1), worked = Duration.ofMinutes(30), expected = Duration.ZERO, dayOff = null),
        )
        assertEquals(Duration.ofMinutes(-78), totalBalance(days))
    }
}
