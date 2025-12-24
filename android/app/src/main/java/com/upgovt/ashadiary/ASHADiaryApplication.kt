package com.upgovt.ashadiary

import android.app.Application
import androidx.work.Configuration
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.data.local.database.DatabaseEncryption
import com.upgovt.ashadiary.data.local.database.DatabaseInitializer
import com.upgovt.ashadiary.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ASHADiaryApplication - Application class
 * Initializes database, schedules sync, and sets up WorkManager
 */
class ASHADiaryApplication : Application(), Configuration.Provider {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    lateinit var database: ASHADiaryDatabase
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize database
        initializeDatabase()
        
        // Schedule periodic sync
        schedulePeriodicSync()
    }
    
    /**
     * Initialize encrypted database
     */
    private fun initializeDatabase() {
        applicationScope.launch {
            val passphrase = DatabaseEncryption.getOrCreatePassphrase(this@ASHADiaryApplication)
            database = ASHADiaryDatabase.getInstance(this@ASHADiaryApplication, passphrase)
            
            // Initialize with default data (roles, etc.)
            if (!DatabaseInitializer.isInitialized(database)) {
                DatabaseInitializer.initialize(this@ASHADiaryApplication, database)
            }
        }
    }
    
    /**
     * Schedule periodic sync worker
     */
    private fun schedulePeriodicSync() {
        SyncWorker.schedulePeriodic(this)
    }
    
    /**
     * WorkManager configuration
     */
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
}
