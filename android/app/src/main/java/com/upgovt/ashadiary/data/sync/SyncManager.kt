package com.upgovt.ashadiary.data.sync

import android.content.Context
import android.util.Log
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.data.local.entities.*
import com.upgovt.ashadiary.utils.network.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson

/**
 * SyncManager - Orchestrates the sync process
 * 
 * Sync order (priority-based):
 * 1. Audit logs (highest priority)
 * 2. Beneficiaries
 * 3. Health visits (ANC, BP, Blood Sugar, Vaccination)
 * 4. Edit requests
 * 5. Duplicate flags
 * 6. User data
 * 7. Village data
 */
class SyncManager(
    private val context: Context,
    private val database: ASHADiaryDatabase,
    private val networkMonitor: NetworkMonitor
) {
    
    companion object {
        private const val TAG = "SyncManager"
        private const val BATCH_SIZE = 50  // Sync in batches to avoid memory issues
    }
    
    private val syncQueueDao = database.syncQueueDao()
    private val gson = Gson()
    
    /**
     * Execute full sync process
     */
    suspend fun executeSync(): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync process")
        
        var totalSynced = 0
        var totalFailed = 0
        
        try {
            // Check network
            if (!networkMonitor.isNetworkAvailable()) {
                return@withContext SyncResult(
                    status = SyncStatus.FAILED,
                    syncedCount = 0,
                    failedCount = 0,
                    summary = "नेटवर्क उपलब्ध नहीं",
                    errorMessage = "No network connection"
                )
            }
            
            // Get pending items from sync queue (priority-based)
            val pendingItems = syncQueueDao.getPendingItems(BATCH_SIZE)
            
            if (pendingItems.isEmpty()) {
                Log.d(TAG, "No items to sync")
                return@withContext SyncResult(
                    status = SyncStatus.SUCCESS,
                    syncedCount = 0,
                    failedCount = 0,
                    summary = "कोई परिवर्तन नहीं"
                )
            }
            
            Log.d(TAG, "Processing ${pendingItems.size} items")
            
            // Process each item
            for (item in pendingItems) {
                val result = syncItem(item)
                
                if (result) {
                    totalSynced++
                    syncQueueDao.markAsSuccess(item.id)
                } else {
                    totalFailed++
                    val nextRetryAt = calculateNextRetryTime(item.retry_count)
                    syncQueueDao.markAsFailed(
                        queueId = item.id,
                        errorMessage = "Sync failed",
                        errorCode = null,
                        nextRetryAt = nextRetryAt
                    )
                }
            }
            
            // Check for items that need retry
            val retryItems = syncQueueDao.getRetryableItems(BATCH_SIZE)
            Log.d(TAG, "${retryItems.size} items need retry")
            
            // Determine sync status
            val status = when {
                totalFailed == 0 -> SyncStatus.SUCCESS
                totalSynced > 0 -> SyncStatus.PARTIAL
                else -> SyncStatus.FAILED
            }
            
            SyncResult(
                status = status,
                syncedCount = totalSynced,
                failedCount = totalFailed,
                summary = "$totalSynced सफल, $totalFailed विफल"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync process failed", e)
            SyncResult(
                status = SyncStatus.FAILED,
                syncedCount = totalSynced,
                failedCount = totalFailed,
                summary = "सिंक प्रक्रिया विफल",
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Sync individual item
     */
    private suspend fun syncItem(item: SyncQueueEntity): Boolean {
        return try {
            when (item.entity_type) {
                "BENEFICIARY" -> syncBeneficiary(item)
                "ANC_VISIT" -> syncVisit(item, "anc")
                "BP_VISIT" -> syncVisit(item, "bp")
                "BLOOD_SUGAR_VISIT" -> syncVisit(item, "blood_sugar")
                "VACCINATION_VISIT" -> syncVisit(item, "vaccination")
                "EDIT_REQUEST" -> syncEditRequest(item)
                "DUPLICATE_FLAG" -> syncDuplicateFlag(item)
                "AUDIT_LOG" -> syncAuditLog(item)
                "USER" -> syncUser(item)
                "VILLAGE" -> syncVillage(item)
                else -> {
                    Log.w(TAG, "Unknown entity type: ${item.entity_type}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync ${item.entity_type} (${item.entity_id})", e)
            false
        }
    }
    
    /**
     * Sync beneficiary data
     */
    private suspend fun syncBeneficiary(item: SyncQueueEntity): Boolean {
        // TODO: Implement actual API call
        // For now, simulate sync
        
        val beneficiary = gson.fromJson(item.payload_json, BeneficiaryEntity::class.java)
        
        // Simulate API call delay
        kotlinx.coroutines.delay(100)
        
        // In production:
        // 1. Send beneficiary to server API
        // 2. Handle conflict resolution if server has newer version
        // 3. Update local entity with server_id
        // 4. Mark as synced in beneficiaries table
        
        Log.d(TAG, "Synced beneficiary: ${beneficiary.beneficiary_id}")
        
        return true  // Simulated success
    }
    
    /**
     * Sync health visit data
     */
    private suspend fun syncVisit(item: SyncQueueEntity, visitType: String): Boolean {
        // TODO: Implement actual API call
        kotlinx.coroutines.delay(50)
        Log.d(TAG, "Synced $visitType visit: ${item.entity_id}")
        return true
    }
    
    /**
     * Sync edit request
     */
    private suspend fun syncEditRequest(item: SyncQueueEntity): Boolean {
        // TODO: Implement actual API call
        kotlinx.coroutines.delay(50)
        Log.d(TAG, "Synced edit request: ${item.entity_id}")
        return true
    }
    
    /**
     * Sync duplicate flag
     */
    private suspend fun syncDuplicateFlag(item: SyncQueueEntity): Boolean {
        // TODO: Implement actual API call
        kotlinx.coroutines.delay(50)
        Log.d(TAG, "Synced duplicate flag: ${item.entity_id}")
        return true
    }
    
    /**
     * Sync audit log (highest priority)
     */
    private suspend fun syncAuditLog(item: SyncQueueEntity): Boolean {
        // TODO: Implement actual API call
        kotlinx.coroutines.delay(50)
        Log.d(TAG, "Synced audit log: ${item.entity_id}")
        return true
    }
    
    /**
     * Sync user data
     */
    private suspend fun syncUser(item: SyncQueueEntity): Boolean {
        // TODO: Implement actual API call
        kotlinx.coroutines.delay(50)
        Log.d(TAG, "Synced user: ${item.entity_id}")
        return true
    }
    
    /**
     * Sync village data
     */
    private suspend fun syncVillage(item: SyncQueueEntity): Boolean {
        // TODO: Implement actual API call
        kotlinx.coroutines.delay(50)
        Log.d(TAG, "Synced village: ${item.entity_id}")
        return true
    }
    
    /**
     * Calculate next retry time with exponential backoff
     */
    private fun calculateNextRetryTime(retryCount: Int): Long {
        // Exponential backoff: 5min, 10min, 20min, 40min, 80min
        val delayMinutes = 5 * (1 shl retryCount)  // 2^retryCount
        return System.currentTimeMillis() + (delayMinutes * 60 * 1000)
    }
    
    /**
     * Get sync statistics
     */
    suspend fun getSyncStats(): SyncStats = withContext(Dispatchers.IO) {
        val pendingCount = syncQueueDao.getPendingCount()
        val allPending = syncQueueDao.getPendingItems(1000)
        
        val byEntityType = allPending.groupBy { it.entity_type }
            .mapValues { it.value.size }
        
        SyncStats(
            totalPending = pendingCount,
            byEntityType = byEntityType,
            lastSyncTimestamp = 0  // TODO: Store and retrieve
        )
    }
}

/**
 * Sync statistics
 */
data class SyncStats(
    val totalPending: Int,
    val byEntityType: Map<String, Int>,
    val lastSyncTimestamp: Long
)
