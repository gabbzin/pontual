package com.gabbzin.pontual.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Source { APP, MANUAL }

enum class DayOffKind { HOLIDAY, RECESS }

enum class PunchAction { IN, OUT }

@Entity(tableName = "work_session")
data class WorkSession(
    @PrimaryKey val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val startSource: Source,
    val endSource: Source?,
    val note: String?,
    val createdAt: Instant,
) {
    val isManualOnly: Boolean get() = startSource == Source.MANUAL && endSource == Source.MANUAL
}

@Entity(tableName = "day_off")
data class DayOff(
    @PrimaryKey val date: LocalDate,
    val kind: DayOffKind,
    val note: String?,
)

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val dailyMinutes: Int = 360,
    val workdays: Set<DayOfWeek> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
    val windowStart: LocalTime = LocalTime.of(8, 0),
    val windowEnd: LocalTime = LocalTime.of(18, 0),
    val toleranceMinutes: Int = 5,
    val internshipStart: LocalDate? = null,
    val balanceStart: LocalDate? = null,
    val zoneId: ZoneId = ZoneId.of("America/Sao_Paulo"),
) {
    val isConfigured: Boolean get() = internshipStart != null && balanceStart != null
}
