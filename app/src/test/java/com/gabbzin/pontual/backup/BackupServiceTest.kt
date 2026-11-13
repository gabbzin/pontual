package com.gabbzin.pontual.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gabbzin.pontual.data.PontoDatabase
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupServiceTest {

    private lateinit var db: PontoDatabase
    private lateinit var service: BackupService

    @Before
    fun setUp() {
        db = PontoDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        service = BackupService(db, Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val manual = WorkSession(
        id = "s2",
        startedAt = Instant.parse("2026-11-11T09:00:00Z"),
        endedAt = Instant.parse("2026-11-11T13:00:00Z"),
        startSource = Source.MANUAL,
        endSource = Source.MANUAL,
        note = "esqueci de bater",
        createdAt = Instant.parse("2026-11-11T15:00:00Z"),
    )

    private val closed = WorkSession(
        id = "s1",
        startedAt = Instant.parse("2026-11-10T08:00:00Z"),
        endedAt = Instant.parse("2026-11-10T12:00:00Z"),
        startSource = Source.APP,
        endSource = Source.APP,
        note = null,
        createdAt = Instant.parse("2026-11-10T08:00:00Z"),
    )

    private val open = WorkSession(
        id = "s3",
        startedAt = Instant.parse("2026-11-12T08:00:00Z"),
        endedAt = null,
        startSource = Source.APP,
        endSource = null,
        note = null,
        createdAt = Instant.parse("2026-11-12T08:00:00Z"),
    )

    private val settings = Settings(
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

    private val holiday = DayOff(LocalDate.of(2026, 11, 2), DayOffKind.HOLIDAY, "Finados")
    private val recess = DayOff(LocalDate.of(2026, 11, 5), DayOffKind.RECESS, null)

    private suspend fun seed() {
        db.settingsDao().upsert(settings)
        db.sessionDao().insert(closed)
        db.sessionDao().insert(manual)
        db.sessionDao().insert(open)
        db.dayOffDao().upsert(holiday)
        db.dayOffDao().upsert(recess)
    }

    private suspend fun export(): ByteArray {
        val out = ByteArrayOutputStream()
        service.export(out)
        return out.toByteArray()
    }

    private suspend fun assertDataEquals(
        expectedSessions: List<WorkSession>,
        expectedDaysOff: List<DayOff>,
        expectedSettings: Settings?,
    ) {
        assertEquals(expectedSessions, db.sessionDao().all())
        assertEquals(expectedDaysOff, db.dayOffDao().all())
        assertEquals(expectedSettings, db.settingsDao().get())
    }

    private fun sessionJson(id: String, start: String, end: String?) =
        """{"id":"$id","startedAt":"$start","endedAt":${end?.let { "\"$it\"" } ?: "null"},""" +
            """"startSource":"APP","endSource":${if (end == null) "null" else "\"APP\""},""" +
            """"note":null,"createdAt":"$start"}"""

    private fun backupJson(version: Int = 1, sessions: List<String> = emptyList()) =
        """
        {"version":$version,"exportedAt":"2026-11-13T12:00:00Z",
         "settings":{"dailyMinutes":360,"workdays":[1,2,3,4,5],"windowStart":"08:00",
           "windowEnd":"18:00","toleranceMinutes":5,"internshipStart":null,
           "balanceStart":null,"zoneId":"America/Sao_Paulo"},
         "sessions":[${sessions.joinToString(",")}],
         "daysOff":[]}
        """.trimIndent()

    @Test
    fun exportThenImport_roundTripsEverything() = runBlocking {
        seed()
        val bytes = export()

        db.sessionDao().deleteAll()
        db.dayOffDao().deleteAll()
        service.import(ByteArrayInputStream(bytes))

        assertDataEquals(listOf(closed, manual, open), listOf(holiday, recess), settings)
    }

    @Test
    fun import_replacesExistingData() = runBlocking {
        seed()
        val bytes = export()

        db.sessionDao().deleteAll()
        db.dayOffDao().deleteAll()
        db.settingsDao().upsert(Settings(id = 1, dailyMinutes = 1))
        db.sessionDao().insert(
            WorkSession(
                id = "junk",
                startedAt = Instant.parse("2026-01-01T08:00:00Z"),
                endedAt = null,
                startSource = Source.APP,
                endSource = null,
                note = null,
                createdAt = Instant.parse("2026-01-01T08:00:00Z"),
            )
        )
        db.dayOffDao().upsert(DayOff(LocalDate.of(2026, 3, 3), DayOffKind.RECESS, "lixo"))

        service.import(ByteArrayInputStream(bytes))

        assertDataEquals(listOf(closed, manual, open), listOf(holiday, recess), settings)
    }

    @Test
    fun import_invalidJson_keepsExistingData() = runBlocking {
        seed()

        assertThrows(InvalidBackupException::class.java) {
            runBlocking { service.import(ByteArrayInputStream("não é json".toByteArray())) }
        }

        assertDataEquals(listOf(closed, manual, open), listOf(holiday, recess), settings)
    }

    @Test
    fun import_wrongVersion_rejected() = runBlocking {
        seed()

        assertThrows(InvalidBackupException::class.java) {
            runBlocking { service.import(ByteArrayInputStream(backupJson(version = 2).toByteArray())) }
        }

        assertDataEquals(listOf(closed, manual, open), listOf(holiday, recess), settings)
    }

    @Test
    fun import_overlappingSessions_rejected() = runBlocking {
        seed()
        val json = backupJson(
            sessions = listOf(
                sessionJson("a", "2026-11-10T08:00:00Z", "2026-11-10T12:00:00Z"),
                sessionJson("b", "2026-11-10T11:00:00Z", "2026-11-10T13:00:00Z"),
            )
        )

        assertThrows(InvalidBackupException::class.java) {
            runBlocking { service.import(ByteArrayInputStream(json.toByteArray())) }
        }

        assertDataEquals(listOf(closed, manual, open), listOf(holiday, recess), settings)
    }
}
