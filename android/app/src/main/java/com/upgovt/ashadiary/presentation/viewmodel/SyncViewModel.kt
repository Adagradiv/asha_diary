package com.upgovt.ashadiary.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.data.local.database.DatabaseEncryption
import com.upgovt.ashadiary.data.sync.SyncManager
import com.upgovt.ashadiary.data.sync.SyncResult
import com.upgovt.ashadiary.data.sync.SyncStats
import com.upgovt.ashadiary.data.sync.SyncWorker
import com.upgovt.ashadiary.utils.network.NetworkMonitor
import com.upgovt.ashadiary.utils.network.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * SyncViewModel - ViewModel for sync status and manual sync triggers
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = ASHADiaryDatabase.getInstance(
        application,
        DatabaseEncryption.getOrCreatePassphrase(application)
    )
    
    private val networkMonitor = NetworkMonitor(application)
    private val syncManager = SyncManager(application, database, networkMonitor)
    private val workManager = WorkManager.getInstance(application)
    
    // Network status
    private val _networkStatus = MutableStateFlow(NetworkStatus(
        isConnected = networkMonitor.isNetworkAvailable(),
        networkType = networkMonitor.getNetworkType()
    ))
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus
    
    // Sync status
    private val _syncState = MutableLiveData<SyncState>(SyncState.Idle)
    val syncState: LiveData<SyncState> = _syncState
    
    // Sync statistics
    private val _syncStats = MutableLiveData<SyncStats>()
    val syncStats: LiveData<SyncStats> = _syncStats
    
    // Periodic sync status
    private val _periodicSyncEnabled = MutableLiveData<Boolean>(false)
    val periodicSyncEnabled: LiveData<Boolean> = _periodicSyncEnabled
    
    init {
        // Observe network changes
        viewModelScope.launch {
            networkMonitor.observeNetworkConnectivity().collect { status ->
                _networkStatus.value = status
            }
        }
        
        // Load initial stats
        loadSyncStats()
        
        // Check if periodic sync is enabled
        checkPeriodicSyncStatus()
    }
    
    /**
     * Trigger manual sync
     */
    fun triggerManualSync() {
        if (!_networkStatus.value.isConnected) {
            _syncState.value = SyncState.Error("नेटवर्क उपलब्ध नहीं है")
            return
        }
        
        _syncState.value = SyncState.Syncing
        
        viewModelScope.launch {
            try {
                val result = syncManager.executeSync()
                
                when (result.status) {
                    com.upgovt.ashadiary.data.sync.SyncStatus.SUCCESS -> {
                        _syncState.value = SyncState.Success(result.summary)
                    }
                    com.upgovt.ashadiary.data.sync.SyncStatus.PARTIAL -> {
                        _syncState.value = SyncState.PartialSuccess(
                            result.summary,
                            result.syncedCount,
                            result.failedCount
                        )
                    }
                    com.upgovt.ashadiary.data.sync.SyncStatus.FAILED -> {
                        _syncState.value = SyncState.Error(result.errorMessage ?: "सिंक विफल")
                    }
                }
                
                // Reload stats
                loadSyncStats()
                
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "सिंक में त्रुटि")
            }
        }
    }
    
    /**
     * Enable periodic background sync (every 2 hours)
     */
    fun enablePeriodicSync() {
        SyncWorker.schedulePeriodic(getApplication())
        _periodicSyncEnabled.value = true
    }
    
    /**
     * Disable periodic background sync
     */
    fun disablePeriodicSync() {
        SyncWorker.cancelSync(getApplication())
        _periodicSyncEnabled.value = false
    }
    
    /**
     * Trigger immediate background sync via WorkManager
     */
    fun triggerImmediateBackgroundSync() {
        SyncWorker.triggerImmediateSync(getApplication())
        _syncState.value = SyncState.Syncing
        
        // Observe work status
        observeWorkStatus()
    }
    
    /**
     * Load sync statistics
     */
    fun loadSyncStats() {
        viewModelScope.launch {
            val stats = syncManager.getSyncStats()
            _syncStats.value = stats
        }
    }
    
    /**
     * Reset sync state to idle
     */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
    
    /**
     * Check if periodic sync is enabled
     */
    private fun checkPeriodicSyncStatus() {
        viewModelScope.launch {
            val workInfos = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME).get()
            _periodicSyncEnabled.value = workInfos.any { 
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING 
            }
        }
    }
    
    /**
     * Observe WorkManager sync status
     */
    private fun observeWorkStatus() {
        workManager.getWorkInfosByTagLiveData("immediate_sync")
            .observeForever { workInfos ->
                workInfos.firstOrNull()?.let { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val syncedCount = workInfo.outputData.getInt("synced_count", 0)
                            val failedCount = workInfo.outputData.getInt("failed_count", 0)
                            
                            if (failedCount == 0) {
                                _syncState.value = SyncState.Success("$syncedCount आइटम सिंक हुए")
                            } else {
                                _syncState.value = SyncState.PartialSuccess(
                                    "$syncedCount सफल, $failedCount विफल",
                                    syncedCount,
                                    failedCount
                                )
                            }
                            
                            loadSyncStats()
                        }
                        WorkInfo.State.FAILED -> {
                            _syncState.value = SyncState.Error("सिंक विफल")
                        }
                        WorkInfo.State.RUNNING -> {
                            _syncState.value = SyncState.Syncing
                        }
                        else -> {}
                    }
                }
            }
    }
}

/**
 * Sync state for UI
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class PartialSuccess(
        val message: String,
        val syncedCount: Int,
        val failedCount: Int
    ) : SyncState()
    data class Error(val message: String) : SyncState()
}
