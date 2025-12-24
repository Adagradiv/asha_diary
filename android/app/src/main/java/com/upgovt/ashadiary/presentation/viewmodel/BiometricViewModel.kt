package com.upgovt.ashadiary.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.upgovt.ashadiary.data.repository.BiometricRepository
import com.upgovt.ashadiary.utils.biometric.*
import kotlinx.coroutines.launch

/**
 * BiometricViewModel - ViewModel for biometric capture and verification
 * Used in beneficiary registration and verification flows
 */
class BiometricViewModel(
    application: Application,
    private val biometricRepository: BiometricRepository
) : AndroidViewModel(application) {
    
    private val biometricHelper = BiometricHelper(application, biometricRepository)
    
    // LiveData for UI state
    private val _captureState = MutableLiveData<BiometricCaptureState>()
    val captureState: LiveData<BiometricCaptureState> = _captureState
    
    private val _searchResults = MutableLiveData<BiometricSearchResult>()
    val searchResults: LiveData<BiometricSearchResult> = _searchResults
    
    private val _verificationResult = MutableLiveData<BiometricVerificationResult>()
    val verificationResult: LiveData<BiometricVerificationResult> = _verificationResult
    
    private val _stats = MutableLiveData<BiometricStats>()
    val stats: LiveData<BiometricStats> = _stats
    
    // Temporary storage for captured result
    private var lastCaptureResult: BiometricCaptureResult? = null
    
    /**
     * Check if biometric capture is available
     */
    fun checkAvailability(): BiometricAvailability {
        return biometricHelper.isBiometricCaptureAvailable()
    }
    
    /**
     * Process capture result from Mantra RD Service
     */
    fun processCaptureResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result = biometricHelper.processCaptureResult(requestCode, resultCode, data)
        
        if (result != null) {
            lastCaptureResult = result
            
            if (result.success) {
                _captureState.value = BiometricCaptureState.Success(
                    qualityScore = result.qualityScore,
                    message = "फिंगरप्रिंट कैप्चर सफल (गुणवत्ता: ${result.qualityScore})"
                )
            } else {
                _captureState.value = BiometricCaptureState.Error(
                    message = result.errorMessage ?: "फिंगरप्रिंट कैप्चर विफल"
                )
            }
        } else {
            _captureState.value = BiometricCaptureState.Error("कोई परिणाम प्राप्त नहीं हुआ")
        }
    }
    
    /**
     * Store captured fingerprint for beneficiary
     */
    fun storeFingerprint(
        beneficiaryId: String,
        fingerPosition: String,
        capturedByUserId: String
    ) {
        val captureResult = lastCaptureResult
        
        if (captureResult == null) {
            _captureState.value = BiometricCaptureState.Error("पहले फिंगरप्रिंट कैप्चर करें")
            return
        }
        
        _captureState.value = BiometricCaptureState.Storing
        
        viewModelScope.launch {
            val result = biometricHelper.storeFingerprintForBeneficiary(
                captureResult = captureResult,
                beneficiaryId = beneficiaryId,
                fingerPosition = fingerPosition,
                capturedByUserId = capturedByUserId
            )
            
            if (result.isSuccess) {
                _captureState.value = BiometricCaptureState.Stored("फिंगरप्रिंट सफलतापूर्वक स्टोर हो गया")
                lastCaptureResult = null  // Clear after successful storage
            } else {
                _captureState.value = BiometricCaptureState.Error(
                    result.exceptionOrNull()?.message ?: "स्टोर करने में विफल"
                )
            }
        }
    }
    
    /**
     * Search for duplicate beneficiaries using captured fingerprint
     */
    fun searchDuplicates() {
        val captureResult = lastCaptureResult
        
        if (captureResult == null) {
            _searchResults.value = BiometricSearchResult(
                success = false,
                matches = emptyList(),
                errorMessage = "पहले फिंगरप्रिंट कैप्चर करें"
            )
            return
        }
        
        viewModelScope.launch {
            val result = biometricHelper.searchDuplicatesByFingerprint(captureResult)
            _searchResults.value = result
        }
    }
    
    /**
     * Verify beneficiary identity
     */
    fun verifyBeneficiary(beneficiaryId: String) {
        val captureResult = lastCaptureResult
        
        if (captureResult == null) {
            _verificationResult.value = BiometricVerificationResult(
                isVerified = false,
                matchScore = 0,
                errorMessage = "पहले फिंगरप्रिंट कैप्चर करें"
            )
            return
        }
        
        viewModelScope.launch {
            val result = biometricHelper.verifyBeneficiary(captureResult, beneficiaryId)
            _verificationResult.value = result
        }
    }
    
    /**
     * Load biometric statistics
     */
    fun loadStats() {
        viewModelScope.launch {
            val statistics = biometricHelper.getBiometricStats()
            _stats.value = statistics
        }
    }
    
    /**
     * Reset capture state
     */
    fun resetCaptureState() {
        lastCaptureResult = null
        _captureState.value = BiometricCaptureState.Idle
    }
    
    /**
     * Clear search results
     */
    fun clearSearchResults() {
        _searchResults.value = BiometricSearchResult(
            success = true,
            matches = emptyList(),
            errorMessage = null
        )
    }
}

/**
 * Biometric capture state for UI
 */
sealed class BiometricCaptureState {
    object Idle : BiometricCaptureState()
    data class Success(val qualityScore: Int, val message: String) : BiometricCaptureState()
    object Storing : BiometricCaptureState()
    data class Stored(val message: String) : BiometricCaptureState()
    data class Error(val message: String) : BiometricCaptureState()
}
