package com.gabbzin.pontual.calc

import com.gabbzin.pontual.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class SettingsRulesTest {

    private fun window(start: String, end: String, tolerance: Int = 5) = Settings(
        windowStart = LocalTime.parse(start),
        windowEnd = LocalTime.parse(end),
        toleranceMinutes = tolerance,
    )

    @Test
    fun defaults_areValid() {
        assertNull(settingsValidationError(Settings()))
    }

    @Test
    fun startBeforeMidnightWithTolerance_isInvalid() {
        assertNotNull(settingsValidationError(window("00:30", "18:00", tolerance = 60)))
    }

    @Test
    fun startAtMidnightWithTolerance_isValid() {
        assertNull(settingsValidationError(window("00:05", "18:00", tolerance = 5)))
    }

    @Test
    fun endPastMidnightWithTolerance_isInvalid() {
        assertNotNull(settingsValidationError(window("08:00", "23:30", tolerance = 30)))
    }

    @Test
    fun endAt2359WithTolerance_isValid() {
        assertNull(settingsValidationError(window("08:00", "23:29", tolerance = 30)))
    }

    @Test
    fun startEqualsEnd_isInvalid() {
        assertNotNull(settingsValidationError(window("08:00", "08:00")))
    }

    @Test
    fun startAfterEnd_isInvalid() {
        assertNotNull(settingsValidationError(window("18:00", "08:00")))
    }

    @Test
    fun dailyMinutesZero_isInvalid() {
        assertNotNull(settingsValidationError(Settings(dailyMinutes = 0)))
    }

    @Test
    fun dailyMinutesAboveDay_isInvalid() {
        assertNotNull(settingsValidationError(Settings(dailyMinutes = 1441)))
    }

    @Test
    fun negativeTolerance_isInvalid() {
        assertNotNull(settingsValidationError(Settings(toleranceMinutes = -1)))
    }

    @Test
    fun emptyWorkdays_isInvalid() {
        assertNotNull(settingsValidationError(Settings(workdays = emptySet())))
    }

    @Test
    fun workdaysWithOneDay_isValid() {
        assertNull(settingsValidationError(Settings(workdays = setOf(DayOfWeek.SATURDAY))))
    }

    @Test
    fun invalidSettings_messageIsPortuguese() {
        assertEquals("Escolha pelo menos um dia útil.", settingsValidationError(Settings(workdays = emptySet())))
    }
}
