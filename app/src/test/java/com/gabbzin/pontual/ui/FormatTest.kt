package com.gabbzin.pontual.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FormatTest {
    private val sp = ZoneId.of("America/Sao_Paulo")

    @Test
    fun duration_unsigned() {
        assertEquals("4h12", formatDuration(Duration.ofMinutes(252)))
        assertEquals("6h00", formatDuration(Duration.ofHours(6)))
    }

    @Test
    fun duration_signed() {
        assertEquals("−1h48", formatDuration(Duration.ofMinutes(-108), signed = true))
        assertEquals("+0h30", formatDuration(Duration.ofMinutes(30), signed = true))
        assertEquals("0h00", formatDuration(Duration.ZERO, signed = true))
    }

    @Test
    fun duration_truncatesSeconds() {
        assertEquals("0h00", formatDuration(Duration.ofSeconds(59)))
        assertEquals("0h00", formatDuration(Duration.ofSeconds(-59), signed = true))
    }

    @Test
    fun time_usesZone() {
        assertEquals("08:02", formatTime(Instant.parse("2026-11-13T11:02:00Z"), sp))
    }

    @Test
    fun timer_hoursAndMinutes() {
        assertEquals("02:14", formatTimer(Duration.ofMinutes(134).plusSeconds(30)))
    }
}
