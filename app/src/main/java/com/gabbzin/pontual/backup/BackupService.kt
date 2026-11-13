package com.gabbzin.pontual.backup

import androidx.room.withTransaction
import com.gabbzin.pontual.data.PontoDatabase
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class InvalidBackupException(message: String) : Exception(message)

class BackupService(private val db: PontoDatabase, private val clock: Clock) {

    suspend fun export(out: OutputStream) {
        val settings = db.settingsDao().get() ?: Settings()
        val backup = Backup(
            exportedAt = clock.instant().toString(),
            settings = settings.toDto(),
            sessions = db.sessionDao().all().map { it.toDto() },
            daysOff = db.dayOffDao().all().map { it.toDto() },
        )
        out.write(JSON.encodeToString(Backup.serializer(), backup).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    suspend fun import(input: InputStream) {
        val (settings, sessions, daysOff) = parseAndValidate(input.readBytes())

        db.withTransaction {
            db.sessionDao().deleteAll()
            db.dayOffDao().deleteAll()
            db.settingsDao().upsert(settings)
            sessions.forEach { db.sessionDao().insert(it) }
            daysOff.forEach { db.dayOffDao().upsert(it) }
        }
    }

    private data class Parsed(
        val settings: Settings,
        val sessions: List<WorkSession>,
        val daysOff: List<DayOff>,
    )

    private fun parseAndValidate(bytes: ByteArray): Parsed {
        try {
            val backup = JSON.decodeFromString(Backup.serializer(), bytes.toString(Charsets.UTF_8))
            if (backup.version != 1) {
                throw InvalidBackupException(
                    "Arquivo de backup inválido: versão não suportada ${backup.version}"
                )
            }
            val sessions = backup.sessions.map { it.toModel() }
            val daysOff = backup.daysOff.map { it.toModel() }
            validateSessions(sessions)
            validateDaysOff(daysOff)
            return Parsed(
                settings = backup.settings.toModel(),
                sessions = sessions,
                daysOff = daysOff,
            )
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBackupException("Arquivo de backup inválido: ${e.message}")
        }
    }

    private fun validateDaysOff(daysOff: List<DayOff>) {
        val duplicate = daysOff.groupBy { it.date }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            throw InvalidBackupException(
                "Arquivo de backup inválido: dia ${duplicate.key} aparece mais de uma vez"
            )
        }
    }

    private fun validateSessions(sessions: List<WorkSession>) {
        val duplicateId = sessions.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }
        if (duplicateId != null) {
            throw InvalidBackupException(
                "Arquivo de backup inválido: sessão ${duplicateId.key} duplicada"
            )
        }
        sessions.forEach { s ->
            if (s.endedAt != null && !s.endedAt.isAfter(s.startedAt)) {
                throw InvalidBackupException(
                    "Arquivo de backup inválido: sessão ${s.id} termina antes de começar"
                )
            }
        }
        if (sessions.count { it.endedAt == null } > 1) {
            throw InvalidBackupException("Arquivo de backup inválido: mais de uma sessão aberta")
        }
        val sorted = sessions.sortedBy { it.startedAt }
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            val currentEnd = current.endedAt ?: Instant.MAX
            if (next.startedAt.isBefore(currentEnd)) {
                throw InvalidBackupException(
                    "Arquivo de backup inválido: sessões ${current.id} e ${next.id} se sobrepõem"
                )
            }
        }
    }

    @Serializable
    private data class Backup(
        val version: Int = 1,
        val exportedAt: String,
        val settings: SettingsDto,
        val sessions: List<SessionDto>,
        val daysOff: List<DayOffDto>,
    )

    @Serializable
    private data class SettingsDto(
        val dailyMinutes: Int,
        val workdays: List<Int>,
        val windowStart: String,
        val windowEnd: String,
        val toleranceMinutes: Int,
        val internshipStart: String?,
        val balanceStart: String?,
        val zoneId: String,
    )

    @Serializable
    private data class SessionDto(
        val id: String,
        val startedAt: String,
        val endedAt: String?,
        val startSource: String,
        val endSource: String?,
        val note: String?,
        val createdAt: String,
    )

    @Serializable
    private data class DayOffDto(
        val date: String,
        val kind: String,
        val note: String?,
    )

    private companion object {
        val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = false
            encodeDefaults = true
        }
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun Settings.toDto() = SettingsDto(
            dailyMinutes = dailyMinutes,
            workdays = workdays.map { it.value }.sorted(),
            windowStart = windowStart.format(TIME_FORMAT),
            windowEnd = windowEnd.format(TIME_FORMAT),
            toleranceMinutes = toleranceMinutes,
            internshipStart = internshipStart?.toString(),
            balanceStart = balanceStart?.toString(),
            zoneId = zoneId.id,
        )

        fun SettingsDto.toModel() = Settings(
            id = 1,
            dailyMinutes = dailyMinutes,
            workdays = workdays.map { DayOfWeek.of(it) }.toSet(),
            windowStart = LocalTime.parse(windowStart, TIME_FORMAT),
            windowEnd = LocalTime.parse(windowEnd, TIME_FORMAT),
            toleranceMinutes = toleranceMinutes,
            internshipStart = internshipStart?.let { LocalDate.parse(it) },
            balanceStart = balanceStart?.let { LocalDate.parse(it) },
            zoneId = ZoneId.of(zoneId),
        )

        fun WorkSession.toDto() = SessionDto(
            id = id,
            startedAt = startedAt.toString(),
            endedAt = endedAt?.toString(),
            startSource = startSource.name,
            endSource = endSource?.name,
            note = note,
            createdAt = createdAt.toString(),
        )

        fun SessionDto.toModel() = WorkSession(
            id = id,
            startedAt = Instant.parse(startedAt),
            endedAt = endedAt?.let { Instant.parse(it) },
            startSource = Source.valueOf(startSource),
            endSource = endSource?.let { Source.valueOf(it) },
            note = note,
            createdAt = Instant.parse(createdAt),
        )

        fun DayOff.toDto() = DayOffDto(
            date = date.toString(),
            kind = kind.name,
            note = note,
        )

        fun DayOffDto.toModel() = DayOff(
            date = LocalDate.parse(date),
            kind = DayOffKind.valueOf(kind),
            note = note,
        )
    }
}
