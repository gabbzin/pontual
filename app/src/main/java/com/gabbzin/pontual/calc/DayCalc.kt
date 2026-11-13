package com.gabbzin.pontual.calc

import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.WorkSession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class DaySummary(
    val date: LocalDate,
    val worked: Duration,
    val expected: Duration,
    val dayOff: DayOffKind?,
) {
    val balance: Duration get() = worked - expected
}

fun expectedFor(date: LocalDate, dayOffDates: Set<LocalDate>, today: LocalDate, s: Settings): Duration {
    val start = s.balanceStart
    val isWorkday = date.dayOfWeek in s.workdays && date !in dayOffDates
    val inRange = start != null && !date.isBefore(start) && !date.isAfter(today)
    return if (isWorkday && inRange) Duration.ofMinutes(s.dailyMinutes.toLong()) else Duration.ZERO
}

fun summarizeDays(
    from: LocalDate,
    to: LocalDate,
    sessions: List<WorkSession>,
    daysOff: List<DayOff>,
    now: Instant,
    s: Settings,
): List<DaySummary> {
    val today = localDateOf(now, s)
    val dayOffDates = daysOff.map { it.date }.toSet()
    val sessionsByDate = sessions.groupBy { localDateOf(it.startedAt, s) }
    val kindByDate = daysOff.associate { it.date to it.kind }

    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .map { date ->
            val worked = sessionsByDate[date].orEmpty().fold(Duration.ZERO) { acc, session ->
                acc + Duration.between(session.startedAt, effectiveEnd(session, now, s))
            }
            DaySummary(
                date = date,
                worked = worked,
                expected = expectedFor(date, dayOffDates, today, s),
                dayOff = kindByDate[date],
            )
        }
        .toList()
}

fun totalBalance(days: List<DaySummary>): Duration =
    days.fold(Duration.ZERO) { acc, day -> acc + day.balance }
