package com.gabbzin.pontual.calc

import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.WorkSession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

fun localDateOf(instant: Instant, s: Settings): LocalDate =
    instant.atZone(s.zoneId).toLocalDate()

fun punchWindow(date: LocalDate, s: Settings): ClosedRange<Instant> {
    val tol = Duration.ofMinutes(s.toleranceMinutes.toLong())
    val start = date.atTime(s.windowStart).atZone(s.zoneId).toInstant().minus(tol)
    val end = date.atTime(s.windowEnd).atZone(s.zoneId).toInstant().plus(tol)
    return start..end
}

fun isWithinWindow(instant: Instant, s: Settings): Boolean =
    instant in punchWindow(localDateOf(instant, s), s)

fun isForgotten(open: WorkSession, now: Instant, s: Settings): Boolean {
    val startDate = localDateOf(open.startedAt, s)
    return startDate < localDateOf(now, s) || now > punchWindow(startDate, s).endInclusive
}

fun effectiveEnd(session: WorkSession, now: Instant, s: Settings): Instant =
    session.endedAt ?: minOf(now, punchWindow(localDateOf(session.startedAt, s), s).endInclusive)
