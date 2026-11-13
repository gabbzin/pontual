package com.gabbzin.pontual.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecessTest {

    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun recess_sixMonths_accrues15() {
        val r = recess(LocalDate.of(2026, 5, 13), today, takenDays = 0)
        assertEquals(15, r.accruedDays)
    }

    @Test
    fun recess_fiveMonthsAnd29Days_accrues12() {
        val r = recess(LocalDate.of(2026, 5, 14), today, takenDays = 0)
        assertEquals(12, r.accruedDays)
    }

    @Test
    fun recess_subtractsTaken() {
        val r = recess(LocalDate.of(2026, 5, 13), today, takenDays = 3)
        assertEquals(15, r.accruedDays)
        assertEquals(3, r.takenDays)
        assertEquals(12, r.availableDays)
    }

    @Test
    fun recess_startInFuture_accruesZero() {
        val r = recess(LocalDate.of(2026, 12, 1), today, takenDays = 0)
        assertEquals(0, r.accruedDays)
    }

    @Test
    fun recess_nullStart_accruesZero() {
        val r = recess(null, today, takenDays = 0)
        assertEquals(0, r.accruedDays)
    }
}
