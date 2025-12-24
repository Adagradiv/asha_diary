package com.upgovt.ashadiary.domain.duplicate

import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import com.upgovt.ashadiary.data.local.entities.BiometricLocalEntity
import kotlin.math.abs

/**
 * DuplicateDetectionEngine - Advanced duplicate detection with scoring
 * 
 * Detection criteria:
 * 1. Name similarity (Levenshtein distance)
 * 2. Village match
 * 3. Gender match
 * 4. Age within ±2 years
 * 5. Optional: Biometric match
 * 
 * Scoring: 0-100 (100 = exact match)
 */
class DuplicateDetectionEngine {
    
    companion object {
        // Match thresholds
        const val EXACT_MATCH_THRESHOLD = 90  // 90+ = Likely same person
        const val POSSIBLE_MATCH_THRESHOLD = 70  // 70-89 = Possible duplicate
        const val LOW_MATCH_THRESHOLD = 50  // 50-69 = Review recommended
        
        // Weights for scoring
        private const val WEIGHT_NAME = 40
        private const val WEIGHT_VILLAGE = 20
        private const val WEIGHT_GENDER = 15
        private const val WEIGHT_AGE = 15
        private const val WEIGHT_BIOMETRIC = 10
    }
    
    /**
     * Calculate duplicate score between two beneficiaries
     * @return DuplicateScore with breakdown
     */
    fun calculateDuplicateScore(
        beneficiary1: BeneficiaryEntity,
        beneficiary2: BeneficiaryEntity,
        biometric1: BiometricLocalEntity? = null,
        biometric2: BiometricLocalEntity? = null
    ): DuplicateScore {
        
        // 1. Name similarity (40 points)
        val nameScore = calculateNameSimilarity(
            beneficiary1.name_hindi,
            beneficiary2.name_hindi
        ) * WEIGHT_NAME / 100
        
        // 2. Village match (20 points)
        val villageScore = if (beneficiary1.village_id == beneficiary2.village_id) {
            WEIGHT_VILLAGE
        } else {
            0
        }
        
        // 3. Gender match (15 points)
        val genderScore = if (beneficiary1.gender == beneficiary2.gender) {
            WEIGHT_GENDER
        } else {
            0
        }
        
        // 4. Age similarity (15 points)
        val ageScore = calculateAgeSimilarity(
            beneficiary1.age_years,
            beneficiary2.age_years
        ) * WEIGHT_AGE / 100
        
        // 5. Biometric match (10 points) - if available
        val biometricScore = if (biometric1 != null && biometric2 != null) {
            calculateBiometricSimilarity(biometric1.iso_template, biometric2.iso_template) * WEIGHT_BIOMETRIC / 100
        } else {
            0
        }
        
        val totalScore = nameScore + villageScore + genderScore + ageScore + biometricScore
        
        return DuplicateScore(
            totalScore = totalScore.toInt(),
            nameScore = nameScore.toInt(),
            villageMatch = villageScore > 0,
            genderMatch = genderScore > 0,
            ageScore = ageScore.toInt(),
            biometricScore = biometricScore.toInt(),
            confidence = getConfidenceLevel(totalScore.toInt()),
            recommendation = getRecommendation(totalScore.toInt())
        )
    }
    
    /**
     * Calculate name similarity using Levenshtein distance
     * Returns: 0-100 (100 = exact match)
     */
    private fun calculateNameSimilarity(name1: String, name2: String): Int {
        if (name1 == name2) return 100
        
        val name1Clean = name1.trim().lowercase()
        val name2Clean = name2.trim().lowercase()
        
        if (name1Clean == name2Clean) return 100
        
        // Levenshtein distance
        val distance = levenshteinDistance(name1Clean, name2Clean)
        val maxLen = maxOf(name1Clean.length, name2Clean.length)
        
        if (maxLen == 0) return 0
        
        // Convert to similarity percentage
        val similarity = ((maxLen - distance).toFloat() / maxLen * 100).toInt()
        
        return similarity.coerceIn(0, 100)
    }
    
    /**
     * Levenshtein distance algorithm
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    /**
     * Calculate age similarity
     * Returns: 0-100 (100 = exact match, decreases with difference)
     */
    private fun calculateAgeSimilarity(age1: Int, age2: Int): Int {
        val diff = abs(age1 - age2)
        
        return when {
            diff == 0 -> 100
            diff == 1 -> 85
            diff == 2 -> 70
            diff == 3 -> 50
            diff <= 5 -> 30
            else -> 0
        }
    }
    
    /**
     * Calculate biometric similarity (placeholder)
     * In production, use BiometricMatcher
     */
    private fun calculateBiometricSimilarity(template1: ByteArray, template2: ByteArray): Int {
        // Use BiometricMatcher from Phase 2
        val matcher = com.upgovt.ashadiary.utils.biometric.BiometricMatcher()
        return matcher.matchTemplates(template1, template2)
    }
    
    /**
     * Get confidence level from score
     */
    private fun getConfidenceLevel(score: Int): DuplicateConfidence {
        return when {
            score >= EXACT_MATCH_THRESHOLD -> DuplicateConfidence.HIGH
            score >= POSSIBLE_MATCH_THRESHOLD -> DuplicateConfidence.MEDIUM
            score >= LOW_MATCH_THRESHOLD -> DuplicateConfidence.LOW
            else -> DuplicateConfidence.VERY_LOW
        }
    }
    
    /**
     * Get recommendation from score
     */
    private fun getRecommendation(score: Int): String {
        return when {
            score >= EXACT_MATCH_THRESHOLD -> "यह संभवतः डुप्लिकेट है। पंजीकरण रोकें।"
            score >= POSSIBLE_MATCH_THRESHOLD -> "डुप्लिकेट हो सकता है। सुपरवाइजर से समीक्षा करवाएं।"
            score >= LOW_MATCH_THRESHOLD -> "समानता कम है। चेतावनी दें और जारी रखें।"
            else -> "डुप्लिकेट नहीं। सुरक्षित रूप से पंजीकृत करें।"
        }
    }
}

/**
 * Duplicate score result
 */
data class DuplicateScore(
    val totalScore: Int,  // 0-100
    val nameScore: Int,
    val villageMatch: Boolean,
    val genderMatch: Boolean,
    val ageScore: Int,
    val biometricScore: Int,
    val confidence: DuplicateConfidence,
    val recommendation: String
)

/**
 * Duplicate confidence level
 */
enum class DuplicateConfidence {
    VERY_LOW,   // < 50
    LOW,        // 50-69
    MEDIUM,     // 70-89
    HIGH        // 90+
}

/**
 * Match pair for display
 */
data class DuplicateMatch(
    val existingBeneficiary: BeneficiaryEntity,
    val newBeneficiary: BeneficiaryEntity,
    val score: DuplicateScore
)
