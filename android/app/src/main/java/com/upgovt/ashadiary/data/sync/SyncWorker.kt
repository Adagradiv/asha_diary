package com.upgovt.ashadiary.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.upgovt.ashadiary.data.local.dao.*
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.utils.network.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * SyncWorker - Background sync worker using WorkManager
 * 
 * Runs every 2 hours to sync:
 * - Beneficiary data
 * - Health visit data (ANC, BP, Blood Sugar, Vaccination)
 * - Edit requests
 * - Duplicate flags
 * - Audit logs
 * 
 * NEVER syncs:
 * - Biometric data (fingerprints stay local)
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "asha_diary_sync"
        
        // Sync interval: 2 hours
        private const val SYNC_INTERVAL_HOURS = 2L
        
        /**
         * Schedule periodic sync
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS,
                30, // Flex interval: 30 minutes
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            
            Log.i(TAG, "Periodic sync scheduled (every $SYNC_INTERVAL_HOURS hours)")
        }
        
        /**
         * Trigger immediate one-time sync
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag("immediate_sync")
                .build()
            
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            Log.i(TAG, "Immediate sync triggered")
        }
        
        /**
         * Cancel all sync work
         */
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Sync cancelled")
        }
    }
    
    private val networkMonitor = NetworkMonitor(applicationContext)
    private val database = ASHADiaryDatabase.getInstance(
        applicationContext,
        // Get passphrase from secure storage
        com.upgovt.ashadiary.data.local.database.DatabaseEncryption
            .getOrCreatePassphrase(applicationContext)
    )
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sync started")
        
        try {
            // Check network availability
            if (!networkMonitor.isNetworkAvailable()) {
                Log.w(TAG, "No network available, will retry later")
                return@withContext Result.retry()
            }
            
            // Create sync manager
            val syncManager = SyncManager(
                context = applicationContext,
                database = database,
                networkMonitor = networkMonitor
            )
            
            // Execute sync
            val syncResult = syncManager.executeSync()
            
            when (syncResult.status) {
                SyncStatus.SUCCESS -> {
                    Log.i(TAG, "Sync completed successfully: ${syncResult.summary}")
                    Result.success(
                        workDataOf(
                            "synced_count" to syncResult.syncedCount,
                            "failed_count" to syncResult.failedCount,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
                SyncStatus.PARTIAL -> {
                    Log.w(TAG, "Sync partially completed: ${syncResult.summary}")
                    Result.success(
                        workDataOf(
                            "synced_count" to syncResult.syncedCount,
                            "failed_count" to syncResult.failedCount,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
                SyncStatus.FAILED -> {
                    Log.e(TAG, "Sync failed: ${syncResult.errorMessage}")
                    Result.retry()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.retry()
        }
    }
}

/**
 * Sync status enum
 */
enum class SyncStatus {
    SUCCESS,    // All items synced
    PARTIAL,    // Some items synced, some failed
    FAILED      // Critical failure, no items synced
}

/**
 * Sync result
 */
data class SyncResult(
    val status: SyncStatus,
    val syncedCount: Int,
    val failedCount: Int,
    val summary: String,
    val errorMessage: String? = null
)
