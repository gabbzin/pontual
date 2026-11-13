package com.gabbzin.pontual.ui.today

import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TodayStateTest {

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

    private val settings = Settings(
        internshipStart = LocalDate.of(2026, 10, 1),
        balanceStart = LocalDate.of(2026, 11, 2),
    )

    /** "2026-11-13" + "08:02" em horário de São Paulo. */
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

    private fun open(id: String, date: String, start: String) = WorkSession(
        id = id,
        startedAt = spAt(date, start),
        endedAt = null,
        startSource = Source.APP,
        endSource = null,
        note = null,
        createdAt = spAt(date, start),
    )

    @Test
    fun noSessions_insideWindow_buttonEnter() {
        val state = buildTodayState(
            now = spAt("2026-11-13", "10:00"),
            settings = settings,
            todaySessions = emptyList(),
            openSession = null,
            daysOff = emptyList(),
        )

        assertEquals(PunchButton.ENTER, state.button)
        assertEquals(LocalDate.of(2026, 11, 13), state.date)
        assertEquals("07:55–18:05", state.windowLabel)
        assertNull(state.running)
        assertNull(state.forgotten)
        assertEquals(Duration.ZERO, state.worked)
        assertEquals(Duration.ofHours(6), state.expected)
        assertEquals(Duration.ofHours(-6), state.dayBalance)
    }

    @Test
    fun openSession_buttonExit_andRunningTimer() {
        val session = open("a", "2026-11-13", "13:01")
        val state = buildTodayState(
            now = spAt("2026-11-13", "15:15"),
            settings = settings,
            todaySessions = listOf(session),
            openSession = session,
            daysOff = emptyList(),
        )

        assertEquals(PunchButton.EXIT, state.button)
        assertEquals(Duration.ofHours(2).plusMinutes(14), state.running)
        assertNull(state.forgotten)
    }

    @Test
    fun beforeWindow_buttonDisabled() {
        val state = buildTodayState(
            now = spAt("2026-11-13", "07:30"),
            settings = settings,
            todaySessions = emptyList(),
            openSession = null,
            daysOff = emptyList(),
        )

        assertEquals(PunchButton.DISABLED, state.button)
    }

    @Test
    fun todayState_openSessionFromYesterday_isForgottenAndButtonDisabled() {
        val yesterday = open("y", "2026-11-12", "09:00")
        val state = buildTodayState(
            now = spAt("2026-11-13", "10:00"),
            settings = settings,
            todaySessions = emptyList(),
            openSession = yesterday,
            daysOff = emptyList(),
        )

        assertEquals(PunchButton.DISABLED, state.button)
        assertNotNull(state.forgotten)
        assertEquals(yesterday.id, state.forgotten!!.id)
        assertNull(state.running)
    }

    @Test
    fun dayBalance_matchesMockup() {
        val morning = closed("m", "2026-11-13", "08:02", "12:00")
        val afternoon = open("a", "2026-11-13", "13:01")
        val state = buildTodayState(
            now = spAt("2026-11-13", "13:15"),
            settings = settings,
            todaySessions = listOf(morning, afternoon),
            openSession = afternoon,
            daysOff = emptyList(),
        )

        assertEquals(Duration.ofHours(4).plusMinutes(12), state.worked)
        assertEquals(Duration.ofHours(6), state.expected)
        assertEquals(Duration.ofHours(-1).minusMinutes(48), state.dayBalance)
    }
}
