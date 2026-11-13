package com.gabbzin.pontual.data

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Converters {

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.format(TIME_FORMAT)

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, TIME_FORMAT) }

    @TypeConverter
    fun zoneIdToString(value: ZoneId?): String? = value?.id

    @TypeConverter
    fun stringToZoneId(value: String?): ZoneId? = value?.let { ZoneId.of(it) }

    @TypeConverter
    fun daysOfWeekToString(value: Set<DayOfWeek>?): String? =
        value?.map { it.value }?.sorted()?.joinToString(",")

    @TypeConverter
    fun stringToDaysOfWeek(value: String?): Set<DayOfWeek>? =
        value?.let {
            if (it.isEmpty()) emptySet()
            else it.split(",").map { part -> DayOfWeek.of(part.toInt()) }.toSet()
        }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
