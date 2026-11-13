package com.gabbzin.pontual.ui.today

import com.gabbzin.pontual.calc.isForgotten
import com.gabbzin.pontual.calc.isWithinWindow
import com.gabbzin.pontual.calc.localDateOf
import com.gabbzin.pontual.calc.punchWindow
import com.gabbzin.pontual.calc.summarizeDays
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.WorkSession
import com.gabbzin.pontual.ui.formatTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class PunchButton { ENTER, EXIT, DISABLED }

data class TodayState(
    val date: LocalDate,
    val button: PunchButton,
    val windowLabel: String,
    val running: Duration?,
    val worked: Duration,
    val expected: Duration,
    val dayBalance: Duration,
    val sessions: List<WorkSession>,
    val forgotten: WorkSession?,
)

/**
 * Estado da tela Hoje. [openSession] pode ser de outro dia (sessão esquecida).
 */
fun buildTodayState(
    now: Instant,
    settings: Settings,
    todaySessions: List<WorkSession>,
    openSession: WorkSession?,
    daysOff: List<DayOff>,
): TodayState {
    val today = localDateOf(now, settings)
    val window = punchWindow(today, settings)
    val forgotten = openSession?.takeIf { isForgotten(it, now, settings) }

    val running = openSession
        ?.takeIf { forgotten == null && localDateOf(it.startedAt, settings) == today }
        ?.let { Duration.between(it.startedAt, now) }

    val button = when {
        forgotten != null -> PunchButton.DISABLED
        !isWithinWindow(now, settings) -> PunchButton.DISABLED
        openSession != null -> PunchButton.EXIT
        else -> PunchButton.ENTER
    }

    val day = summarizeDays(today, today, todaySessions, daysOff, now, settings).single()

    return TodayState(
        date = today,
        button = button,
        windowLabel = "${formatTime(window.start, settings.zoneId)}–${formatTime(window.endInclusive, settings.zoneId)}",
        running = running,
        worked = day.worked,
        expected = day.expected,
        dayBalance = day.balance,
        sessions = todaySessions,
        forgotten = forgotten,
    )
}
