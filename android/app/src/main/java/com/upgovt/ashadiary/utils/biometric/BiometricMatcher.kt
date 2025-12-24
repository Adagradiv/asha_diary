package com.upgovt.ashadiary.utils.biometric

import android.util.Log
import kotlin.math.min

/**
 * BiometricMatcher - Local ISO fingerprint template matching
 * 
 * CRITICAL: This is a SIMPLIFIED matcher for duplicate detection
 * For production, integrate a proper ISO 19794-2 matcher library like:
 * - SourceAFIS
 * - NIST NBIS
 * - Commercial SDK (MegaMatcher, Neurotechnology, etc.)
 * 
 * This implementation provides basic matching for offline duplicate detection
 */
class BiometricMatcher {
    
    companion object {
        private const val TAG = "BiometricMatcher"
        
        // Match threshold (0-100, higher = more strict)
        const val MATCH_THRESHOLD = 60
        const val HIGH_CONFIDENCE_THRESHOLD = 80
        
        // ISO template header size (varies by format)
        private const val ISO_HEADER_SIZE = 24
    }
    
    /**
     * Match two ISO fingerprint templates
     * 
     * @param template1 First ISO template
     * @param template2 Second ISO template
     * @return Match score (0-100), higher means better match
     */
    fun matchTemplates(template1: ByteArray, template2: ByteArray): Int {
        try {
            // Validate templates
            if (template1.isEmpty() || template2.isEmpty()) {
                Log.w(TAG, "Empty templates provided")
                return 0
            }
            
            if (template1.size < ISO_HEADER_SIZE || template2.size < ISO_HEADER_SIZE) {
                Log.w(TAG, "Templates too small to be valid ISO templates")
                return 0
            }
            
            // SIMPLIFIED MATCHING ALGORITHM
            // This is NOT production-grade - integrate proper matcher
            
            // 1. Basic similarity check using Hamming distance on template data
            val score = calculateSimpleSimilarity(template1, template2)
            
            Log.d(TAG, "Match score: $score (threshold: $MATCH_THRESHOLD)")
            
            return score
            
        } catch (e: Exception) {
            Log.e(TAG, "Error matching templates", e)
            return 0
        }
    }
    
    /**
     * Check if two templates match above threshold
     */
    fun isMatch(template1: ByteArray, template2: ByteArray, threshold: Int = MATCH_THRESHOLD): Boolean {
        val score = matchTemplates(template1, template2)
        return score >= threshold
    }
    
    /**
     * Find best match from a list of templates
     * Returns Pair(index, score) or null if no match above threshold
     */
    fun findBestMatch(
        queryTemplate: ByteArray,
        referenceTemplates: List<ByteArray>,
        threshold: Int = MATCH_THRESHOLD
    ): Pair<Int, Int>? {
        var bestMatchIndex = -1
        var bestMatchScore = 0
        
        referenceTemplates.forEachIndexed { index, template ->
            val score = matchTemplates(queryTemplate, template)
            if (score > bestMatchScore && score >= threshold) {
                bestMatchScore = score
                bestMatchIndex = index
            }
        }
        
        return if (bestMatchIndex >= 0) {
            Pair(bestMatchIndex, bestMatchScore)
        } else {
            null
        }
    }
    
    /**
     * SIMPLIFIED similarity calculation
     * Production: Replace with proper ISO 19794-2 minutiae matching
     * 
     * This uses a basic byte-by-byte comparison for demonstration
     */
    private fun calculateSimpleSimilarity(template1: ByteArray, template2: ByteArray): Int {
        // Skip header, compare data portion
        val data1 = template1.copyOfRange(ISO_HEADER_SIZE, template1.size)
        val data2 = template2.copyOfRange(ISO_HEADER_SIZE, template2.size)
        
        val minLen = min(data1.size, data2.size)
        
        if (minLen == 0) return 0
        
        // Calculate similarity as percentage of matching bytes
        var matchingBytes = 0
        for (i in 0 until minLen) {
            if (data1[i] == data2[i]) {
                matchingBytes++
            }
        }
        
        // Normalize to 0-100 scale
        val similarity = (matchingBytes.toFloat() / minLen.toFloat() * 100).toInt()
        
        // Apply size penalty if templates differ significantly in size
        val sizeDiff = kotlin.math.abs(data1.size - data2.size).toFloat() / minLen.toFloat()
        val sizePenalty = (sizeDiff * 20).toInt()  // Up to 20 point penalty
        
        return maxOf(0, similarity - sizePenalty)
    }
}

/**
 * Production-grade matcher interface
 * Implement this with a proper ISO 19794-2 matcher library
 */
interface ISOFingerprintMatcher {
    /**
     * Extract minutiae from ISO template
     */
    fun extractMinutiae(isoTemplate: ByteArray): MinutiaeData
    
    /**
     * Match two minutiae sets
     */
    fun matchMinutiae(minutiae1: MinutiaeData, minutiae2: MinutiaeData): MatchResult
}

/**
 * Minutiae data (ISO 19794-2)
 */
data class MinutiaeData(
    val minutiaePoints: List<MinutiaePoint>,
    val imageQuality: Int,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Minutiae point
 */
data class MinutiaePoint(
    val x: Int,
    val y: Int,
    val angle: Int,
    val type: MinutiaeType
)

/**
 * Minutiae types
 */
enum class MinutiaeType {
    ENDING,
    BIFURCATION,
    UNKNOWN
}

/**
 * Match result
 */
data class MatchResult(
    val score: Int,  // 0-100
    val isMatch: Boolean,
    val matchedMinutiaeCount: Int,
    val confidence: MatchConfidence
)

/**
 * Match confidence level
 */
enum class MatchConfidence {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
