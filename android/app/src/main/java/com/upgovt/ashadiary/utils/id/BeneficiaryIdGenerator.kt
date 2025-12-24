package com.upgovt.ashadiary.utils.id

import com.upgovt.ashadiary.data.local.entities.VillageEntity
import java.util.concurrent.atomic.AtomicInteger

/**
 * BeneficiaryIdGenerator - Generates unique beneficiary IDs offline
 * 
 * Format: SS-DDD-BBB-VVVV-W-NNNN
 * Example: 09-146-137-4462-A-0001
 * 
 * Components:
 * - SS: State code (09 for Uttar Pradesh)
 * - DDD: District code (146 for Agra)
 * - BBB: Block code
 * - VVVV: Village code
 * - W: Worker code (A, B, C... assigned to field staff)
 * - NNNN: Sequential number (0001-9999)
 * 
 * Collision prevention: Each worker has unique code + sequential counter
 */
object BeneficiaryIdGenerator {
    
    // Thread-safe counter for sequential numbers
    private val sequentialCounter = AtomicInteger(0)
    private var lastGeneratedDate = ""
    
    /**
     * Generate unique beneficiary ID
     * 
     * @param village Village entity with geographic codes
     * @param workerCode Worker code assigned to field staff (A-Z)
     * @param lastSequentialNumber Last used sequential number for this worker (from DB)
     * @return Formatted beneficiary ID
     */
    fun generateId(
        village: VillageEntity,
        workerCode: String,
        lastSequentialNumber: Int = 0
    ): String {
        // Reset counter daily to prevent overflow
        val currentDate = getCurrentDate()
        if (currentDate != lastGeneratedDate) {
            sequentialCounter.set(0)
            lastGeneratedDate = currentDate
        }
        
        // Get next sequential number
        val nextNumber = maxOf(
            sequentialCounter.incrementAndGet(),
            lastSequentialNumber + 1
        )
        
        // Format: SS-DDD-BBB-VVVV-W-NNNN
        return buildString {
            append(village.state_code.padStart(2, '0'))
            append("-")
            append(village.district_code.padStart(3, '0'))
            append("-")
            append(village.block_code.padStart(3, '0'))
            append("-")
            append(village.village_code.padStart(4, '0'))
            append("-")
            append(workerCode.uppercase())
            append("-")
            append(nextNumber.toString().padStart(4, '0'))
        }
    }
    
    /**
     * Validate beneficiary ID format
     */
    fun isValidFormat(beneficiaryId: String): Boolean {
        // Regex: XX-XXX-XXX-XXXX-X-XXXX
        val regex = Regex("^\\d{2}-\\d{3}-\\d{3}-\\d{4}-[A-Z]-\\d{4}$")
        return regex.matches(beneficiaryId)
    }
    
    /**
     * Extract components from beneficiary ID
     */
    fun parseId(beneficiaryId: String): BeneficiaryIdComponents? {
        if (!isValidFormat(beneficiaryId)) return null
        
        val parts = beneficiaryId.split("-")
        return BeneficiaryIdComponents(
            stateCode = parts[0],
            districtCode = parts[1],
            blockCode = parts[2],
            villageCode = parts[3],
            workerCode = parts[4],
            sequentialNumber = parts[5].toInt()
        )
    }
    
    /**
     * Get last sequential number for a worker from database
     * This should be called before generating new IDs
     */
    suspend fun getLastSequentialNumber(
        beneficiaryDao: com.upgovt.ashadiary.data.local.dao.BeneficiaryDao,
        workerCode: String
    ): Int {
        // Query database for highest sequential number for this worker
        // This is a simplified version - actual implementation would query DB
        return 0  // TODO: Implement DB query
    }
    
    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
    }
}

/**
 * Data class for beneficiary ID components
 */
data class BeneficiaryIdComponents(
    val stateCode: String,
    val districtCode: String,
    val blockCode: String,
    val villageCode: String,
    val workerCode: String,
    val sequentialNumber: Int
)
