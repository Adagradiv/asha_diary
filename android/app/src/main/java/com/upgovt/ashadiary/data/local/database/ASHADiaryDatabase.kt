package com.upgovt.ashadiary.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.upgovt.ashadiary.data.local.dao.*
import com.upgovt.ashadiary.data.local.entities.*

/**
 * ASHADiaryDatabase - Encrypted SQLite database
 * Uses SQLCipher for encryption
 * Government security compliance - biometrics never leave device
 */
@Database(
    entities = [
        UserEntity::class,
        RoleEntity::class,
        VillageEntity::class,
        UserAreaMappingEntity::class,
        BeneficiaryEntity::class,
        ANCVisitEntity::class,
        BPVisitEntity::class,
        BloodSugarVisitEntity::class,
        VaccinationVisitEntity::class,
        EditRequestEntity::class,
        DuplicateFlagEntity::class,
        AuditLogEntity::class,
        BiometricLocalEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ASHADiaryDatabase : RoomDatabase() {
    
    // DAOs
    abstract fun userDao(): UserDao
    abstract fun roleDao(): RoleDao
    abstract fun villageDao(): VillageDao
    abstract fun userAreaMappingDao(): UserAreaMappingDao
    abstract fun beneficiaryDao(): BeneficiaryDao
    abstract fun ancVisitDao(): ANCVisitDao
    abstract fun bpVisitDao(): BPVisitDao
    abstract fun bloodSugarVisitDao(): BloodSugarVisitDao
    abstract fun vaccinationVisitDao(): VaccinationVisitDao
    abstract fun editRequestDao(): EditRequestDao
    abstract fun duplicateFlagDao(): DuplicateFlagDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun biometricLocalDao(): BiometricLocalDao
    abstract fun syncQueueDao(): SyncQueueDao
    
    companion object {
        @Volatile
        private var INSTANCE: ASHADiaryDatabase? = null
        
        private const val DATABASE_NAME = "asha_diary.db"
        
        /**
         * Get encrypted database instance
         * Passphrase is derived from device-specific secure key
         */
        fun getInstance(context: Context, passphrase: CharArray): ASHADiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context, passphrase)
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Build encrypted database with SQLCipher
         */
        private fun buildDatabase(context: Context, passphrase: CharArray): ASHADiaryDatabase {
            // Create SQLCipher support factory with passphrase
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))
            
            return Room.databaseBuilder(
                context.applicationContext,
                ASHADiaryDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)  // Enable encryption
                .fallbackToDestructiveMigration()  // CAUTION: Only for development
                .addCallback(DatabaseCallback())
                .build()
        }
        
        /**
         * For testing only - unencrypted database
         */
        fun getInstanceForTest(context: Context): ASHADiaryDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ASHADiaryDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
        
        /**
         * Clear instance (for logout or testing)
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
    
    /**
     * Database callback for initialization
     */
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(db)
            // Pre-populate roles
            // This will be triggered by app initialization in Repository
        }
    }
}
