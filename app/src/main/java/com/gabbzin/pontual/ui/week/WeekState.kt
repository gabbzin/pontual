package com.gabbzin.pontual.ui.week

import com.gabbzin.pontual.calc.DaySummary
import com.gabbzin.pontual.ui.formatDayMonth
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Segunda-feira (ISO) da semana que contém [date]. */
fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** "09/11 – 15/11" */
fun weekLabel(monday: LocalDate): String =
    "${formatDayMonth(monday)} – ${formatDayMonth(monday.plusDays(6))}"

data class WeekState(val monday: LocalDate, val days: List<DaySummary>, val balance: Duration)
