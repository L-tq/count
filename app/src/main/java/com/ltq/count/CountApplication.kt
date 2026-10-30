package com.ltq.count

import android.app.Application
import android.content.Context
import com.ltq.count.data.db.CountDatabase
import com.ltq.count.data.prefs.SettingsRepository
import com.ltq.count.data.repo.BackupRepository
import com.ltq.count.data.repo.CounterRepository

/**
 * Deliberately small manual dependency container. The app has one database, one repository and
 * one preference store, so a DI framework would add more indirection than value.
 */
class AppContainer(context: Context) {
    val database: CountDatabase = CountDatabase.get(context)
    val counterRepository: CounterRepository = CounterRepository(database)
    val settingsRepository: SettingsRepository = SettingsRepository(context)
    val backupRepository: BackupRepository = BackupRepository(context.contentResolver, counterRepository)
}

class CountApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.countApplication: CountApplication
    get() = applicationContext as CountApplication
