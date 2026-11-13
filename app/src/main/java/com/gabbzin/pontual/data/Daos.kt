package com.gabbzin.pontual.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.WorkSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(s: WorkSession)

    @Update
    suspend fun update(s: WorkSession)

    @Query("DELETE FROM work_session WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM work_session WHERE endedAt IS NULL LIMIT 1")
    suspend fun findOpen(): WorkSession?

    @Query("SELECT * FROM work_session WHERE endedAt IS NULL LIMIT 1")
    fun observeOpen(): Flow<WorkSession?>

    @Query("SELECT * FROM work_session WHERE id = :id")
    suspend fun findById(id: String): WorkSession?

    @Query(
        "SELECT * FROM work_session " +
            "WHERE startedAt < :end AND (endedAt IS NULL OR endedAt > :start) " +
            "AND (:excludeId IS NULL OR id != :excludeId) " +
            "ORDER BY startedAt"
    )
    suspend fun findOverlapping(start: Instant, end: Instant, excludeId: String?): List<WorkSession>

    @Query(
        "SELECT * FROM work_session WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt"
    )
    fun observeBetween(from: Instant, to: Instant): Flow<List<WorkSession>>

    @Query("SELECT * FROM work_session ORDER BY startedAt")
    suspend fun all(): List<WorkSession>

    @Query("DELETE FROM work_session")
    suspend fun deleteAll()
}

@Dao
interface DayOffDao {

    @Upsert
    suspend fun upsert(d: DayOff)

    @Query("DELETE FROM day_off WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("SELECT * FROM day_off ORDER BY date")
    fun observeAll(): Flow<List<DayOff>>

    @Query("SELECT * FROM day_off ORDER BY date")
    suspend fun all(): List<DayOff>

    @Query("DELETE FROM day_off")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): Settings?

    @Upsert
    suspend fun upsert(s: Settings)
}
