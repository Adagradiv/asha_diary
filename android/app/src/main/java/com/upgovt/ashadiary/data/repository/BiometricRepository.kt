package com.upgovt.ashadiary.data.repository

import com.upgovt.ashadiary.data.local.dao.BiometricLocalDao
import com.upgovt.ashadiary.data.local.dao.BeneficiaryDao
import com.upgovt.ashadiary.data.local.entities.BiometricLocalEntity
import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import com.upgovt.ashadiary.utils.biometric.BiometricMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * BiometricRepository - Manages local biometric data
 * 
 * CRITICAL RULES:
 * 1. ALL fingerprint data stays LOCAL ONLY
 * 2. NEVER sync to server
 * 3. Encrypted at database level (SQLCipher)
 * 4. Used for duplicate detection only
 */
class BiometricRepository(
    private val biometricDao: BiometricLocalDao,
    private val beneficiaryDao: BeneficiaryDao,
    private val matcher: BiometricMatcher = BiometricMatcher()
) {
    
    /**
     * Store fingerprint template locally
     * 
     * @return BiometricLocalEntity if successful
     */
    suspend fun storeFingerprintTemplate(
        beneficiaryId: String,
        fingerPosition: String,
        isoTemplate: ByteArray,
        qualityScore: Int,
        capturedByUserId: String,
        deviceSerialNumber: String
    ): Result<BiometricLocalEntity> = withContext(Dispatchers.IO) {
        try {
            // Check if template for this finger already exists
            val existing = biometricDao.getByBeneficiaryAndFinger(beneficiaryId, fingerPosition)
            
            if (existing != null) {
                // Update existing
                biometricDao.delete(existing.id)
            }
            
            val biometric = BiometricLocalEntity(
                id = UUID.randomUUID().toString(),
                beneficiary_id = beneficiaryId,
                finger_position = fingerPosition,
                quality_score = qualityScore,
                iso_template = isoTemplate,
                captured_by_user_id = capturedByUserId,
                device_serial_number = deviceSerialNumber
            )
            
            biometricDao.insert(biometric)
            
            Result.success(biometric)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Search for matching fingerprint across ALL beneficiaries
     * Used for duplicate detection during registration
     * 
     * @return List of Pair<BeneficiaryEntity, matchScore> sorted by score descending
     */
    suspend fun searchMatchingFingerprints(
        queryTemplate: ByteArray,
        matchThreshold: Int = BiometricMatcher.MATCH_THRESHOLD,
        maxResults: Int = 10
    ): List<Pair<BeneficiaryEntity, Int>> = withContext(Dispatchers.IO) {
        try {
            // Get all stored biometric templates
            val allBiometrics = biometricDao.getAllBiometrics()
            
            if (allBiometrics.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Match against all templates
            val matches = mutableListOf<Triple<String, Int, Int>>()  // beneficiaryId, score, index
            
            allBiometrics.forEachIndexed { index, biometric ->
                val score = matcher.matchTemplates(queryTemplate, biometric.iso_template)
                if (score >= matchThreshold) {
                    matches.add(Triple(biometric.beneficiary_id, score, index))
                }
            }
            
            // Sort by score descending
            matches.sortByDescending { it.second }
            
            // Get beneficiary details for matches
            val results = mutableListOf<Pair<BeneficiaryEntity, Int>>()
            
            for (match in matches.take(maxResults)) {
                val beneficiary = beneficiaryDao.getBeneficiaryById(match.first)
                if (beneficiary != null) {
                    results.add(Pair(beneficiary, match.second))
                }
            }
            
            results
            
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Verify fingerprint against stored templates for a specific beneficiary
     * Used for beneficiary verification/authentication
     * 
     * @return Pair<isMatch, matchScore>
     */
    suspend fun verifyFingerprint(
        beneficiaryId: String,
        queryTemplate: ByteArray,
        matchThreshold: Int = BiometricMatcher.MATCH_THRESHOLD
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        try {
            val storedBiometrics = biometricDao.getByBeneficiary(beneficiaryId)
            
            if (storedBiometrics.isEmpty()) {
                return@withContext Pair(false, 0)
            }
            
            // Try to match against any stored finger
            var bestScore = 0
            for (biometric in storedBiometrics) {
                val score = matcher.matchTemplates(queryTemplate, biometric.iso_template)
                if (score > bestScore) {
                    bestScore = score
                }
            }
            
            Pair(bestScore >= matchThreshold, bestScore)
            
        } catch (e: Exception) {
            Pair(false, 0)
        }
    }
    
    /**
     * Get all fingerprints for a beneficiary
     */
    suspend fun getBiometricsForBeneficiary(beneficiaryId: String): List<BiometricLocalEntity> {
        return withContext(Dispatchers.IO) {
            biometricDao.getByBeneficiary(beneficiaryId)
        }
    }
    
    /**
     * Delete all fingerprints for a beneficiary
     * Use when beneficiary is merged/deleted
     */
    suspend fun deleteBiometricsForBeneficiary(beneficiaryId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                biometricDao.deleteByBeneficiary(beneficiaryId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get statistics
     */
    suspend fun getBiometricStats(): BiometricStats = withContext(Dispatchers.IO) {
        val allBiometrics = biometricDao.getAllBiometrics()
        val uniqueBeneficiaries = allBiometrics.map { it.beneficiary_id }.distinct().size
        val avgQuality = if (allBiometrics.isNotEmpty()) {
            allBiometrics.map { it.quality_score }.average().toInt()
        } else 0
        
        BiometricStats(
            totalFingerprints = allBiometrics.size,
            uniqueBeneficiaries = uniqueBeneficiaries,
            averageQuality = avgQuality
        )
    }
}

/**
 * Biometric statistics
 */
data class BiometricStats(
    val totalFingerprints: Int,
    val uniqueBeneficiaries: Int,
    val averageQuality: Int
)
