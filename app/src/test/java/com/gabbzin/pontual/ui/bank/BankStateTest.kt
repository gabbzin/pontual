package com.gabbzin.pontual.ui.bank

import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class BankStateTest {

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

    /** "2026-11-13" + "08:00" em horário de São Paulo. */
    private fun spAt(date: String, time: String): Instant =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(sp).toInstant()

    private fun closed(id: String, date: String, start: String, end: String) = WorkSession(
        id = id,
        startedAt = spAt(date, start),
        endedAt = spAt(date, end),
        startSource = Source.APP,
        endSource = Source.APP,
        note = null,
        createdAt = spAt(date, start),
    )

    @Test
    fun accumulated_excludesToday() {
        // Quinta 12/11 teve 7h (+1h). Sexta 13/11 (hoje) teve 4h (−2h), que não entra no acumulado.
        val settings = Settings(
            internshipStart = LocalDate.of(2026, 5, 13),
            balanceStart = LocalDate.of(2026, 11, 12),
        )
        val sessions = listOf(
            closed("a", "2026-11-12", "08:00", "15:00"),
            closed("b", "2026-11-13", "08:00", "12:00"),
        )

        val state = buildBankState(spAt("2026-11-13", "12:00"), settings, sessions, emptyList())

        assertEquals(Duration.ofHours(1), state.accumulated)
        assertEquals(Duration.ofHours(-2), state.today)
    }

    @Test
    fun recess_countsOnlyRecessDays() {
        val settings = Settings(internshipStart = LocalDate.of(2026, 5, 13), balanceStart = LocalDate.of(2026, 5, 13))
        val daysOff = listOf(
            DayOff(LocalDate.of(2026, 7, 1), DayOffKind.RECESS, null),
            DayOff(LocalDate.of(2026, 7, 2), DayOffKind.RECESS, null),
            DayOff(LocalDate.of(2026, 11, 2), DayOffKind.HOLIDAY, null),
        )

        val state = buildBankState(spAt("2026-11-13", "12:00"), settings, emptyList(), daysOff)

        assertEquals(2, state.recess.takenDays)
        assertEquals(15, state.recess.accruedDays)
    }

    @Test
    fun accumulated_withoutBalanceStart_isZero() {
        val settings = Settings(internshipStart = LocalDate.of(2026, 5, 13), balanceStart = null)
        val sessions = listOf(closed("a", "2026-11-12", "08:00", "15:00"))

        val state = buildBankState(spAt("2026-11-13", "12:00"), settings, sessions, emptyList())

        assertEquals(Duration.ZERO, state.accumulated)
    }
}
