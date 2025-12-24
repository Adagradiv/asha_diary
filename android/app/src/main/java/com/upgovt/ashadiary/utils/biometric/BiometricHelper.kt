package com.upgovt.ashadiary.utils.biometric

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.upgovt.ashadiary.data.repository.BiometricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BiometricHelper - High-level helper for biometric operations
 * Combines Mantra RD Service integration with local storage and matching
 */
class BiometricHelper(
    private val context: Context,
    private val biometricRepository: BiometricRepository
) {
    
    private val mantraHelper = MantraRDServiceHelper(context)
    
    companion object {
        private const val TAG = "BiometricHelper"
    }
    
    /**
     * Check if biometric capture is available
     */
    fun isBiometricCaptureAvailable(): BiometricAvailability {
        val isInstalled = mantraHelper.isMantraServiceInstalled()
        
        return BiometricAvailability(
            isAvailable = isInstalled,
            errorMessage = if (!isInstalled) {
                "Mantra MFS100 RD Service नहीं मिली। कृपया Mantra ड्राइवर इंस्टॉल करें।"
            } else null
        )
    }
    
    /**
     * Capture fingerprint from device
     * Launch Mantra RD Service activity
     */
    fun captureFingerprint(activity: Activity) {
        try {
            mantraHelper.captureFingerprint(activity)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to capture fingerprint", e)
            throw BiometricException("फिंगरप्रिंट कैप्चर शुरू नहीं हो सका: ${e.message}")
        }
    }
    
    /**
     * Process captured fingerprint result
     * Call this from Activity.onActivityResult()
     */
    fun processCaptureResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): BiometricCaptureResult? {
        return mantraHelper.handleActivityResult(requestCode, resultCode, data)
    }
    
    /**
     * Complete workflow: Store captured fingerprint for beneficiary
     */
    suspend fun storeFingerprintForBeneficiary(
        captureResult: BiometricCaptureResult,
        beneficiaryId: String,
        fingerPosition: String,
        capturedByUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!captureResult.success || captureResult.isoTemplate == null) {
            return@withContext Result.failure(
                BiometricException(captureResult.errorMessage ?: "फिंगरप्रिंट कैप्चर विफल")
            )
        }
        
        if (captureResult.qualityScore < MantraRDServiceConstants.MIN_QUALITY_SCORE) {
            return@withContext Result.failure(
                BiometricException("फिंगरप्रिंट गुणवत्ता बहुत कम है (${captureResult.qualityScore}). कृपया फिर से प्रयास करें।")
            )
        }
        
        val result = biometricRepository.storeFingerprintTemplate(
            beneficiaryId = beneficiaryId,
            fingerPosition = fingerPosition,
            isoTemplate = captureResult.isoTemplate,
            qualityScore = captureResult.qualityScore,
            capturedByUserId = capturedByUserId,
            deviceSerialNumber = captureResult.deviceSerialNumber
        )
        
        if (result.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: BiometricException("फिंगरप्रिंट स्टोर नहीं हो सका"))
        }
    }
    
    /**
     * Search for duplicate beneficiaries using fingerprint
     * Returns list of potential matches with scores
     */
    suspend fun searchDuplicatesByFingerprint(
        captureResult: BiometricCaptureResult
    ): BiometricSearchResult = withContext(Dispatchers.IO) {
        if (!captureResult.success || captureResult.isoTemplate == null) {
            return@withContext BiometricSearchResult(
                success = false,
                matches = emptyList(),
                errorMessage = captureResult.errorMessage ?: "अमान्य फिंगरप्रिंट"
            )
        }
        
        try {
            val matches = biometricRepository.searchMatchingFingerprints(
                queryTemplate = captureResult.isoTemplate,
                matchThreshold = BiometricMatcher.MATCH_THRESHOLD,
                maxResults = 10
            )
            
            BiometricSearchResult(
                success = true,
                matches = matches.map { (beneficiary, score) ->
                    BiometricMatch(
                        beneficiaryId = beneficiary.beneficiary_id,
                        beneficiaryName = beneficiary.name_hindi,
                        matchScore = score,
                        confidence = when {
                            score >= BiometricMatcher.HIGH_CONFIDENCE_THRESHOLD -> MatchConfidence.HIGH
                            score >= BiometricMatcher.MATCH_THRESHOLD + 10 -> MatchConfidence.MEDIUM
                            else -> MatchConfidence.LOW
                        }
                    )
                },
                errorMessage = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching duplicates", e)
            BiometricSearchResult(
                success = false,
                matches = emptyList(),
                errorMessage = "खोज विफल: ${e.message}"
            )
        }
    }
    
    /**
     * Verify beneficiary identity using fingerprint
     */
    suspend fun verifyBeneficiary(
        captureResult: BiometricCaptureResult,
        beneficiaryId: String
    ): BiometricVerificationResult = withContext(Dispatchers.IO) {
        if (!captureResult.success || captureResult.isoTemplate == null) {
            return@withContext BiometricVerificationResult(
                isVerified = false,
                matchScore = 0,
                errorMessage = captureResult.errorMessage ?: "अमान्य फिंगरप्रिंट"
            )
        }
        
        try {
            val (isMatch, score) = biometricRepository.verifyFingerprint(
                beneficiaryId = beneficiaryId,
                queryTemplate = captureResult.isoTemplate
            )
            
            BiometricVerificationResult(
                isVerified = isMatch,
                matchScore = score,
                errorMessage = if (!isMatch) "फिंगरप्रिंट मैच नहीं हुआ" else null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying beneficiary", e)
            BiometricVerificationResult(
                isVerified = false,
                matchScore = 0,
                errorMessage = "सत्यापन विफल: ${e.message}"
            )
        }
    }
    
    /**
     * Get fingerprint statistics
     */
    suspend fun getBiometricStats() = biometricRepository.getBiometricStats()
}

/**
 * Biometric availability status
 */
data class BiometricAvailability(
    val isAvailable: Boolean,
    val errorMessage: String?
)

/**
 * Biometric search result
 */
data class BiometricSearchResult(
    val success: Boolean,
    val matches: List<BiometricMatch>,
    val errorMessage: String?
)

/**
 * Single biometric match
 */
data class BiometricMatch(
    val beneficiaryId: String,
    val beneficiaryName: String,
    val matchScore: Int,
    val confidence: MatchConfidence
)

/**
 * Match confidence
 */
enum class MatchConfidence {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Biometric verification result
 */
data class BiometricVerificationResult(
    val isVerified: Boolean,
    val matchScore: Int,
    val errorMessage: String?
)

/**
 * Biometric exception
 */
class BiometricException(message: String) : Exception(message)
