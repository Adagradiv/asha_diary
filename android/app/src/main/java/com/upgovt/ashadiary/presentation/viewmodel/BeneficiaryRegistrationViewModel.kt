package com.upgovt.ashadiary.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.upgovt.ashadiary.data.local.database.ASHADiaryDatabase
import com.upgovt.ashadiary.data.local.database.DatabaseEncryption
import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import com.upgovt.ashadiary.data.repository.BiometricRepository
import com.upgovt.ashadiary.domain.duplicate.DuplicateScore
import com.upgovt.ashadiary.domain.usecases.RegisterBeneficiaryUseCase
import com.upgovt.ashadiary.domain.usecases.BeneficiaryRegistrationRequest
import com.upgovt.ashadiary.domain.usecases.RegistrationResult
import kotlinx.coroutines.launch

/**
 * BeneficiaryRegistrationViewModel - Handles beneficiary registration flow
 * Integrates duplicate detection and biometric capture
 */
class BeneficiaryRegistrationViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = ASHADiaryDatabase.getInstance(
        application,
        DatabaseEncryption.getOrCreatePassphrase(application)
    )
    
    private val biometricRepository = BiometricRepository(
        database.biometricLocalDao(),
        database.beneficiaryDao()
    )
    
    private val registerBeneficiaryUseCase = RegisterBeneficiaryUseCase(
        beneficiaryDao = database.beneficiaryDao(),
        villageDao = database.villageDao(),
        duplicateFlagDao = database.duplicateFlagDao(),
        biometricRepository = biometricRepository,
        auditLogDao = database.auditLogDao(),
        syncQueueDao = database.syncQueueDao()
    )
    
    // Registration state
    private val _registrationState = MutableLiveData<RegistrationState>(RegistrationState.Idle)
    val registrationState: LiveData<RegistrationState> = _registrationState
    
    // Duplicate matches
    private val _duplicateMatches = MutableLiveData<List<Pair<BeneficiaryEntity, DuplicateScore>>>()
    val duplicateMatches: LiveData<List<Pair<BeneficiaryEntity, DuplicateScore>>> = _duplicateMatches
    
    // Temporary storage for registration request
    private var pendingRequest: BeneficiaryRegistrationRequest? = null
    
    /**
     * Register beneficiary
     */
    fun registerBeneficiary(request: BeneficiaryRegistrationRequest) {
        _registrationState.value = RegistrationState.Registering
        
        viewModelScope.launch {
            val result = registerBeneficiaryUseCase.execute(request)
            
            when (result) {
                is RegistrationResult.Success -> {
                    _registrationState.value = RegistrationState.Success(
                        beneficiaryId = result.beneficiary.beneficiary_id,
                        message = "लाभार्थी सफलतापूर्वक पंजीकृत: ${result.beneficiary.beneficiary_id}"
                    )
                }
                is RegistrationResult.DuplicatesFound -> {
                    // Store pending request for retry
                    pendingRequest = request
                    
                    _duplicateMatches.value = result.duplicates
                    _registrationState.value = RegistrationState.DuplicatesDetected(
                        count = result.duplicates.size,
                        highestScore = result.duplicates.firstOrNull()?.second?.totalScore ?: 0
                    )
                }
                is RegistrationResult.Error -> {
                    _registrationState.value = RegistrationState.Error(result.message)
                }
            }
        }
    }
    
    /**
     * Confirm registration despite duplicates
     */
    fun confirmRegistrationWithDuplicates() {
        val request = pendingRequest?.copy(confirmDuplicateOverride = true)
        
        if (request != null) {
            registerBeneficiary(request)
        } else {
            _registrationState.value = RegistrationState.Error("कोई लंबित अनुरोध नहीं")
        }
    }
    
    /**
     * Cancel registration
     */
    fun cancelRegistration() {
        pendingRequest = null
        _duplicateMatches.value = emptyList()
        _registrationState.value = RegistrationState.Idle
    }
    
    /**
     * Reset state
     */
    fun resetState() {
        _registrationState.value = RegistrationState.Idle
        _duplicateMatches.value = emptyList()
        pendingRequest = null
    }
}

/**
 * Registration state for UI
 */
sealed class RegistrationState {
    object Idle : RegistrationState()
    object Registering : RegistrationState()
    data class Success(val beneficiaryId: String, val message: String) : RegistrationState()
    data class DuplicatesDetected(val count: Int, val highestScore: Int) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}
