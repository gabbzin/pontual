package com.gabbzin.pontual

import android.app.Application
import android.content.Context
import com.gabbzin.pontual.backup.BackupService
import com.gabbzin.pontual.data.PontoDatabase
import com.gabbzin.pontual.data.PontoRepository
import java.time.Clock

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()
    val db: PontoDatabase = PontoDatabase.build(context)
    val repository = PontoRepository(db, clock)
    val backup = BackupService(db, clock)
}

class PontualApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
