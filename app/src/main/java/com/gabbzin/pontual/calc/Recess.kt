package com.gabbzin.pontual.calc

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class RecessSummary(val accruedDays: Int, val takenDays: Int) {
    val availableDays: Int get() = accruedDays - takenDays
}

fun recess(internshipStart: LocalDate?, today: LocalDate, takenDays: Int): RecessSummary {
    val accrued = if (internshipStart == null) {
        0
    } else {
        val months = ChronoUnit.MONTHS.between(internshipStart, today)
        (30 * months / 12).toInt().coerceAtLeast(0)
    }
    return RecessSummary(accruedDays = accrued, takenDays = takenDays)
}
