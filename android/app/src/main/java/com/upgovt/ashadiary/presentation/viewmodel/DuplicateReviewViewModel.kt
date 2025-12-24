package com.upgovt.ashadiary.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.data.local.database.DatabaseEncryption
import com.upgovt.ashadiary.data.local.entities.DuplicateFlagEntity
import com.upgovt.ashadiary.domain.duplicate.DuplicateMergeManager
import com.upgovt.ashadiary.domain.duplicate.MergeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * DuplicateReviewViewModel - For Block Nodal to review and resolve duplicates
 */
class DuplicateReviewViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = ASHADiaryDatabase.getInstance(
        application,
        DatabaseEncryption.getOrCreatePassphrase(application)
    )
    
    private val mergeManager = DuplicateMergeManager(
        beneficiaryDao = database.beneficiaryDao(),
        duplicateFlagDao = database.duplicateFlagDao(),
        biometricDao = database.biometricLocalDao(),
        ancVisitDao = database.ancVisitDao(),
        bpVisitDao = database.bpVisitDao(),
        bloodSugarVisitDao = database.bloodSugarVisitDao(),
        vaccinationVisitDao = database.vaccinationVisitDao(),
        auditLogDao = database.auditLogDao()
    )
    
    // Pending duplicate flags
    val pendingDuplicates: Flow<List<DuplicateFlagEntity>> = 
        database.duplicateFlagDao().observePendingFlags()
    
    val pendingCount: Flow<Int> = 
        database.duplicateFlagDao().observePendingCount()
    
    // Merge operation state
    private val _mergeState = MutableLiveData<MergeState>(MergeState.Idle)
    val mergeState: LiveData<MergeState> = _mergeState
    
    /**
     * Merge duplicate into master
     */
    fun mergeDuplicate(
        duplicateBeneficiaryId: String,
        masterBeneficiaryId: String,
        userId: String,
        userRoleId: Int,
        notes: String?
    ) {
        _mergeState.value = MergeState.Merging
        
        viewModelScope.launch {
            val result = mergeManager.mergeDuplicateIntoMaster(
                duplicateBeneficiaryId = duplicateBeneficiaryId,
                masterBeneficiaryId = masterBeneficiaryId,
                userId = userId,
                userRoleId = userRoleId,
                notes = notes
            )
            
            if (result.isSuccess) {
                val mergeResult = result.getOrNull()!!
                _mergeState.value = MergeState.Success(
                    message = mergeResult.message,
                    transferredVisits = mergeResult.transferredVisits.totalVisits
                )
            } else {
                _mergeState.value = MergeState.Error(
                    result.exceptionOrNull()?.message ?: "मर्ज विफल"
                )
            }
        }
    }
    
    /**
     * Mark as not duplicate (keep both)
     */
    fun markAsNotDuplicate(
        flagId: String,
        userId: String,
        userRoleId: Int,
        notes: String
    ) {
        viewModelScope.launch {
            database.duplicateFlagDao().resolveFlag(
                flagId = flagId,
                status = "KEPT_BOTH",
                action = "KEEP_BOTH",
                notes = notes,
                resolvedByUserId = userId
            )
            
            _mergeState.value = MergeState.Success(
                message = "दोनों लाभार्थी सक्रिय रखे गए",
                transferredVisits = 0
            )
        }
    }
    
    /**
     * Inactivate duplicate
     */
    fun inactivateDuplicate(
        beneficiaryId: String,
        userId: String,
        userRoleId: Int,
        reason: String
    ) {
        _mergeState.value = MergeState.Merging
        
        viewModelScope.launch {
            val result = mergeManager.inactivateDuplicate(
                beneficiaryId = beneficiaryId,
                userId = userId,
                userRoleId = userRoleId,
                reason = reason
            )
            
            if (result.isSuccess) {
                _mergeState.value = MergeState.Success(
                    message = "लाभार्थी निष्क्रिय हो गया",
                    transferredVisits = 0
                )
            } else {
                _mergeState.value = MergeState.Error(
                    result.exceptionOrNull()?.message ?: "निष्क्रिय करना विफल"
                )
            }
        }
    }
    
    /**
     * Reset merge state
     */
    fun resetMergeState() {
        _mergeState.value = MergeState.Idle
    }
}

/**
 * Merge operation state
 */
sealed class MergeState {
    object Idle : MergeState()
    object Merging : MergeState()
    data class Success(val message: String, val transferredVisits: Int) : MergeState()
    data class Error(val message: String) : MergeState()
}
