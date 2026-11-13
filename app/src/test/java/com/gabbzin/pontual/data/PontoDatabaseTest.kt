package com.gabbzin.pontual.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PontoDatabaseTest {

    private lateinit var db: PontoDatabase

    @Before
    fun setUp() {
        db = PontoDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(hour: Int): Instant = Instant.parse("2026-11-13T%02d:00:00Z".format(hour))

    private fun session(id: String, start: Instant, end: Instant?) = WorkSession(
        id = id,
        startedAt = start,
        endedAt = end,
        startSource = Source.APP,
        endSource = if (end == null) null else Source.APP,
        note = null,
        createdAt = start,
    )

    @Test
    fun settings_roundTripsAllFields() = runBlocking {
        val settings = Settings(
            id = 1,
            dailyMinutes = 420,
            workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
            windowStart = LocalTime.of(7, 30),
            windowEnd = LocalTime.of(17, 45),
            toleranceMinutes = 10,
            internshipStart = LocalDate.of(2026, 1, 5),
            balanceStart = LocalDate.of(2026, 2, 1),
            zoneId = ZoneId.of("Europe/Lisbon"),
        )

        db.settingsDao().upsert(settings)

        assertEquals(settings, db.settingsDao().get())
    }

    @Test
    fun session_roundTripsInstantsAndNullEnd() = runBlocking {
        val open = session("open", at(9), null)

        db.sessionDao().insert(open)

        assertEquals(open, db.sessionDao().findById("open"))
        assertEquals(open, db.sessionDao().findOpen())
    }

    @Test
    fun secondOpenSession_violatesIndex() {
        runBlocking { db.sessionDao().insert(session("first", at(9), null)) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.sessionDao().insert(session("second", at(10), null)) }
        }
    }

    @Test
    fun twoClosedSessions_allowed() = runBlocking {
        db.sessionDao().insert(session("a", at(8), at(9)))
        db.sessionDao().insert(session("b", at(10), at(11)))

        assertEquals(2, db.sessionDao().all().size)
    }

    @Test
    fun findOverlapping_detectsPartialAndOpen() = runBlocking {
        db.sessionDao().insert(session("closed", at(10), at(12)))
        db.sessionDao().insert(session("open", at(13), null))

        val partial = db.sessionDao().findOverlapping(at(11), at(13), null)
        assertTrue(partial.any { it.id == "closed" })

        val touchingEnd = db.sessionDao().findOverlapping(at(12), at(13), null)
        assertFalse(touchingEnd.any { it.id == "closed" })

        val openSession = db.sessionDao().findOverlapping(at(14), at(15), null)
        assertTrue(openSession.any { it.id == "open" })
    }
}
