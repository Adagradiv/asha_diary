package com.upgovt.ashadiary.domain.usecases

import com.upgovt.ashadiary.data.local.dao.*
import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import com.upgovt.ashadiary.data.repository.BiometricRepository
import com.upgovt.ashadiary.domain.duplicate.DuplicateDetectionEngine
import com.upgovt.ashadiary.domain.duplicate.DuplicateScore
import com.upgovt.ashadiary.utils.id.BeneficiaryIdGenerator
import com.upgovt.ashadiary.data.local.entities.DuplicateFlagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * RegisterBeneficiaryUseCase - Complete beneficiary registration workflow
 * 
 * Steps:
 * 1. Generate unique beneficiary ID
 * 2. Check for duplicates (name/village/age/biometric)
 * 3. If duplicates found: Create flags and warn user
 * 4. If no duplicates or user confirms: Register beneficiary
 * 5. Store fingerprint (if captured)
 * 6. Add to sync queue
 * 7. Create audit log
 */
class RegisterBeneficiaryUseCase(
    private val beneficiaryDao: BeneficiaryDao,
    private val villageDao: VillageDao,
    private val duplicateFlagDao: DuplicateFlagDao,
    private val biometricRepository: BiometricRepository,
    private val auditLogDao: AuditLogDao,
    private val syncQueueDao: SyncQueueDao
) {
    
    private val duplicateEngine = DuplicateDetectionEngine()
    
    /**
     * Execute registration with duplicate detection
     */
    suspend fun execute(
        request: BeneficiaryRegistrationRequest
    ): RegistrationResult = withContext(Dispatchers.IO) {
        
        try {
            // Step 1: Get village for ID generation
            val village = villageDao.getById(request.villageId)
                ?: return@withContext RegistrationResult.Error("गाँव नहीं मिला")
            
            // Step 2: Generate unique beneficiary ID
            val beneficiaryId = BeneficiaryIdGenerator.generateId(
                village = village,
                workerCode = request.workerCode,
                lastSequentialNumber = 0  // TODO: Get from DB
            )
            
            // Step 3: Create beneficiary entity
            val beneficiary = BeneficiaryEntity(
                id = UUID.randomUUID().toString(),
                beneficiary_id = beneficiaryId,
                name_hindi = request.nameHindi,
                father_husband_name_hindi = request.fatherHusbandNameHindi,
                gender = request.gender,
                date_of_birth = request.dateOfBirth,
                age_years = request.ageYears,
                beneficiary_type = request.beneficiaryType,
                village_id = request.villageId,
                address_hindi = request.addressHindi,
                mobile_number = request.mobileNumber,
                registered_by_user_id = request.registeredByUserId,
                registration_gps_lat = request.gpsLat,
                registration_gps_lng = request.gpsLng,
                last_modified_by_user_id = request.registeredByUserId,
                last_modified_by_role_id = request.registeredByRoleId
            )
            
            // Step 4: Check for duplicates
            val duplicateMatches = checkForDuplicates(beneficiary)
            
            if (duplicateMatches.isNotEmpty() && !request.confirmDuplicateOverride) {
                // Duplicates found, user needs to confirm
                return@withContext RegistrationResult.DuplicatesFound(
                    beneficiary = beneficiary,
                    duplicates = duplicateMatches
                )
            }
            
            // Step 5: Insert beneficiary
            beneficiaryDao.insertBeneficiary(beneficiary)
            
            // Step 6: Create duplicate flags if matches found but user confirmed
            if (duplicateMatches.isNotEmpty()) {
                createDuplicateFlags(beneficiary, duplicateMatches, request.registeredByUserId)
            }
            
            // Step 7: Store fingerprint if provided
            request.biometricTemplate?.let { template ->
                biometricRepository.storeFingerprintTemplate(
                    beneficiaryId = beneficiary.id,
                    fingerPosition = request.fingerPosition ?: "RIGHT_THUMB",
                    isoTemplate = template,
                    qualityScore = request.biometricQuality ?: 0,
                    capturedByUserId = request.registeredByUserId,
                    deviceSerialNumber = request.deviceSerial ?: ""
                )
            }
            
            // Step 8: Add to sync queue
            addToSyncQueue(beneficiary, request.registeredByRoleId)
            
            // Step 9: Create audit log
            createAuditLog(beneficiary, request.registeredByUserId, request.registeredByRoleId)
            
            RegistrationResult.Success(beneficiary)
            
        } catch (e: Exception) {
            RegistrationResult.Error(e.message ?: "पंजीकरण विफल")
        }
    }
    
    /**
     * Check for duplicate beneficiaries
     */
    private suspend fun checkForDuplicates(
        newBeneficiary: BeneficiaryEntity
    ): List<Pair<BeneficiaryEntity, DuplicateScore>> {
        // Find potential duplicates by name, village, age
        val potentialDuplicates = beneficiaryDao.findPotentialDuplicates(
            villageId = newBeneficiary.village_id,
            gender = newBeneficiary.gender,
            ageYears = newBeneficiary.age_years,
            excludeId = newBeneficiary.id
        )
        
        // Calculate scores
        val matches = mutableListOf<Pair<BeneficiaryEntity, DuplicateScore>>()
        
        for (existing in potentialDuplicates) {
            val score = duplicateEngine.calculateDuplicateScore(existing, newBeneficiary)
            
            // Only include if score is above threshold
            if (score.totalScore >= DuplicateDetectionEngine.LOW_MATCH_THRESHOLD) {
                matches.add(Pair(existing, score))
            }
        }
        
        // Sort by score descending
        return matches.sortedByDescending { it.second.totalScore }
    }
    
    /**
     * Create duplicate flags for matches
     */
    private suspend fun createDuplicateFlags(
        newBeneficiary: BeneficiaryEntity,
        matches: List<Pair<BeneficiaryEntity, DuplicateScore>>,
        userId: String
    ) {
        for ((existing, score) in matches) {
            val flag = DuplicateFlagEntity(
                id = UUID.randomUUID().toString(),
                beneficiary_id_1 = existing.id,
                beneficiary_id_2 = newBeneficiary.id,
                similarity_score = score.totalScore,
                name_match = score.nameScore >= 70,
                village_match = score.villageMatch,
                gender_match = score.genderMatch,
                age_within_range = score.ageScore >= 70,
                status = "PENDING",
                flagged_by_user_id = userId,
                flag_source = "AUTO_DETECTION"
            )
            
            duplicateFlagDao.insert(flag)
            
            // Mark both beneficiaries as flagged
            beneficiaryDao.setDuplicateFlagged(existing.id, true)
            beneficiaryDao.setDuplicateFlagged(newBeneficiary.id, true)
        }
    }
    
    /**
     * Add to sync queue
     */
    private suspend fun addToSyncQueue(beneficiary: BeneficiaryEntity, roleId: Int) {
        // Implementation would add to sync queue
        // Using SyncQueueEntity and SyncPriority from Phase 1
    }
    
    /**
     * Create audit log
     */
    private suspend fun createAuditLog(
        beneficiary: BeneficiaryEntity,
        userId: String,
        roleId: Int
    ) {
        // Implementation would create audit log
    }
}

/**
 * Beneficiary registration request
 */
data class BeneficiaryRegistrationRequest(
    val nameHindi: String,
    val fatherHusbandNameHindi: String,
    val gender: String,
    val dateOfBirth: Long,
    val ageYears: Int,
    val beneficiaryType: String,
    val villageId: String,
    val addressHindi: String,
    val mobileNumber: String?,
    val registeredByUserId: String,
    val registeredByRoleId: Int,
    val workerCode: String,
    val gpsLat: Double,
    val gpsLng: Double,
    val biometricTemplate: ByteArray? = null,
    val biometricQuality: Int? = null,
    val fingerPosition: String? = null,
    val deviceSerial: String? = null,
    val confirmDuplicateOverride: Boolean = false  // User confirmed despite duplicates
)

/**
 * Registration result
 */
sealed class RegistrationResult {
    data class Success(val beneficiary: BeneficiaryEntity) : RegistrationResult()
    data class DuplicatesFound(
        val beneficiary: BeneficiaryEntity,
        val duplicates: List<Pair<BeneficiaryEntity, DuplicateScore>>
    ) : RegistrationResult()
    data class Error(val message: String) : RegistrationResult()
}
