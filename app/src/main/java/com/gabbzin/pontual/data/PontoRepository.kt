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

    fun observeSessions(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<List<WorkSession>> =
        db.sessionDao().observeBetween(
            from.atStartOfDay(zone).toInstant(),
            to.plusDays(1).atStartOfDay(zone).toInstant(),
        )

    fun observeDaysOff(): Flow<List<DayOff>> = db.dayOffDao().observeAll()

    suspend fun addDayOff(d: DayOff) = db.dayOffDao().upsert(d)

    suspend fun removeDayOff(date: LocalDate) = db.dayOffDao().delete(date)
}
