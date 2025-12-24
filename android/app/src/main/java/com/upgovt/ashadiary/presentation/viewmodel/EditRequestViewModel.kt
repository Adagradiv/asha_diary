package com.upgovt.ashadiary.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.data.local.database.DatabaseEncryption
import com.upgovt.ashadiary.data.local.entities.EditRequestEntity
import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import com.upgovt.ashadiary.domain.editrequest.EditRequestWorkflowManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * EditRequestViewModel - Manages edit request workflow
 * Used by Field Staff, Supervisors, and Block Nodal
 */
class EditRequestViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = ASHADiaryDatabase.getInstance(
        application,
        DatabaseEncryption.getOrCreatePassphrase(application)
    )
    
    private val workflowManager = EditRequestWorkflowManager(
        editRequestDao = database.editRequestDao(),
        beneficiaryDao = database.beneficiaryDao(),
        auditLogDao = database.auditLogDao()
    )
    
    // Pending requests (for Supervisor/Block Nodal)
    val pendingRequests: Flow<List<EditRequestEntity>> = 
        workflowManager.getPendingRequests()
    
    val pendingCount: Flow<Int> = 
        database.editRequestDao().observePendingCount()
    
    // Edit request state
    private val _requestState = MutableLiveData<EditRequestState>(EditRequestState.Idle)
    val requestState: LiveData<EditRequestState> = _requestState
    
    /**
     * Submit edit request (Field Staff)
     */
    fun submitEditRequest(
        beneficiaryId: String,
        fieldName: String,
        currentValue: String,
        requestedValue: String,
        reason: String,
        userId: String,
        userRoleId: Int,
        gpsLat: Double,
        gpsLng: Double
    ) {
        _requestState.value = EditRequestState.Submitting
        
        viewModelScope.launch {
            val result = workflowManager.submitEditRequest(
                beneficiaryId = beneficiaryId,
                fieldName = fieldName,
                currentValue = currentValue,
                requestedValue = requestedValue,
                reason = reason,
                requestedByUserId = userId,
                requestedByRoleId = userRoleId,
                gpsLat = gpsLat,
                gpsLng = gpsLng
            )
            
            if (result.isSuccess) {
                _requestState.value = EditRequestState.Submitted(
                    "संपादन अनुरोध सबमिट हो गया। सुपरवाइजर समीक्षा करेंगे।"
                )
            } else {
                _requestState.value = EditRequestState.Error(
                    result.exceptionOrNull()?.message ?: "अनुरोध सबमिट विफल"
                )
            }
        }
    }
    
    /**
     * Approve edit request (Supervisor/Block Nodal)
     */
    fun approveRequest(
        requestId: String,
        userId: String,
        userRoleId: Int,
        reviewNotes: String? = null
    ) {
        _requestState.value = EditRequestState.Processing
        
        viewModelScope.launch {
            val result = workflowManager.approveEditRequest(
                requestId = requestId,
                reviewedByUserId = userId,
                reviewedByRoleId = userRoleId,
                reviewNotes = reviewNotes
            )
            
            if (result.isSuccess) {
                _requestState.value = EditRequestState.Approved(
                    "संपादन अनुरोध स्वीकृत। लाभार्थी अपडेट हो गया।"
                )
            } else {
                _requestState.value = EditRequestState.Error(
                    result.exceptionOrNull()?.message ?: "स्वीकृति विफल"
                )
            }
        }
    }
    
    /**
     * Reject edit request (Supervisor/Block Nodal)
     */
    fun rejectRequest(
        requestId: String,
        userId: String,
        userRoleId: Int,
        reviewNotes: String
    ) {
        _requestState.value = EditRequestState.Processing
        
        viewModelScope.launch {
            val result = workflowManager.rejectEditRequest(
                requestId = requestId,
                reviewedByUserId = userId,
                reviewedByRoleId = userRoleId,
                reviewNotes = reviewNotes
            )
            
            if (result.isSuccess) {
                _requestState.value = EditRequestState.Rejected(
                    "संपादन अनुरोध अस्वीकृत।"
                )
            } else {
                _requestState.value = EditRequestState.Error(
                    result.exceptionOrNull()?.message ?: "अस्वीकृति विफल"
                )
            }
        }
    }
    
    /**
     * Get user's submitted requests (Field Staff tracking)
     */
    fun getMyRequests(userId: String): Flow<List<EditRequestEntity>> {
        return workflowManager.getRequestsByUser(userId)
    }
    
    /**
     * Get requests for a beneficiary
     */
    fun getRequestsForBeneficiary(beneficiaryId: String): Flow<List<EditRequestEntity>> {
        return workflowManager.getRequestsForBeneficiary(beneficiaryId)
    }
    
    /**
     * Reset state
     */
    fun resetState() {
        _requestState.value = EditRequestState.Idle
    }
}

/**
 * Edit request state for UI
 */
sealed class EditRequestState {
    object Idle : EditRequestState()
    object Submitting : EditRequestState()
    object Processing : EditRequestState()
    data class Submitted(val message: String) : EditRequestState()
    data class Approved(val message: String) : EditRequestState()
    data class Rejected(val message: String) : EditRequestState()
    data class Error(val message: String) : EditRequestState()
}
