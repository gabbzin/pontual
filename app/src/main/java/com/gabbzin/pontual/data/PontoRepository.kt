package com.gabbzin.pontual.data

import androidx.room.withTransaction
import com.gabbzin.pontual.calc.isForgotten
import com.gabbzin.pontual.calc.localDateOf
import com.gabbzin.pontual.calc.isWithinWindow
import com.gabbzin.pontual.calc.punchWindow
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.PunchAction
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PunchResult(val changed: Boolean, val open: WorkSession?)

class PontoRepository(private val db: PontoDatabase, private val clock: Clock) {

    fun observeSettings(): Flow<Settings> =
        db.settingsDao().observe().map { it ?: Settings() }

    suspend fun settings(): Settings = db.settingsDao().get() ?: Settings()

    suspend fun saveSettings(s: Settings) = db.settingsDao().upsert(s.copy(id = 1))

    suspend fun punch(action: PunchAction): PunchResult = db.withTransaction {
        val now = clock.instant()
        val s = settings()
        val sessions = db.sessionDao()
        val open = sessions.findOpen()

        if (open != null && isForgotten(open, now, s)) {
            throw PunchException(PunchError.ForgottenOpenSession)
        }

        when (action) {
            PunchAction.IN -> if (open != null) return@withTransaction PunchResult(false, open)
            PunchAction.OUT -> if (open == null) return@withTransaction PunchResult(false, null)
        }

        if (!isWithinWindow(now, s)) {
            val window = punchWindow(localDateOf(now, s), s)
            throw PunchException(
                PunchError.OutsideWindow(
                    from = window.start.atZone(s.zoneId).toLocalTime(),
                    to = window.endInclusive.atZone(s.zoneId).toLocalTime(),
                )
            )
        }

        when (action) {
            PunchAction.IN -> {
                val created = WorkSession(
                    id = UUID.randomUUID().toString(),
                    startedAt = now,
                    endedAt = null,
                    startSource = Source.APP,
                    endSource = null,
                    note = null,
                    createdAt = now,
                )
                sessions.insert(created)
                PunchResult(true, created)
            }
            PunchAction.OUT -> {
                sessions.update(open!!.copy(endedAt = now, endSource = Source.APP))
                PunchResult(true, null)
            }
        }
    }

    suspend fun closeForgotten(id: String, endedAt: Instant, note: String): WorkSession =
        db.withTransaction {
            val now = clock.instant()
            requireNote(note)
            requireNotFuture(endedAt, now)
            val session = db.sessionDao().findById(id)
                ?.takeIf { it.endedAt == null }
                ?: throw PunchException(PunchError.NotOpen)
            requireValidSpan(session.startedAt, endedAt, id)
            val closed = session.copy(endedAt = endedAt, endSource = Source.MANUAL, note = note)
            db.sessionDao().update(closed)
            closed
        }

    suspend fun addManual(startedAt: Instant, endedAt: Instant, note: String): WorkSession =
        db.withTransaction {
            val now = clock.instant()
            requireNote(note)
            requireNotFuture(endedAt, now)
            requireValidSpan(startedAt, endedAt, null)
            val added = WorkSession(
                id = UUID.randomUUID().toString(),
                startedAt = startedAt,
                endedAt = endedAt,
                startSource = Source.MANUAL,
                endSource = Source.MANUAL,
                note = note,
                createdAt = now,
            )
            db.sessionDao().insert(added)
            added
        }

    suspend fun deleteManual(id: String) = db.withTransaction {
        val session = db.sessionDao().findById(id) ?: return@withTransaction
        if (!session.isManualOnly) throw PunchException(PunchError.NotManual)
        db.sessionDao().delete(id)
    }

    private fun requireNote(note: String) {
        if (note.isBlank()) throw PunchException(PunchError.NoteRequired)
    }

    private fun requireNotFuture(endedAt: Instant, now: Instant) {
        if (endedAt > now) throw PunchException(PunchError.InFuture)
    }

    private suspend fun requireValidSpan(startedAt: Instant, endedAt: Instant, excludeId: String?) {
        val s = settings()
        val sameDay = localDateOf(startedAt, s) == localDateOf(endedAt, s)
        val inWindow = isWithinWindow(startedAt, s) && isWithinWindow(endedAt, s)
        if (!endedAt.isAfter(startedAt) || !sameDay || !inWindow) {
            throw PunchException(PunchError.InvalidRange)
        }
        if (db.sessionDao().findOverlapping(startedAt, endedAt, excludeId).isNotEmpty()) {
            throw PunchException(PunchError.Overlap)
        }
    }

    fun observeSessions(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<WorkSession>> =
        db.sessionDao().observeBetween(
            from.atStartOfDay(zone).toInstant(),
            to.plusDays(1).atStartOfDay(zone).toInstant(),
        )

    fun observeDaysOff(): Flow<List<DayOff>> = db.dayOffDao().observeAll()

    suspend fun addDayOff(d: DayOff) = db.dayOffDao().upsert(d)

    suspend fun removeDayOff(date: LocalDate) = db.dayOffDao().delete(date)
}
