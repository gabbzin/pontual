package com.gabbzin.pontual.calc

import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

private val settings = Settings(balanceStart = LocalDate.of(2026, 11, 2))

private fun at(local: String): Instant =
    LocalDateTime.parse(local).atZone(settings.zoneId).toInstant()

private fun session(start: Instant, end: Instant?) = WorkSession(
    id = "s-$start",
    startedAt = start,
    endedAt = end,
    startSource = Source.APP,
    endSource = if (end == null) null else Source.APP,
    note = null,
    createdAt = start,
)

class WorkWindowTest {

    @Test
    fun window_acceptsBorders() {
        assertTrue(isWithinWindow(at("2026-11-13T07:55:00"), settings))
        assertTrue(isWithinWindow(at("2026-11-13T18:05:00"), settings))
    }

    @Test
    fun window_rejectsJustOutside() {
        assertFalse(isWithinWindow(at("2026-11-13T07:54:59"), settings))
        assertFalse(isWithinWindow(at("2026-11-13T18:05:01"), settings))
    }

    @Test
    fun window_usesSettingsZone_notClockZone() {
        assertTrue(isWithinWindow(Instant.parse("2026-11-13T10:56:00Z"), settings)) // 07:56 em SP
        assertFalse(isWithinWindow(Instant.parse("2026-11-13T21:06:00Z"), settings)) // 18:06 em SP
        assertEquals(
            LocalDate.of(2026, 11, 13),
            localDateOf(Instant.parse("2026-11-14T01:00:00Z"), settings),
        )
    }

    @Test
    fun forgotten_sameDayInsideWindow_false() {
        val open = session(start = at("2026-11-13T13:00:00"), end = null)
        assertFalse(isForgotten(open, now = at("2026-11-13T17:00:00"), s = settings))
    }

    @Test
    fun forgotten_afterWindowEnd_true() {
        val open = session(start = at("2026-11-13T13:00:00"), end = null)
        assertTrue(isForgotten(open, now = at("2026-11-13T18:06:00"), s = settings))
    }

    @Test
    fun forgotten_nextDay_true() {
        val open = session(start = at("2026-11-12T13:00:00"), end = null)
        assertTrue(isForgotten(open, now = at("2026-11-13T08:00:00"), s = settings))
    }

    @Test
    fun effectiveEnd_openSession_capsAtWindowEnd() {
        val open = session(start = at("2026-11-13T13:00:00"), end = null)
        assertEquals(at("2026-11-13T18:05:00"), effectiveEnd(open, now = at("2026-11-13T20:00:00"), s = settings))
        assertEquals(at("2026-11-13T15:00:00"), effectiveEnd(open, now = at("2026-11-13T15:00:00"), s = settings))
    }

    @Test
    fun effectiveEnd_openSessionStartedAfterWindow_isZeroLength() {
        val open = session(start = at("2026-11-13T19:00:00"), end = null)
        assertEquals(at("2026-11-13T19:00:00"), effectiveEnd(open, now = at("2026-11-13T20:00:00"), s = settings))
    }

    @Test
    fun effectiveEnd_closedSession_usesEndedAt() {
        val closed = session(start = at("2026-11-13T13:00:00"), end = at("2026-11-13T14:00:00"))
        assertEquals(at("2026-11-13T14:00:00"), effectiveEnd(closed, now = at("2026-11-13T20:00:00"), s = settings))
    }
}
