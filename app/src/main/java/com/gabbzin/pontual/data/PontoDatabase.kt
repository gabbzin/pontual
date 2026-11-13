package com.gabbzin.pontual.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.WorkSession

@Database(
    entities = [WorkSession::class, DayOff::class, Settings::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PontoDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun dayOffDao(): DayOffDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        // IFNULL makes every open session map to the same key (0), so a second
        // open session collides. A plain index on endedAt would not work because
        // NULLs are distinct in unique indexes.
        private const val ONE_OPEN_SESSION_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS one_open_session " +
                "ON work_session(IFNULL(endedAt, 0)) WHERE endedAt IS NULL"

        private val openSessionCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(ONE_OPEN_SESSION_SQL)
            }
        }

        fun build(context: Context): PontoDatabase =
            Room.databaseBuilder(context, PontoDatabase::class.java, "pontual.db")
                .addCallback(openSessionCallback)
                .build()

        fun inMemory(context: Context): PontoDatabase =
            Room.inMemoryDatabaseBuilder(context, PontoDatabase::class.java)
                .addCallback(openSessionCallback)
                .build()
    }
}
