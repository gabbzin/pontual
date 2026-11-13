package com.gabbzin.pontual.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")
val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** "4h12"; com [signed]: "−1h48", "+0h30", "0h00". Segundos são truncados. */
fun formatDuration(d: Duration, signed: Boolean = false): String {
    val totalMinutes = d.toMinutes()
    val abs = Math.abs(totalMinutes)
    val body = "%dh%02d".format(abs / 60, abs % 60)
    return when {
        !signed || totalMinutes == 0L -> body
        totalMinutes < 0 -> "−$body"
        else -> "+$body"
    }
}

fun formatTime(i: Instant, zone: ZoneId): String = TIME.format(i.atZone(zone))

/** Cronômetro "HH:mm". */
fun formatTimer(d: Duration): String {
    val m = d.toMinutes().coerceAtLeast(0)
    return "%02d:%02d".format(m / 60, m % 60)
}

fun formatDayMonth(date: LocalDate): String = DAY_MONTH.format(date)

/** "Sexta, 13/11" */
fun formatLongDay(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, PT_BR).substringBefore("-")
        .replaceFirstChar { it.uppercase() } + ", " + formatDayMonth(date)

/** "Sex" */
fun formatShortWeekday(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, PT_BR).removeSuffix(".")
        .replaceFirstChar { it.uppercase() }
