package com.gabbzin.pontual.ui.week

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekStateTest {
    @Test
    fun weekStart_sunday_returnsPreviousMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun weekStart_monday_returnsSelf() {
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun weekLabel_format() {
        assertEquals("09/11 – 15/11", weekLabel(LocalDate.of(2026, 11, 9)))
    }
}
