package com.gabbzin.pontual.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gabbzin.pontual.model.PunchAction
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.testutil.MutableClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualEntryTest {

    private lateinit var db: PontoDatabase
    private lateinit var clock: MutableClock
    private lateinit var repo: PontoRepository

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val today = "2026-11-13"

    @Before
    fun setUp() {
        db = PontoDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        clock = MutableClock()
        repo = PontoRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun spAt(date: String, time: String): Instant =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(sp).toInstant()

    private fun setNow(date: String, time: String) = clock.set(spAt(date, time))

    /** Opens an app session on [today] at 09:00 and moves the clock to the next day, so it is forgotten. */
    private fun forgottenSessionId(): String = runBlocking {
        setNow(today, "09:00")
        val id = repo.punch(PunchAction.IN).open!!.id
        setNow("2026-11-14", "10:00")
        id
    }

    private fun assertThrowsPunch(expected: PunchError, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected $expected")
        } catch (ex: PunchException) {
            assertEquals(expected, ex.error)
        }
    }

    @Test
    fun closeForgotten_setsManualEndAndNote() = runBlocking {
        val id = forgottenSessionId()

        val closed = repo.closeForgotten(id, spAt(today, "17:00"), "esqueci de bater")

        assertEquals(spAt(today, "17:00"), closed.endedAt)
        assertEquals(Source.MANUAL, closed.endSource)
        assertEquals("esqueci de bater", closed.note)
        assertEquals(Source.APP, closed.startSource)
        assertEquals(closed, db.sessionDao().findById(id))
        assertTrue(db.sessionDao().findOpen() == null)
    }

    @Test
    fun closeForgotten_onClosedSession_throwsNotOpen() = runBlocking {
        setNow(today, "09:00")
        val id = repo.punch(PunchAction.IN).open!!.id
        setNow(today, "12:00")
        repo.punch(PunchAction.OUT)
        setNow(today, "17:00")

        assertThrowsPunch(PunchError.NotOpen) {
            repo.closeForgotten(id, spAt(today, "11:00"), "x")
        }
    }

    @Test
    fun closeForgotten_endAfterWindow_throwsInvalidRange() {
        val id = forgottenSessionId()

        assertThrowsPunch(PunchError.InvalidRange) {
            repo.closeForgotten(id, spAt(today, "18:06"), "x")
        }
    }

    @Test
    fun addManual_validPastPeriod_isManualBothSides() = runBlocking {
        setNow(today, "17:00")

        val added = repo.addManual(spAt(today, "10:00"), spAt(today, "12:00"), "almoço externo")

        assertEquals(Source.MANUAL, added.startSource)
        assertEquals(Source.MANUAL, added.endSource)
        assertEquals("almoço externo", added.note)
        assertTrue(added.isManualOnly)
        assertEquals(added, db.sessionDao().findById(added.id))
    }

    @Test
    fun addManual_blankNote_throwsNoteRequired() {
        setNow(today, "17:00")

        assertThrowsPunch(PunchError.NoteRequired) {
            repo.addManual(spAt(today, "10:00"), spAt(today, "12:00"), "   ")
        }
    }

    @Test
    fun addManual_endInFuture_throwsInFuture() {
        setNow(today, "10:00")

        assertThrowsPunch(PunchError.InFuture) {
            repo.addManual(spAt(today, "09:00"), spAt(today, "11:00"), "x")
        }
    }

    @Test
    fun addManual_endBeforeStart_throwsInvalidRange() {
        setNow(today, "17:00")

        assertThrowsPunch(PunchError.InvalidRange) {
            repo.addManual(spAt(today, "12:00"), spAt(today, "11:00"), "x")
        }
    }

    @Test
    fun addManual_overlapsExisting_throwsOverlap() = runBlocking {
        setNow(today, "17:00")
        repo.addManual(spAt(today, "10:00"), spAt(today, "12:00"), "a")

        assertThrowsPunch(PunchError.Overlap) {
            repo.addManual(spAt(today, "11:00"), spAt(today, "13:00"), "b")
        }
    }

    @Test
    fun addManual_touchingBorder_isAllowed() = runBlocking {
        setNow(today, "17:00")
        repo.addManual(spAt(today, "10:00"), spAt(today, "12:00"), "a")

        repo.addManual(spAt(today, "12:00"), spAt(today, "13:00"), "b")

        assertEquals(2, db.sessionDao().all().size)
    }

    @Test
    fun addManual_onSaturday_isAllowed() = runBlocking {
        setNow("2026-11-14", "17:00")

        val added = repo.addManual(
            spAt("2026-11-14", "10:00"), spAt("2026-11-14", "12:00"), "sábado",
        )

        assertTrue(added.isManualOnly)
    }

    @Test
    fun deleteManual_appSession_throwsNotManual() = runBlocking {
        setNow(today, "08:00")
        val id = repo.punch(PunchAction.IN).open!!.id
        setNow(today, "09:00")
        repo.punch(PunchAction.OUT)

        assertThrowsPunch(PunchError.NotManual) { repo.deleteManual(id) }
        assertEquals(1, db.sessionDao().all().size)
    }

    @Test
    fun deleteManual_halfManual_throwsNotManual() = runBlocking {
        val id = forgottenSessionId()
        repo.closeForgotten(id, spAt(today, "17:00"), "x")

        assertThrowsPunch(PunchError.NotManual) { repo.deleteManual(id) }
        assertEquals(1, db.sessionDao().all().size)
    }

    @Test
    fun deleteManual_fullyManual_removes() = runBlocking {
        setNow(today, "17:00")
        val added = repo.addManual(spAt(today, "10:00"), spAt(today, "12:00"), "x")

        repo.deleteManual(added.id)

        assertTrue(db.sessionDao().all().isEmpty())
    }
}
