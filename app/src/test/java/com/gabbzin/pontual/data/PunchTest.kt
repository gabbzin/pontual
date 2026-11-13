package com.gabbzin.pontual.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gabbzin.pontual.model.PunchAction
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import com.gabbzin.pontual.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class PunchTest {

    private lateinit var db: PontoDatabase
    private lateinit var clock: MutableClock
    private lateinit var repo: PontoRepository

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

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

    private val today = "2026-11-13"

    @Test
    fun punchIn_insideWindow_opensAppSession() = runBlocking {
        setNow(today, "08:00")

        val result = repo.punch(PunchAction.IN)

        assertTrue(result.changed)
        assertNotNull(result.open)
        val open = result.open!!
        assertEquals(spAt(today, "08:00"), open.startedAt)
        assertNull(open.endedAt)
        assertEquals(Source.APP, open.startSource)
        assertEquals(open, db.sessionDao().findOpen())
    }

    @Test
    fun punchIn_whenAlreadyOpen_isNoOp() = runBlocking {
        setNow(today, "08:00")
        repo.punch(PunchAction.IN)
        setNow(today, "09:00")

        val result = repo.punch(PunchAction.IN)

        assertFalse(result.changed)
        assertNotNull(result.open)
        assertEquals(1, db.sessionDao().all().size)
    }

    @Test
    fun punchOut_closesSession() = runBlocking {
        setNow(today, "08:00")
        repo.punch(PunchAction.IN)
        setNow(today, "12:00")

        val result = repo.punch(PunchAction.OUT)

        assertTrue(result.changed)
        assertNull(result.open)
        val only: WorkSession = db.sessionDao().all().single()
        assertEquals(spAt(today, "12:00"), only.endedAt)
        assertEquals(Source.APP, only.endSource)
        assertNull(db.sessionDao().findOpen())
    }

    @Test
    fun punchOut_whenNothingOpen_isNoOp() = runBlocking {
        setNow(today, "12:00")

        val result = repo.punch(PunchAction.OUT)

        assertFalse(result.changed)
        assertNull(result.open)
        assertTrue(db.sessionDao().all().isEmpty())
    }

    @Test
    fun punchIn_at0754_throwsOutsideWindow() = runBlocking {
        setNow(today, "07:54")

        try {
            repo.punch(PunchAction.IN)
            fail("expected PunchException")
        } catch (ex: PunchException) {
            assertEquals(
                PunchError.OutsideWindow(LocalTime.of(7, 55), LocalTime.of(18, 5)),
                ex.error,
            )
        }
        assertTrue(db.sessionDao().all().isEmpty())
    }

    @Test
    fun punchOut_at1806_throwsForgotten() = runBlocking {
        setNow(today, "13:00")
        repo.punch(PunchAction.IN)
        setNow(today, "18:06")

        try {
            repo.punch(PunchAction.OUT)
            fail("expected PunchException")
        } catch (ex: PunchException) {
            assertEquals(PunchError.ForgottenOpenSession, ex.error)
        }
        assertNotNull(db.sessionDao().findOpen())
    }

    @Test
    fun punchIn_nextDayWithYesterdayOpen_throwsForgotten() = runBlocking {
        setNow(today, "09:00")
        repo.punch(PunchAction.IN)
        setNow("2026-11-14", "08:30")

        try {
            repo.punch(PunchAction.IN)
            fail("expected PunchException")
        } catch (ex: PunchException) {
            assertEquals(PunchError.ForgottenOpenSession, ex.error)
        }
        assertEquals(1, db.sessionDao().all().size)
    }

    @Test
    fun punchIn_concurrent_createsSingleSession() = runBlocking(Dispatchers.Default) {
        setNow(today, "08:00")

        coroutineScope {
            repeat(2) { launch { repo.punch(PunchAction.IN) } }
        }

        assertEquals(1, db.sessionDao().all().size)
    }

    @Test
    fun observeSettings_withoutRow_emitsDefaults() = runBlocking {
        assertEquals(Settings(), repo.observeSettings().first())
    }
}
