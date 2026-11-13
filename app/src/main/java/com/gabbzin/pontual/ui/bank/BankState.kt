package com.gabbzin.pontual.ui.bank

import com.gabbzin.pontual.calc.RecessSummary
import com.gabbzin.pontual.calc.localDateOf
import com.gabbzin.pontual.calc.recess
import com.gabbzin.pontual.calc.summarizeDays
import com.gabbzin.pontual.calc.totalBalance
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.WorkSession
import java.time.Duration
import java.time.Instant

data class BankState(
    val accumulated: Duration,
    val today: Duration,
    val recess: RecessSummary,
    val daysOff: List<DayOff>,
)

/**
 * Estado da tela Banco. O acumulado vai de `balanceStart` até ontem; hoje aparece separado.
 * [sessions] precisa cobrir de `balanceStart` até hoje (ou estar vazio se `balanceStart` for nulo).
 */
fun buildBankState(
    now: Instant,
    settings: Settings,
    sessions: List<WorkSession>,
    daysOff: List<DayOff>,
): BankState {
    val today = localDateOf(now, settings)
    val yesterday = today.minusDays(1)
    val start = settings.balanceStart

    val accumulated = if (start == null || start.isAfter(yesterday)) {
        Duration.ZERO
    } else {
        totalBalance(summarizeDays(start, yesterday, sessions, daysOff, now, settings))
    }
    val todayBalance = summarizeDays(today, today, sessions, daysOff, now, settings).single().balance
    val recessSummary = recess(
        settings.internshipStart,
        today,
        daysOff.count { it.kind == DayOffKind.RECESS },
    )

    return BankState(
        accumulated = accumulated,
        today = todayBalance,
        recess = recessSummary,
        daysOff = daysOff,
    )
}
